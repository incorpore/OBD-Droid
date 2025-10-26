package com.obddroid.scan;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages scan results storage, retrieval, and provides access to latest scan data.
 * Used by CoPilot to reference scan findings in conversations.
 */
public final class ScanResultsManager {

    private static final String TAG = "ScanResultsManager";
    private static final String PREFS_NAME = "scan_results";
    private static final String KEY_LATEST_SCAN_ID = "latest_scan_id";
    private static final String KEY_LATEST_SCAN_TIMESTAMP = "latest_scan_timestamp";

    private static ScanResultsManager instance;

    private final Context appContext;
    private final SharedPreferences preferences;
    private final ConcurrentHashMap<String, ScanReport> reportsCache;
    private volatile ScanReport latestReport;

    private ScanResultsManager(Context context) {
        this.appContext = context.getApplicationContext();
        this.preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.reportsCache = new ConcurrentHashMap<>();
        loadLatestReport();
    }

    public static synchronized ScanResultsManager getInstance(Context context) {
        if (instance == null) {
            instance = new ScanResultsManager(context);
        }
        return instance;
    }

    /**
     * Store a completed scan report
     */
    public void storeScanReport(ScanReport report) {
        if (report == null) {
            Log.w(TAG, "Attempted to store null report");
            return;
        }

        // Update latest reference
        this.latestReport = report;
        reportsCache.put(report.getScanId(), report);

        // Persist metadata
        preferences.edit()
            .putString(KEY_LATEST_SCAN_ID, report.getScanId())
            .putLong(KEY_LATEST_SCAN_TIMESTAMP, System.currentTimeMillis())
            .apply();

        // Write full report to disk
        writeReportToDisk(report);

        Log.i(TAG, "Stored scan report: " + report.getScanId());
    }

    /**
     * Get the most recent scan report
     */
    public ScanReport getLatestReport() {
        return latestReport;
    }

    /**
     * Check if any scan results are available
     */
    public boolean hasScans() {
        return latestReport != null;
    }

    /**
     * Get a summary of the latest scan for CoPilot context
     */
    public JSONObject getLatestScanSummary() {
        if (latestReport == null) {
            return null;
        }

        try {
            JSONObject summary = new JSONObject();
            summary.put("scanId", latestReport.getScanId());
            summary.put("timestamp", preferences.getLong(KEY_LATEST_SCAN_TIMESTAMP, 0));
            summary.put("success", latestReport.isSuccess());
            summary.put("durationMs", latestReport.getTotalDurationMs());

            // Add key findings
            JSONObject findings = new JSONObject();

            // Count DTCs from stage results
            for (ScanOrchestrator.StageExecutionRecord record : latestReport.getStageResults()) {
                if ("FAULT_CODES".equals(record.stage.getId()) && record.result.getData() != null) {
                    JSONObject data = record.result.getData();
                    findings.put("totalDTCs", data.optInt("totalCount", 0));
                    findings.put("confirmedDTCs", data.optInt("confirmedCount", 0));
                    findings.put("pendingDTCs", data.optInt("pendingCount", 0));
                    findings.put("permanentDTCs", data.optInt("permanentCount", 0));
                }
            }

            summary.put("findings", findings);
            summary.put("stageCount", latestReport.getStageResults().size());

            return summary;

        } catch (JSONException e) {
            Log.e(TAG, "Failed to build scan summary", e);
            return null;
        }
    }

    /**
     * Get detailed scan data for AI analysis
     */
    public JSONObject getLatestScanDetailedData() {
        if (latestReport == null) {
            return null;
        }
        return latestReport.toJson();
    }

    /**
     * Get scan by ID from cache or disk
     */
    public ScanReport getScanById(String scanId) {
        // Check cache first
        ScanReport cached = reportsCache.get(scanId);
        if (cached != null) {
            return cached;
        }

        // TODO: Load from disk if needed
        return null;
    }

    /**
     * Get list of available scan IDs
     */
    public List<String> getAvailableScanIds() {
        List<String> ids = new ArrayList<>(reportsCache.keySet());

        // TODO: Also scan disk for historical scans
        File scansDir = new File(appContext.getExternalFilesDir(null), "scans");
        if (scansDir.exists() && scansDir.isDirectory()) {
            File[] scanDirs = scansDir.listFiles(File::isDirectory);
            if (scanDirs != null) {
                for (File dir : scanDirs) {
                    String id = dir.getName();
                    if (!ids.contains(id)) {
                        ids.add(id);
                    }
                }
            }
        }

        return ids;
    }

    private void loadLatestReport() {
        String latestId = preferences.getString(KEY_LATEST_SCAN_ID, null);
        if (latestId != null) {
            // Try to load from disk
            // For now, it will be populated when a new scan completes
            Log.i(TAG, "Latest scan ID: " + latestId);
        }
    }

    private void writeReportToDisk(ScanReport report) {
        ReportArtifacts artifacts = report.getArtifacts();

        File jsonFile = artifacts != null && artifacts.getJsonFile() != null
            ? artifacts.getJsonFile()
            : new File(report.getOutputDirectory(), "scan_report.json");

        File summaryFile = artifacts != null && artifacts.getSummaryFile() != null
            ? artifacts.getSummaryFile()
            : new File(report.getOutputDirectory(), "scan_summary.txt");

        if (jsonFile.exists() && summaryFile.exists()) {
            Log.i(TAG, "Report artifacts already present on disk for " + report.getScanId());
            return;
        }

        try {
            if (!jsonFile.getParentFile().exists() && !jsonFile.getParentFile().mkdirs()) {
                Log.w(TAG, "Failed to create directory for report JSON: " + jsonFile.getParent());
            }
            if (!jsonFile.exists()) {
                try (FileWriter writer = new FileWriter(jsonFile)) {
                    writer.write(report.toJson().toString(2));
                }
                Log.i(TAG, "Wrote report JSON to: " + jsonFile.getAbsolutePath());
            }

            if (!summaryFile.getParentFile().exists() && !summaryFile.getParentFile().mkdirs()) {
                Log.w(TAG, "Failed to create directory for report summary: " + summaryFile.getParent());
            }
            if (!summaryFile.exists()) {
                try (FileWriter writer = new FileWriter(summaryFile)) {
                    writer.write(report.getSummary());
                }
                Log.i(TAG, "Wrote report summary to: " + summaryFile.getAbsolutePath());
            }

        } catch (IOException | JSONException e) {
            Log.e(TAG, "Failed to write report to disk", e);
        }
    }

    /**
     * Clear all cached reports (doesn't delete files)
     */
    public void clearCache() {
        reportsCache.clear();
        latestReport = null;
    }
}
