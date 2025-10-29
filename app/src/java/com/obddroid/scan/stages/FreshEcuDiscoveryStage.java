package com.obddroid.scan.stages;

import com.obddroid.scan.ScanContext;
import com.obddroid.scan.ScanStage;
import com.obddroid.scan.StageResult;
import com.obddroid.services.EcuDiscoveryService;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Stage 1: Fresh ECU Discovery using isolated EcuDiscoveryService.
 *
 * This stage performs a FRESH discovery of all ECUs on the CAN bus by:
 * 1. Enabling headers (ATH1)
 * 2. Sending Mode 9 requests directly to the vehicle
 * 3. Parsing responses with ECU addresses
 * 4. Disabling headers (ATH0)
 *
 * This ensures the full vehicle scan has accurate, up-to-date ECU information
 * regardless of app state or cached data from the connection stage.
 */
public final class FreshEcuDiscoveryStage implements ScanStage {

    private static final Logger log = Logger.getLogger(FreshEcuDiscoveryStage.class.getName());
    private static final int DISCOVERY_TIMEOUT_SECONDS = 15;

    @Override
    public String getId() {
        return "FRESH_ECU_DISCOVERY";
    }

    @Override
    public String getDisplayName() {
        return "Discovering ECUs with Mode 9";
    }

    @Override
    public int getEstimatedDurationSeconds() {
        return 3;  // Typically takes 2-3 seconds
    }

    @Override
    public StageResult execute(ScanContext context) throws InterruptedException {
        context.checkCancelled();

        try {
            log.info("SCAN: Starting fresh ECU discovery with EcuDiscoveryService");

            // Create discovery service and run fresh discovery
            EcuDiscoveryService discoveryService = new EcuDiscoveryService();

            // Execute discovery with timeout (blocks until complete)
            Map<Integer, EcuDiscoveryService.EcuDiscoveryInfo> discoveryData =
                discoveryService.discoverEcus().get(DISCOVERY_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            log.info("SCAN: Fresh ECU discovery complete - found " + discoveryData.size() + " ECU(s)");

            // Build JSON result
            JSONObject data = new JSONObject();
            JSONArray ecuArray = new JSONArray();
            JSONArray addressArray = new JSONArray();

            for (Map.Entry<Integer, EcuDiscoveryService.EcuDiscoveryInfo> entry : discoveryData.entrySet()) {
                context.checkCancelled();

                int address = entry.getKey();
                EcuDiscoveryService.EcuDiscoveryInfo info = entry.getValue();

                // Add to address list
                addressArray.put(String.format("0x%X", address));

                // Build ECU snapshot
                JSONObject ecuSnapshot = new JSONObject();
                ecuSnapshot.put("address", String.format("0x%X", address));
                ecuSnapshot.put("addressDec", address);

                if (info.name != null && !info.name.isEmpty()) {
                    ecuSnapshot.put("name", info.name);
                }
                if (info.calibrationId != null && !info.calibrationId.isEmpty()) {
                    ecuSnapshot.put("calibrationId", info.calibrationId);
                }
                if (info.calibrationId2 != null && !info.calibrationId2.isEmpty()) {
                    ecuSnapshot.put("calibrationId2", info.calibrationId2);
                }
                if (info.cvn != null && !info.cvn.isEmpty()) {
                    ecuSnapshot.put("cvn", info.cvn);
                }

                ecuArray.put(ecuSnapshot);
            }

            // Build discovery summary (compatible with DiscoverySnapshotStage format)
            JSONObject discoverySummary = new JSONObject();
            discoverySummary.put("method", "fresh_mode9_discovery");
            discoverySummary.put("ecuCount", discoveryData.size());
            discoverySummary.put("addresses", addressArray);

            data.put("discovery", discoverySummary);
            data.put("ecuSnapshots", ecuArray);
            data.put("timestamp", System.currentTimeMillis());

            // Store ECU count for other stages
            context.putSharedData("ecuCount", discoveryData.size());

            String message = discoveryData.isEmpty() ?
                "No ECUs found (vehicle may not support Mode 9)" :
                String.format("Discovered %d ECU(s) with Mode 9", discoveryData.size());

            log.info("SCAN: " + message);

            return StageResult.success(message, data);

        } catch (java.util.concurrent.TimeoutException e) {
            log.warning("SCAN: ECU discovery timed out after " + DISCOVERY_TIMEOUT_SECONDS + " seconds");
            return StageResult.failed(
                "ECU discovery timed out - vehicle may not support Mode 9",
                e
            );
        } catch (InterruptedException e) {
            log.info("SCAN: ECU discovery interrupted (scan cancelled)");
            throw e;  // Re-throw to properly cancel scan
        } catch (Exception e) {
            log.severe("SCAN: ECU discovery failed: " + e.getMessage());
            return StageResult.failed(
                "ECU discovery failed: " + e.getMessage(),
                e
            );
        }
    }

    @Override
    public boolean shouldSkip(ScanContext context) {
        return false;  // Always perform fresh ECU discovery
    }
}
