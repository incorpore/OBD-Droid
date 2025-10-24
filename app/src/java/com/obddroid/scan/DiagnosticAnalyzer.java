package com.obddroid.scan;

import android.content.Context;
import android.util.Log;

import com.obddroid.utils.OpenAiService;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * AI-powered diagnostic analyzer that interprets full vehicle scan results
 * and provides structured recommendations.
 */
public class DiagnosticAnalyzer {

    private static final String TAG = "DiagnosticAnalyzer";

    private final OpenAiService openAiService;

    public DiagnosticAnalyzer(Context context) {
        this.openAiService = new OpenAiService(context);
    }

    /**
     * Analyze a scan report and generate AI-powered diagnosis.
     * Returns a CompletableFuture that completes with the analysis result.
     */
    public CompletableFuture<DiagnosticAnalysis> analyzeScan(ScanReport report) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Build comprehensive diagnostic prompt
                List<OpenAiService.ChatMessage> messages = buildDiagnosticPrompt(report);

                // Call OpenAI with higher token limit for detailed analysis
                String response = openAiService.completeChat(messages, 1500, 0.7);

                // Parse response into structured analysis
                return parseDiagnosticResponse(response, report);

            } catch (Exception e) {
                Log.e(TAG, "Failed to analyze scan", e);
                throw new RuntimeException("AI analysis failed: " + e.getMessage(), e);
            }
        });
    }

    private List<OpenAiService.ChatMessage> buildDiagnosticPrompt(ScanReport report) {
        List<OpenAiService.ChatMessage> messages = new ArrayList<>();

        // System message with role definition
        String systemPrompt = "You are an expert automotive diagnostic AI analyzing OBD scan data. " +
            "Provide clear, actionable diagnostic insights focusing on root causes and repair priorities. " +
            "Your response should help technicians quickly identify issues and plan repairs.";

        messages.add(new OpenAiService.ChatMessage("system", systemPrompt));

        // User message with scan data
        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("Analyze this comprehensive vehicle diagnostic scan:\n\n");

        // Add vehicle metadata
        try {
            JSONObject metadata = report.getMetadata();
            if (metadata.has("vin")) {
                userPrompt.append("VIN: ").append(metadata.getString("vin")).append("\n");
            }
            if (metadata.has("make") && metadata.has("model") && metadata.has("year")) {
                userPrompt.append("Vehicle: ")
                    .append(metadata.getInt("year")).append(" ")
                    .append(metadata.getString("make")).append(" ")
                    .append(metadata.getString("model")).append("\n");
            }
            userPrompt.append("\n");
        } catch (JSONException e) {
            // Continue without metadata
        }

        // Add scan summary
        userPrompt.append("SCAN SUMMARY:\n");
        userPrompt.append("Duration: ").append(report.getTotalDurationMs() / 1000.0).append(" seconds\n");
        userPrompt.append("Stages Completed: ").append(report.getStageResults().size()).append("\n");
        userPrompt.append("Overall Status: ").append(report.isSuccess() ? "SUCCESS" : "PARTIAL/ERRORS").append("\n\n");

        // Add detailed findings from each stage
        userPrompt.append("DETAILED FINDINGS:\n\n");

        for (ScanOrchestrator.StageExecutionRecord record : report.getStageResults()) {
            userPrompt.append("Stage: ").append(record.stage.getDisplayName()).append("\n");
            userPrompt.append("Status: ").append(record.result.getStatus()).append("\n");

            if (record.result.getMessage() != null) {
                userPrompt.append("Message: ").append(record.result.getMessage()).append("\n");
            }

            if (record.result.getData() != null) {
                userPrompt.append("Data: ").append(formatStageData(record)).append("\n");
            }

            userPrompt.append("\n");
        }

        userPrompt.append("\nPlease provide:\n");
        userPrompt.append("1. PRIMARY DIAGNOSIS: What is the main issue (if any)?\n");
        userPrompt.append("2. ROOT CAUSES: Likely underlying causes of any problems\n");
        userPrompt.append("3. REPAIR RECOMMENDATIONS: Prioritized list of actions (HIGH/MEDIUM/LOW priority)\n");
        userPrompt.append("4. VERIFICATION STEPS: How to confirm repairs were successful\n");
        userPrompt.append("5. ADDITIONAL NOTES: Any warnings, related issues, or follow-up scans needed\n");

        messages.add(new OpenAiService.ChatMessage("user", userPrompt.toString()));

        return messages;
    }

    private String formatStageData(ScanOrchestrator.StageExecutionRecord record) {
        try {
            JSONObject data = record.result.getData();

            // Special formatting for fault codes
            if ("FAULT_CODES".equals(record.stage.getId())) {
                int total = data.optInt("totalCount", 0);
                int confirmed = data.optInt("confirmedCount", 0);
                int pending = data.optInt("pendingCount", 0);
                int permanent = data.optInt("permanentCount", 0);

                StringBuilder sb = new StringBuilder();
                sb.append(total).append(" total DTC(s) - ");
                sb.append(confirmed).append(" confirmed, ");
                sb.append(pending).append(" pending, ");
                sb.append(permanent).append(" permanent");

                // Add actual codes if available
                if (data.has("codes")) {
                    JSONArray codes = data.getJSONArray("codes");
                    if (codes.length() > 0) {
                        sb.append("\nCodes: ");
                        for (int i = 0; i < Math.min(codes.length(), 10); i++) {
                            JSONObject code = codes.getJSONObject(i);
                            sb.append(code.getString("code"));
                            if (code.has("description")) {
                                sb.append(" (").append(code.getString("description")).append(")");
                            }
                            if (i < codes.length() - 1) sb.append(", ");
                        }
                        if (codes.length() > 10) {
                            sb.append(" ... and ").append(codes.length() - 10).append(" more");
                        }
                    }
                }

                return sb.toString();
            }

            // Special formatting for live data
            if ("LIVE_DATA".equals(record.stage.getId())) {
                int count = data.optInt("count", 0);
                return count + " PID(s) captured";
            }

            // Special formatting for vehicle info
            if ("VEHICLE_INFO".equals(record.stage.getId())) {
                int count = data.optInt("count", 0);
                String vin = data.optString("vin", "");
                if (!vin.isEmpty()) {
                    return count + " data items, VIN: " + vin;
                }
                return count + " data items";
            }

            // Default: compact JSON
            return data.toString();

        } catch (JSONException e) {
            return "[error formatting data]";
        }
    }

    private DiagnosticAnalysis parseDiagnosticResponse(String response, ScanReport report) {
        // For now, return the raw text response
        // In the future, we could parse structured JSON responses
        return new DiagnosticAnalysis(
            report.getScanId(),
            response,
            System.currentTimeMillis(),
            true
        );
    }

    /**
     * Result of AI diagnostic analysis
     */
    public static class DiagnosticAnalysis {
        private final String scanId;
        private final String analysisText;
        private final long timestamp;
        private final boolean success;

        public DiagnosticAnalysis(String scanId, String analysisText, long timestamp, boolean success) {
            this.scanId = scanId;
            this.analysisText = analysisText;
            this.timestamp = timestamp;
            this.success = success;
        }

        public String getScanId() {
            return scanId;
        }

        public String getAnalysisText() {
            return analysisText;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public boolean isSuccess() {
            return success;
        }

        public JSONObject toJson() {
            try {
                JSONObject obj = new JSONObject();
                obj.put("scanId", scanId);
                obj.put("analysisText", analysisText);
                obj.put("timestamp", timestamp);
                obj.put("success", success);
                return obj;
            } catch (JSONException e) {
                return new JSONObject();
            }
        }
    }
}
