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
 * Stage 2: Capture live data snapshot from Mode 01 PIDs
 */
public final class LiveDataStage implements ScanStage {

    @Override
    public String getId() {
        return "LIVE_DATA";
    }

    @Override
    public String getDisplayName() {
        return "Capturing Live Data Snapshot";
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
            JSONArray pidsArray = new JSONArray();

            int capturedCount = 0;

            // Snapshot current PidPvs (Mode 01 data)
            if (ObdProt.PidPvs != null && !ObdProt.PidPvs.isEmpty()) {
                // Get a copy of the entries to avoid concurrent modification
                @SuppressWarnings("unchecked")
                java.util.Set<Map.Entry<String, EcuDataPv>> entries =
                    new java.util.HashSet<>(ObdProt.PidPvs.entrySet());

                for (Map.Entry<String, EcuDataPv> entry : entries) {
                    context.checkCancelled();

                    try {
                        String key = entry.getKey();
                        EcuDataPv pv = entry.getValue();

                        if (pv == null) continue;

                        JSONObject pidObj = new JSONObject();
                        pidObj.put("key", key);
                        pidObj.put("description", pv.get(EcuDataPv.FID_DESCRIPT));
                        pidObj.put("value", pv.get(EcuDataPv.FID_VALUE));
                        pidObj.put("units", pv.get(EcuDataPv.FID_UNITS));
                        pidObj.put("pid", pv.get(EcuDataPv.FID_PID));

                        pidsArray.put(pidObj);
                        capturedCount++;
                    } catch (Exception e) {
                        // Skip this PID if there's an issue
                        continue;
                    }
                }
            }

            data.put("pids", pidsArray);
            data.put("count", capturedCount);
            data.put("timestamp", System.currentTimeMillis());

            String message = capturedCount > 0 ?
                String.format("Captured %d live data PID(s)", capturedCount) :
                "No live data available";

            return StageResult.success(message, data);

        } catch (Exception e) {
            return StageResult.failed("Failed to capture live data: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean shouldSkip(ScanContext context) {
        return !context.getConfiguration().isIncludeLiveData();
    }
}
