package com.obddroid.features.copilot.agent.tools;

import android.content.Context;

import com.obddroid.scan.ScanReport;
import com.obddroid.scan.ScanResultsManager;

import org.json.JSONObject;

/**
 * Tool handler for get_scan_results.
 * Retrieves the latest scan results or a specific scan by ID.
 */
public class GetScanResultsTool implements AgentTool {

    @Override
    public String getName() {
        return "get_scan_results";
    }

    @Override
    public String execute(Context context, JSONObject parameters) throws Exception {
        String scanId = parameters.optString("scan_id", null);

        ScanResultsManager resultsManager = ScanResultsManager.getInstance(context);
        ScanReport report;

        if (scanId != null && !scanId.isEmpty()) {
            report = resultsManager.getScanById(scanId);
            if (report == null) {
                return "Scan not found: " + scanId + ". Available scans: " +
                    resultsManager.getRecentScans(5);
            }
        } else {
            report = resultsManager.getLatestScan();
            if (report == null) {
                return "No scans available yet. You can run a full vehicle scan to gather diagnostic data.";
            }
        }

        // Build comprehensive summary
        StringBuilder summary = new StringBuilder();
        summary.append("Scan Results for ").append(report.getScanId()).append(":\n\n");

        // Add metadata
        JSONObject metadata = report.getMetadata();
        if (metadata.has("vin")) {
            summary.append("VIN: ").append(metadata.optString("vin")).append("\n");
        }
        if (metadata.has("make") && metadata.has("model")) {
            summary.append("Vehicle: ")
                .append(metadata.optInt("year", 0)).append(" ")
                .append(metadata.optString("make")).append(" ")
                .append(metadata.optString("model")).append("\n");
        }

        summary.append("Duration: ").append(report.getTotalDurationMs() / 1000.0).append(" seconds\n");
        summary.append("Status: ").append(report.isSuccess() ? "SUCCESS" : "PARTIAL/ERRORS").append("\n\n");

        // Add summary note (detailed stage results in full report)
        summary.append("Stages Completed: ").append(report.getStageResults().size()).append("\n");
        summary.append("See full scan report for detailed stage results.\n");

        // Add AI analysis if available
        if (report.hasAiAnalysis()) {
            summary.append("\nAI Diagnostic Analysis Available: Yes\n");
            summary.append("Analysis: ").append(report.getAiAnalysis().getAnalysisText().substring(0,
                Math.min(500, report.getAiAnalysis().getAnalysisText().length())))
                .append("...\n");
        }

        return summary.toString();
    }

    @Override
    public boolean requiresConfirmation() {
        return false;
    }
}
