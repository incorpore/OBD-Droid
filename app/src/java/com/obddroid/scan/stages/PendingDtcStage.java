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
 * Stage: Surface pending (Mode 07) diagnostic trouble codes.
 * Relies on previously captured fault code snapshot to avoid re-querying the adapter.
 */
public final class PendingDtcStage implements ScanStage {

    private static final String SHARED_KEY = "faultCodesPending";

    @Override
    public String getId() {
        return "PENDING_DTC";
    }

    @Override
    public String getDisplayName() {
        return "Reading Pending DTCs";
    }

    @Override
    public int getEstimatedDurationSeconds() {
        return 4;
    }

    @Override
    @SuppressWarnings("unchecked")
    public StageResult execute(ScanContext context) throws InterruptedException {
        context.checkCancelled();

        List<FaultCodeService.FaultCodeInfo> pendingCodes =
            context.getSharedData(SHARED_KEY, List.class);

        if (pendingCodes == null) {
            pendingCodes = new ArrayList<>();
        }

        try {
            JSONArray codesArray = new JSONArray();
            for (FaultCodeService.FaultCodeInfo code : pendingCodes) {
                JSONObject codeObj = new JSONObject();
                codeObj.put("code", code.code);
                codeObj.put("description", code.description);
                codeObj.put("dtcNumber", code.dtcNumber);
                codeObj.put("hasFreeze", code.hasFreeze);
                codesArray.put(codeObj);
            }

            JSONObject data = new JSONObject();
            data.put("pendingCount", pendingCodes.size());
            data.put("codes", codesArray);

            String message;
            if (pendingCodes.isEmpty()) {
                message = "No pending (Mode 07) codes detected";
            } else {
                message = String.format(Locale.US, "Captured %d pending DTC(s)", pendingCodes.size());
            }

            return StageResult.success(message, data);
        } catch (JSONException e) {
            return StageResult.failed("Failed to serialize pending DTCs: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean shouldSkip(ScanContext context) {
        Boolean hasDtcs = context.getSharedData("hasDtcs", Boolean.class);
        return hasDtcs != null && !hasDtcs;
    }
}
