package com.obddroid.scan;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Complete scan report containing metadata, stage results, and file paths.
 */
public final class ScanReport {

    private final String scanId;
    private final JSONObject metadata;
    private final List<ScanOrchestrator.StageExecutionRecord> stageResults;
    private final File outputDirectory;
    private final long totalDurationMs;
    private final boolean success;

    public ScanReport(String scanId,
                     ScanContext context,
                     List<ScanOrchestrator.StageExecutionRecord> stageResults) {
        this.scanId = scanId;
        this.stageResults = stageResults;
        this.outputDirectory = context.getOutputDirectory();
        this.metadata = context.getVehicleMetadata();

        // Calculate total duration
        long total = 0;
        boolean allSuccessOrSkipped = true;
        for (ScanOrchestrator.StageExecutionRecord record : stageResults) {
            total += record.durationMs;
            if (record.result.getStatus() == StageResult.Status.FAILED ||
                record.result.getStatus() == StageResult.Status.FATAL_ERROR) {
                allSuccessOrSkipped = false;
            }
        }
        this.totalDurationMs = total;
        this.success = allSuccessOrSkipped;

        try {
            metadata.put("scanId", scanId);
            metadata.put("scanTimestamp", System.currentTimeMillis());
            metadata.put("totalDurationMs", totalDurationMs);
            metadata.put("success", success);
            metadata.put("stageCount", stageResults.size());
        } catch (Exception e) {
            // Ignore metadata errors
        }
    }

    public String getScanId() {
        return scanId;
    }

    public JSONObject getMetadata() {
        return metadata;
    }

    public List<ScanOrchestrator.StageExecutionRecord> getStageResults() {
        return stageResults;
    }

    public File getOutputDirectory() {
        return outputDirectory;
    }

    public long getTotalDurationMs() {
        return totalDurationMs;
    }

    public boolean isSuccess() {
        return success;
    }

    /**
     * Generate a complete JSON representation of the scan report
     */
    public JSONObject toJson() {
        try {
            JSONObject root = new JSONObject();
            root.put("metadata", metadata);

            JSONArray stages = new JSONArray();
            for (ScanOrchestrator.StageExecutionRecord record : stageResults) {
                stages.put(record.toJson());
            }
            root.put("stages", stages);

            return root;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    /**
     * Generate a human-readable summary
     */
    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("Full Vehicle Scan Report\n");
        sb.append("=======================\n\n");

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        try {
            long timestamp = metadata.getLong("scanTimestamp");
            sb.append("Date: ").append(sdf.format(new Date(timestamp))).append("\n");
        } catch (Exception e) {
            // Ignore
        }

        sb.append("Scan ID: ").append(scanId).append("\n");
        sb.append("Duration: ").append(totalDurationMs / 1000.0).append(" seconds\n");
        sb.append("Status: ").append(success ? "SUCCESS" : "COMPLETED WITH ERRORS").append("\n\n");

        // Add vehicle info if available
        try {
            if (metadata.has("vin")) {
                sb.append("VIN: ").append(metadata.getString("vin")).append("\n");
            }
            if (metadata.has("make") && metadata.has("model") && metadata.has("year")) {
                sb.append("Vehicle: ").append(metadata.getInt("year")).append(" ")
                    .append(metadata.getString("make")).append(" ")
                    .append(metadata.getString("model")).append("\n");
            }
        } catch (Exception e) {
            // Ignore
        }

        sb.append("\nStage Results:\n");
        sb.append("--------------\n");

        int successCount = 0;
        int skippedCount = 0;
        int failedCount = 0;

        for (ScanOrchestrator.StageExecutionRecord record : stageResults) {
            sb.append(String.format("%-30s %s\n",
                record.stage.getDisplayName() + ":",
                record.result.getStatus().name()));

            if (record.result.getMessage() != null && !record.result.getMessage().isEmpty()) {
                sb.append("  → ").append(record.result.getMessage()).append("\n");
            }

            switch (record.result.getStatus()) {
                case SUCCESS:
                    successCount++;
                    break;
                case SKIPPED:
                    skippedCount++;
                    break;
                case FAILED:
                case FATAL_ERROR:
                    failedCount++;
                    break;
            }
        }

        sb.append("\n");
        sb.append("Total Stages: ").append(stageResults.size()).append("\n");
        sb.append("  Success: ").append(successCount).append("\n");
        sb.append("  Skipped: ").append(skippedCount).append("\n");
        sb.append("  Failed: ").append(failedCount).append("\n");

        return sb.toString();
    }
}
