package com.obddroid.scan.stages;

import com.obddroid.scan.ScanContext;
import com.obddroid.scan.ScanStage;
import com.obddroid.scan.StageResult;
import com.obddroid.services.FaultCodeService;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Stage to scan for all fault codes (Modes 03, 07, 0A)
 */
public final class FaultCodeStage implements ScanStage {

    private final FaultCodeService faultCodeService;

    public FaultCodeStage() {
        this.faultCodeService = new FaultCodeService();
    }

    @Override
    public String getId() {
        return "FAULT_CODES";
    }

    @Override
    public String getDisplayName() {
        return "Reading Fault Codes";
    }

    @Override
    public int getEstimatedDurationSeconds() {
        return 10;
    }

    @Override
    public StageResult execute(ScanContext context) throws InterruptedException {
        context.checkCancelled();

        try {
            // Scan all code types (confirmed, pending, permanent)
            List<FaultCodeService.FaultCodeInfo> codes = faultCodeService.scanAllCodes()
                .get(context.getConfiguration().getTimeoutPerStageSeconds(), TimeUnit.SECONDS);

            context.checkCancelled();

            JSONObject data = new JSONObject();
            JSONArray codesArray = new JSONArray();

            int confirmedCount = 0;
            int pendingCount = 0;
            int permanentCount = 0;
            List<FaultCodeService.FaultCodeInfo> confirmedCodes = new ArrayList<>();
            List<FaultCodeService.FaultCodeInfo> pendingCodes = new ArrayList<>();
            List<FaultCodeService.FaultCodeInfo> permanentCodes = new ArrayList<>();

            for (FaultCodeService.FaultCodeInfo code : codes) {
                JSONObject codeObj = new JSONObject();
                codeObj.put("code", code.code);
                codeObj.put("description", code.description);
                codeObj.put("type", code.type.name());
                codeObj.put("isPending", code.isPending);
                codeObj.put("hasFreeze", code.hasFreeze);
                codeObj.put("dtcNumber", code.dtcNumber);
                codesArray.put(codeObj);

                switch (code.type) {
                    case CONFIRMED:
                        confirmedCount++;
                        confirmedCodes.add(code);
                        break;
                    case PENDING:
                        pendingCount++;
                        pendingCodes.add(code);
                        break;
                    case PERMANENT:
                        permanentCount++;
                        permanentCodes.add(code);
                        break;
                }
            }

            data.put("codes", codesArray);
            data.put("totalCount", codes.size());
            data.put("confirmedCount", confirmedCount);
            data.put("pendingCount", pendingCount);
            data.put("permanentCount", permanentCount);

            // Store for other stages to use
            context.putSharedData("dtcCount", codes.size());
            context.putSharedData("hasDtcs", !codes.isEmpty());
            context.putSharedData("faultCodesAll", new ArrayList<>(codes));
            context.putSharedData("faultCodesConfirmed", confirmedCodes);
            context.putSharedData("faultCodesPending", pendingCodes);
            context.putSharedData("faultCodesPermanent", permanentCodes);
            context.putSharedData("hasFreezeFrameCandidates",
                confirmedCodes.stream().anyMatch(info -> info.hasFreeze));

            String message;
            if (codes.isEmpty()) {
                message = "No fault codes found";
            } else {
                message = String.format("Found %d fault code(s): %d confirmed, %d pending, %d permanent",
                    codes.size(), confirmedCount, pendingCount, permanentCount);
            }

            return StageResult.success(message, data);

        } catch (TimeoutException e) {
            return StageResult.failed("Fault code scan timed out", e);
        } catch (ExecutionException e) {
            return StageResult.failed("Failed to read fault codes: " + e.getMessage(), e);
        } catch (Exception e) {
            return StageResult.failed("Unexpected error reading fault codes: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean shouldSkip(ScanContext context) {
        return false; // Always try to read fault codes
    }
}
