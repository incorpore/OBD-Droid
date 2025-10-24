package com.obddroid.scan.stages;

import com.obddroid.core.ecu.EcuDataPv;
import com.obddroid.core.obd.ObdProt;
import com.obddroid.scan.ScanContext;
import com.obddroid.scan.ScanStage;
import com.obddroid.scan.StageResult;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Map;

/**
 * Stage: Capture vehicle information from Mode 09
 */
public final class VehicleInfoStage implements ScanStage {

    @Override
    public String getId() {
        return "VEHICLE_INFO";
    }

    @Override
    public String getDisplayName() {
        return "Reading Vehicle Information";
    }

    @Override
    public int getEstimatedDurationSeconds() {
        return 5;
    }

    @Override
    public StageResult execute(ScanContext context) throws InterruptedException {
        context.checkCancelled();

        try {
            JSONObject data = new JSONObject();
            JSONArray vidsArray = new JSONArray();

            int capturedCount = 0;

            // Snapshot current VidPvs (Mode 09 data)
            if (ObdProt.VidPvs != null && !ObdProt.VidPvs.isEmpty()) {
                // Get a copy of the entries to avoid concurrent modification
                @SuppressWarnings("unchecked")
                java.util.Set<Map.Entry<Integer, EcuDataPv>> entries =
                    new java.util.HashSet<>(ObdProt.VidPvs.entrySet());

                for (Map.Entry<Integer, EcuDataPv> entry : entries) {
                    context.checkCancelled();

                    try {
                        Integer key = entry.getKey();
                        EcuDataPv pv = entry.getValue();

                        if (pv == null) continue;

                        JSONObject vidObj = new JSONObject();
                        vidObj.put("vid", key);
                        vidObj.put("description", pv.get(EcuDataPv.FID_DESCRIPT));
                        vidObj.put("value", pv.get(EcuDataPv.FID_VALUE));
                        vidObj.put("units", pv.get(EcuDataPv.FID_UNITS));

                        vidsArray.put(vidObj);
                        capturedCount++;
                    } catch (Exception e) {
                        // Skip this VID if there's an issue
                        continue;
                    }
                }
            }

            // Also capture VIN from VehicleManager if available
            String vin = context.getVehicleManager().getCurrentVIN();
            if (vin != null && !vin.isEmpty()) {
                data.put("vin", vin);
            }

            data.put("vids", vidsArray);
            data.put("count", capturedCount);
            data.put("timestamp", System.currentTimeMillis());

            String message = capturedCount > 0 ?
                String.format("Captured %d vehicle info item(s)", capturedCount) :
                "No vehicle info available";

            if (vin != null && !vin.isEmpty()) {
                message += " (VIN: " + vin + ")";
            }

            return StageResult.success(message, data);

        } catch (Exception e) {
            return StageResult.failed("Failed to capture vehicle info: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean shouldSkip(ScanContext context) {
        return false; // Always try to capture vehicle info
    }
}
