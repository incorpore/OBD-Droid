package com.obddroid.features.copilot.agent.tools;

import android.content.Context;

import com.obddroid.scan.DiagnosticAnalyzer;
import com.obddroid.scan.ScanReport;
import com.obddroid.scan.ScanResultsManager;

import org.json.JSONObject;

/**
 * Tool handler for analyze_dtcs.
 * Builds a comprehensive diagnostic prompt from the latest scan results.
 * The Agent API will analyze this data within the conversation context.
 */
public class AnalyzeDtcsTool implements AgentTool {

    @Override
    public String getName() {
        return "analyze_dtcs";
    }

    @Override
    public String execute(Context context, JSONObject parameters) throws Exception {
        ScanResultsManager resultsManager = ScanResultsManager.getInstance(context);
        ScanReport report = resultsManager.getLatestScan();

        if (report == null) {
            return "No scan results available to analyze. Please run a full vehicle scan first using the run_full_scan tool.";
        }

        // Build comprehensive diagnostic prompt for the agent to analyze
        String diagnosticPrompt = DiagnosticAnalyzer.buildDiagnosticPrompt(report);

        // Return the prompt - the Agent API will analyze it within the conversation
        return diagnosticPrompt;
    }

    @Override
    public boolean requiresConfirmation() {
        return false;
    }
}
