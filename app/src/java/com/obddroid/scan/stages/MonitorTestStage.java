package com.obddroid.scan.stages;

import com.obddroid.core.obd.ObdProt;
import com.obddroid.scan.ScanContext;
import com.obddroid.scan.ScanStage;
import com.obddroid.scan.StageResult;
import com.obddroid.services.CommService;

/**
 * Stage placeholder for Modes 05/06 monitor test results.
 * Currently reports as skipped because adapter support has not yet been implemented.
 */
public final class MonitorTestStage implements ScanStage {

    @Override
    public String getId() {
        return "MONITOR_TESTS";
    }

    @Override
    public String getDisplayName() {
        return "Reading Monitor Test Results";
    }

    @Override
    public int getEstimatedDurationSeconds() {
        return 5;
    }

    @Override
    public StageResult execute(ScanContext context) throws InterruptedException {
        if (!context.getConfiguration().isIncludeMonitorTests()) {
            return StageResult.skipped("Monitor test capture disabled in configuration");
        }

        if (CommService.elm == null) {
            return StageResult.failed("ELM327 connection unavailable for monitor tests",
                new IllegalStateException("CommService.elm == null"));
        }

        // Until Mode 05/06 support lands in ObdProt, surface an informative skip result.
        String serviceName = ObdProt.getServiceName(ObdProt.OBD_SVC_MON_RESULT);
        return StageResult.skipped(serviceName + " not yet implemented – skipping monitor capture");
    }

    @Override
    public boolean shouldSkip(ScanContext context) {
        return false;
    }
}
