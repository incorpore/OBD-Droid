package com.obddroid.scan.stages;

import com.obddroid.core.obd.ObdProt;
import com.obddroid.scan.ScanContext;
import com.obddroid.scan.ScanStage;
import com.obddroid.scan.StageResult;
import com.obddroid.services.CommService;

/**
 * Stage placeholder for Mode 08 component tests.
 */
public final class ComponentTestStage implements ScanStage {

    @Override
    public String getId() {
        return "COMPONENT_TESTS";
    }

    @Override
    public String getDisplayName() {
        return "Running Component Tests";
    }

    @Override
    public int getEstimatedDurationSeconds() {
        return 5;
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

        // Placeholder until Mode 08 support is implemented.
        String serviceName = ObdProt.getServiceName(ObdProt.OBD_SVC_CTRL_MODE);
        return StageResult.skipped(serviceName + " not yet implemented – skipping component tests");
    }

    @Override
    public boolean shouldSkip(ScanContext context) {
        return false;
    }
}
