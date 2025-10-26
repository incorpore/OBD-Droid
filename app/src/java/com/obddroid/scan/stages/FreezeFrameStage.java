package com.obddroid.scan.stages;

import com.obddroid.core.ecu.EcuDataPv;
import com.obddroid.core.obd.FreezeFrameManager;
import com.obddroid.core.pvs.ProcessVariables.ProcessVar;
import com.obddroid.scan.ScanContext;
import com.obddroid.scan.ScanStage;
import com.obddroid.scan.StageResult;
import com.obddroid.services.CommService;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Stage: Capture freeze frame snapshots (Mode 02) for associated DTCs.
 */
public final class FreezeFrameStage implements ScanStage {

    private static final int MAX_FRAMES_TO_REQUEST = 4;
    private static final long POLL_INTERVAL_MS = 250L;
    private static final long FRAME_TIMEOUT_MS = 2000L;

    @Override
    public String getId() {
        return "FREEZE_FRAME";
    }

    @Override
    public String getDisplayName() {
        return "Capturing Freeze Frame Data";
    }

    @Override
    public int getEstimatedDurationSeconds() {
        return 6;
    }

    @Override
    public StageResult execute(ScanContext context) throws InterruptedException {
        if (!context.getConfiguration().isIncludeFreezeFrames()) {
            return StageResult.skipped("Freeze frame capture disabled in configuration");
        }

        Boolean hasDtcs = context.getSharedData("hasDtcs", Boolean.class);
        if (hasDtcs != null && !hasDtcs) {
            return StageResult.skipped("No DTCs detected – skipping Mode 02 freeze frame capture");
        }

        if (CommService.elm == null) {
            return StageResult.failed("ELM327 connection unavailable for freeze frame capture",
                new IllegalStateException("CommService.elm == null"));
        }

        FreezeFrameManager manager = CommService.elm.getFreezeFrameManager();
        if (manager == null) {
            return StageResult.failed("Freeze frame manager unavailable",
                new IllegalStateException("FreezeFrameManager not initialized"));
        }

        // Request a bounded number of frames (frame 0 always queried)
        int dtcCount = context.getSharedData("dtcCount", Integer.class) != null
            ? context.getSharedData("dtcCount", Integer.class)
            : 0;
        int framesToQuery = Math.max(1, Math.min(MAX_FRAMES_TO_REQUEST, Math.max(dtcCount, 1)));

        for (int frameId = 0; frameId < framesToQuery; frameId++) {
            context.checkCancelled();
            requestFrame(frameId, manager, context);
        }

        JSONObject data = new JSONObject();
        JSONArray framesArray = buildFramesArray(manager);

        data.put("frameCount", framesArray.length());
        data.put("frames", framesArray);

        String message;
        if (framesArray.length() == 0) {
            message = "No freeze frame snapshots returned";
        } else {
            message = String.format(Locale.US, "Captured %d freeze frame snapshot(s)", framesArray.length());
        }

        context.putSharedData("freezeFrameCount", framesArray.length());
        return StageResult.success(message, data);
    }

    private void requestFrame(int frameId,
                              FreezeFrameManager manager,
                              ScanContext context) throws InterruptedException {
        // Issue the snapshot request
        CommService.elm.requestFreezeFrameSnapshot(frameId);

        long deadline = System.currentTimeMillis() + FRAME_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            context.checkCancelled();
            FreezeFrameManager.FreezeFrameData frame = manager.getFreezeFrame(frameId);
            if (frame != null && frame.data != null && !frame.data.isEmpty()) {
                return;
            }
            Thread.sleep(POLL_INTERVAL_MS);
        }
    }

    private JSONArray buildFramesArray(FreezeFrameManager manager) {
        JSONArray framesArray = new JSONArray();
        Map<Integer, FreezeFrameManager.FreezeFrameData> frames = manager.getAllFreezeFrames();

        if (frames.isEmpty()) {
            return framesArray;
        }

        List<FreezeFrameManager.FreezeFrameData> sorted = new ArrayList<>(frames.values());
        Collections.sort(sorted, Comparator.comparingInt(f -> f.frameId));

        for (FreezeFrameManager.FreezeFrameData frame : sorted) {
            JSONObject frameObj = new JSONObject();
            frameObj.put("frameId", frame.frameId);
            if (frame.dtcCode != null) {
                frameObj.put("dtcCode", frame.dtcCode);
            }
            frameObj.put("timestamp", frame.timestamp);

            JSONArray pidArray = new JSONArray();
            if (frame.data != null) {
                for (Object entryObj : frame.data.entrySet()) {
                    Map.Entry<?, ?> entry = (Map.Entry<?, ?>) entryObj;
                    Object value = entry.getValue();
                    if (value instanceof EcuDataPv) {
                        pidArray.put(convertEcuDataPv((EcuDataPv) value));
                    } else if (value instanceof ProcessVar) {
                        pidArray.put(convertProcessVar((ProcessVar) value));
                    }
                }
            }
            frameObj.put("pids", pidArray);
            frameObj.put("pidCount", pidArray.length());

            framesArray.put(frameObj);
        }

        return framesArray;
    }

    private JSONObject convertEcuDataPv(EcuDataPv pv) {
        JSONObject obj = new JSONObject();
        obj.put("pid", pv.get(EcuDataPv.FID_PID));
        obj.put("description", pv.get(EcuDataPv.FID_DESCRIPT));
        obj.put("value", pv.get(EcuDataPv.FID_VALUE));
        obj.put("units", pv.get(EcuDataPv.FID_UNITS));
        return obj;
    }

    private JSONObject convertProcessVar(ProcessVar pv) {
        JSONObject obj = new JSONObject();
        obj.put("key", pv.getKeyValue());
        JSONObject values = new JSONObject();
        for (Object keyObj : pv.keySet()) {
            Object value = pv.get(keyObj);
            if (value instanceof EcuDataPv) {
                values.put(String.valueOf(keyObj), convertEcuDataPv((EcuDataPv) value));
            } else {
                values.put(String.valueOf(keyObj), value);
            }
        }
        obj.put("values", values);
        return obj;
    }

    @Override
    public boolean shouldSkip(ScanContext context) {
        return false;
    }
}
