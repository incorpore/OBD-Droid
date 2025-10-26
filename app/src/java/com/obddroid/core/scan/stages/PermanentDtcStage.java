package com.obddroid.scan.stages;

import com.obddroid.scan.ScanContext;
import com.obddroid.scan.ScanStage;
import com.obddroid.scan.StageResult;
import com.obddroid.services.FaultCodeService;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Stage: Surface permanent (Mode 0A) diagnostic trouble codes.
 */
public final class PermanentDtcStage implements ScanStage {

    private static final String SHARED_KEY = "faultCodesPermanent";

    @Override
    public String getId() {
        return "PERMANENT_DTC";
    }

    @Override
    public String getDisplayName() {
        return "Reading Permanent DTCs";
    }

    @Override
    public int getEstimatedDurationSeconds() {
        return 4;
    }

    @Override
    @SuppressWarnings("unchecked")
    public StageResult execute(ScanContext context) throws InterruptedException {
        context.checkCancelled();

        List<FaultCodeService.FaultCodeInfo> permanentCodes =
            context.getSharedData(SHARED_KEY, List.class);

        if (permanentCodes == null) {
            permanentCodes = new ArrayList<>();
        }

        try {
            JSONArray codesArray = new JSONArray();
            for (FaultCodeService.FaultCodeInfo code : permanentCodes) {
                JSONObject codeObj = new JSONObject();
                codeObj.put("code", code.code);
                codeObj.put("description", code.description);
                codeObj.put("dtcNumber", code.dtcNumber);
                codesArray.put(codeObj);
            }

            JSONObject data = new JSONObject();
            data.put("permanentCount", permanentCodes.size());
            data.put("codes", codesArray);

            String message;
            if (permanentCodes.isEmpty()) {
                message = "No permanent (Mode 0A) codes detected";
            } else {
                message = String.format(Locale.US, "Captured %d permanent DTC(s)", permanentCodes.size());
            }

            return StageResult.success(message, data);
        } catch (JSONException e) {
            return StageResult.failed("Failed to serialize permanent DTCs: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean shouldSkip(ScanContext context) {
        Boolean hasDtcs = context.getSharedData("hasDtcs", Boolean.class);
        return hasDtcs != null && !hasDtcs;
    }
}
