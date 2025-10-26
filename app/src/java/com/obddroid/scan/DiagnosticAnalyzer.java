package com.obddroid.scan;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Diagnostic analyzer that builds structured prompts for AI analysis of scan results.
 * Works with Agent API - prompts are sent to the conversational agent for analysis.
 */
public class DiagnosticAnalyzer {

    /**
     * Build a comprehensive diagnostic analysis prompt for a scan report.
     * This prompt can be sent to the Agent API for analysis within a conversation.
     */
    public static String buildDiagnosticPrompt(ScanReport report) {
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

        return userPrompt.toString();
    }

    private static String formatStageData(ScanOrchestrator.StageExecutionRecord record) {
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

}
