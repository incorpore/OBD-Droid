package com.obddroid.features.copilot.agent.tools;

import android.content.Context;

import com.obddroid.scan.DiagnosticAnalyzer;
import com.obddroid.scan.ScanReport;
import com.obddroid.scan.ScanResultsManager;

import org.json.JSONObject;

/**
 * Tool handler for analyze_dtcs.
 * Runs AI diagnostic analysis on the latest scan results.
 */
public class AnalyzeDtcsTool implements AgentTool {

    @Override
    public String getName() {
        return "analyze_dtcs";
    }

    @Override
    public String execute(Context context, JSONObject parameters) throws Exception {
        String detailLevel = parameters.optString("detail_level", "detailed");

        ScanResultsManager resultsManager = ScanResultsManager.getInstance(context);
        ScanReport report = resultsManager.getLatestScan();

        if (report == null) {
            return "No scan results available to analyze. Please run a full vehicle scan first.";
        }

        // Check if AI analysis already exists
        if (report.hasAiAnalysis()) {
            return "AI Diagnostic Analysis (from previous scan):\n\n" +
                report.getAiAnalysis().getAnalysisText();
        }

        // Run new AI analysis
        DiagnosticAnalyzer analyzer = new DiagnosticAnalyzer(context);
        DiagnosticAnalyzer.DiagnosticAnalysis analysis = analyzer.analyzeScan(report).get();

        if (analysis.isSuccess()) {
            // Cache the analysis for future use
            report.attachAiAnalysis(analysis);
            return "AI Diagnostic Analysis:\n\n" + analysis.getAnalysisText();
        } else {
            throw new Exception("AI analysis failed. Please check your OpenAI API key in settings.");
        }
    }

    @Override
    public boolean requiresConfirmation() {
        return false;
    }
}
