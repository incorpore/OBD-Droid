package com.obddroid.features.copilot.agent.tools;

import android.content.Context;
import android.content.Intent;

import com.obddroid.scan.ScanConfiguration;
import com.obddroid.scan.ScanOrchestrator;

import org.json.JSONObject;

/**
 * Tool handler for run_full_scan.
 * Executes a comprehensive OBD diagnostic scan.
 */
public class RunFullScanTool implements AgentTool {

    @Override
    public String getName() {
        return "run_full_scan";
    }

    @Override
    public String execute(Context context, JSONObject parameters) throws Exception {
        boolean includeLiveData = parameters.optBoolean("include_live_data", true);
        boolean includeFreezeFrames = parameters.optBoolean("include_freeze_frames", true);

        // Build scan configuration
        ScanConfiguration config = new ScanConfiguration.Builder()
            .includeLiveData(includeLiveData)
            .includeFreezeFrames(includeFreezeFrames)
            .build();

        // Start scan orchestrator service
        Intent intent = new Intent(context, ScanOrchestrator.class);
        context.startService(intent);

        // NOTE: This is a synchronous limitation - we need to return immediately
        // In a real implementation, we'd need to:
        // 1. Start the scan
        // 2. Wait for completion (or return a scan_id for later retrieval)
        // 3. Return the results
        //
        // For now, return a status message indicating scan has started
        return "Full vehicle scan started. This will take 30-60 seconds to complete. " +
            "Stages include: discovery, vehicle info, live data" +
            (includeLiveData ? " (enabled)" : " (disabled)") +
            ", fault codes, pending/permanent DTCs, freeze frames" +
            (includeFreezeFrames ? " (enabled)" : " (disabled)") +
            ", monitors, and component tests. " +
            "You can ask me about the results once the scan completes.";
    }

    @Override
    public boolean requiresConfirmation() {
        return false; // Scans are safe to run without confirmation
    }
}
