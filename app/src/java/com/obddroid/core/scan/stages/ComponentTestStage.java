package com.obddroid.scan.stages;

import com.obddroid.core.ecu.EcuDataPv;
import com.obddroid.core.obd.ObdProt;
import com.obddroid.scan.ScanContext;
import com.obddroid.scan.ScanStage;
import com.obddroid.scan.StageResult;
import com.obddroid.services.CommService;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Stage: Capture Mode 08 component test data (Test IDs / control tests).
 */
public final class ComponentTestStage implements ScanStage {

    private static final long QUERY_TIMEOUT_MS = 2000L;
    private static final long POLL_INTERVAL_MS = 200L;

    @Override
    public String getId() {
        return "COMPONENT_TESTS";
    }

    @Override
    public String getDisplayName() {
        return "Capturing Component Test Data";
    }

    @Override
    public int getEstimatedDurationSeconds() {
        return 6;
    }

    @Override
    public StageResult execute(ScanContext context) throws InterruptedException {
        if (!context.getConfiguration().isIncludeComponentTests()) {
            return StageResult.skipped("Component tests disabled in configuration");
        }

        if (CommService.elm == null) {
            return StageResult.failed("ELM327 connection unavailable for component tests",
                new IllegalStateException("CommService.elm == null"));
        }

        int previousService = CommService.elm.getService();
        boolean serviceSwitched = false;

        try {
            if (previousService != ObdProt.OBD_SVC_CTRL_MODE) {
                CommService.elm.setService(ObdProt.OBD_SVC_CTRL_MODE, true);
                serviceSwitched = true;
            }

            waitForTidPopulation(context);

            JSONObject data = buildComponentTestPayload();

            int supportedCount = data.optInt("supportedCount", 0);
            String message;
            if (supportedCount == 0) {
                message = "No Mode 08 component tests reported by vehicle";
            } else {
                message = String.format(Locale.US,
                    "%d component test(s) reported via Mode 08",
                    supportedCount);
            }

            return StageResult.success(message, data);

        } catch (InterruptedException e) {
            throw e;
        } catch (Exception e) {
            return StageResult.failed("Failed to capture component tests: " + e.getMessage(), e);
        } finally {
            if (serviceSwitched) {
                CommService.elm.setService(previousService, false);
            }
        }
    }

    @Override
    public boolean shouldSkip(ScanContext context) {
        return false;
    }

    private void waitForTidPopulation(ScanContext context) throws InterruptedException {
        long deadline = System.currentTimeMillis() + QUERY_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            context.checkCancelled();
            if (!ObdProt.TidPvs.isEmpty()) {
                return;
            }
            Thread.sleep(POLL_INTERVAL_MS);
        }
    }

    private JSONObject buildComponentTestPayload() {
        try {
            JSONObject root = new JSONObject();
            JSONArray testsArray = new JSONArray();

            @SuppressWarnings("unchecked")
            List<Map.Entry<Object, Object>> entries = new ArrayList<>(ObdProt.TidPvs.entrySet());

            int supported = 0;
            for (Map.Entry<Object, Object> entry : entries) {
                Object value = entry.getValue();
                if (!(value instanceof EcuDataPv)) {
                    continue;
                }
                EcuDataPv pv = (EcuDataPv) value;

                JSONObject testObj = new JSONObject();
                Object pid = pv.get(EcuDataPv.FID_PID);
                if (pid instanceof Number) {
                    testObj.put("tid", String.format(Locale.US, "0x%02X", ((Number) pid).intValue()));
                } else if (pid != null) {
                    testObj.put("tid", pid.toString());
                }
                Object description = pv.get(EcuDataPv.FID_DESCRIPT);
                if (description != null) {
                    testObj.put("description", description);
                }
                Object units = pv.get(EcuDataPv.FID_UNITS);
                if (units != null) {
                    testObj.put("units", units);
                }
                Object valueObj = pv.get(EcuDataPv.FID_VALUE);
                if (valueObj != null) {
                    testObj.put("value", valueObj);
                }

                testsArray.put(testObj);
                supported++;
            }

            root.put("supportedCount", supported);
            root.put("tests", testsArray);
            root.put("service", ObdProt.getServiceName(ObdProt.OBD_SVC_CTRL_MODE));

            return root;
        } catch (JSONException e) {
            throw new IllegalStateException("Failed to build component test payload", e);
        }
    }
}
