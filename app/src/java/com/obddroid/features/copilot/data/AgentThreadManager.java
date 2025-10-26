package com.obddroid.features.copilot.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.obddroid.scan.ScanResultsManager;

/**
 * Manages persistent storage of Agent API threads.
 * Threads are keyed by VIN (or session ID) to enable conversation resumption.
 *
 * Storage Strategy:
 * - SharedPreferences for thread_id → VIN/session mapping
 * - Thread metadata stored locally for offline access
 * - Automatic cleanup of old/stale threads
 */
public class AgentThreadManager {

    private static final String TAG = "AgentThreadManager";
    private static final String PREFS_NAME = "agent_threads";
    private static final String KEY_ASSISTANT_ID = "assistant_id";
    private static final String KEY_THREAD_PREFIX = "thread_";
    private static final String KEY_METADATA_PREFIX = "metadata_";

    private final Context appContext;
    private final SharedPreferences prefs;
    private final AgentApiClient apiClient;

    public AgentThreadManager(Context context) {
        this.appContext = context.getApplicationContext();
        this.prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.apiClient = new AgentApiClient(appContext);
    }

    /**
     * Get or create a thread for the specified vehicle/session.
     * Returns existing thread if available, otherwise creates new.
     *
     * @param vin Vehicle Identification Number (or session ID)
     * @param metadata Vehicle metadata (make, model, year, etc.)
     * @return Thread ID
     */
    public String getOrCreateThread(String vin, JSONObject metadata) throws Exception {
        if (vin == null || vin.isEmpty()) {
            throw new IllegalArgumentException("VIN cannot be null or empty");
        }

        // Check if thread already exists for this VIN
        String existingThreadId = getThreadIdForVin(vin);
        if (existingThreadId != null) {
            try {
                // Verify thread still exists on server
                apiClient.retrieveThread(existingThreadId);
                Log.i(TAG, "Resuming existing thread " + existingThreadId + " for VIN " + vin);
                return existingThreadId;
            } catch (Exception e) {
                Log.w(TAG, "Existing thread " + existingThreadId + " not found, creating new", e);
                // Fall through to create new thread
            }
        }

        // Create new thread
        AgentApiClient.Thread thread = apiClient.createThread(metadata);
        saveThreadMapping(vin, thread.id, metadata);

        Log.i(TAG, "Created new thread " + thread.id + " for VIN " + vin);
        return thread.id;
    }

    /**
     * Get thread ID for a specific VIN (if exists).
     */
    public String getThreadIdForVin(String vin) {
        return prefs.getString(KEY_THREAD_PREFIX + vin, null);
    }

    /**
     * Get stored metadata for a thread.
     */
    public JSONObject getThreadMetadata(String vin) {
        String metadataJson = prefs.getString(KEY_METADATA_PREFIX + vin, null);
        if (metadataJson != null) {
            try {
                return new JSONObject(metadataJson);
            } catch (JSONException e) {
                Log.w(TAG, "Failed to parse metadata for VIN " + vin, e);
            }
        }
        return null;
    }

    /**
     * Delete a thread both locally and from the server.
     */
    public void deleteThread(String vin) throws Exception {
        String threadId = getThreadIdForVin(vin);
        if (threadId != null) {
            // Delete from server
            apiClient.deleteThread(threadId);

            // Remove from local storage
            SharedPreferences.Editor editor = prefs.edit();
            editor.remove(KEY_THREAD_PREFIX + vin);
            editor.remove(KEY_METADATA_PREFIX + vin);
            editor.apply();

            Log.i(TAG, "Deleted thread " + threadId + " for VIN " + vin);
        }
    }

    /**
     * List all stored thread VINs.
     */
    public Map<String, String> getAllThreadMappings() {
        Map<String, String> mappings = new HashMap<>();
        Map<String, ?> all = prefs.getAll();

        for (Map.Entry<String, ?> entry : all.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(KEY_THREAD_PREFIX)) {
                String vin = key.substring(KEY_THREAD_PREFIX.length());
                String threadId = (String) entry.getValue();
                mappings.put(vin, threadId);
            }
        }

        return mappings;
    }

    /**
     * Delete all threads (both locally and from server).
     * Use for "Delete All My Data" privacy feature.
     */
    public void deleteAllThreads() {
        Map<String, String> mappings = getAllThreadMappings();

        for (Map.Entry<String, String> entry : mappings.entrySet()) {
            String vin = entry.getKey();
            try {
                deleteThread(vin);
            } catch (Exception e) {
                Log.e(TAG, "Failed to delete thread for VIN " + vin, e);
                // Continue deleting others
            }
        }

        Log.i(TAG, "Deleted all threads (" + mappings.size() + " total)");
    }

    /**
     * Store the assistant ID (created once per app install).
     */
    public void saveAssistantId(String assistantId) {
        prefs.edit().putString(KEY_ASSISTANT_ID, assistantId).apply();
        Log.i(TAG, "Saved assistant ID: " + assistantId);
    }

    /**
     * Retrieve the stored assistant ID.
     */
    public String getAssistantId() {
        return prefs.getString(KEY_ASSISTANT_ID, null);
    }

    /**
     * Build thread overviews including recent scan history for UI consumption.
     * @return Ordered list suitable for ThreadManager UI.
     */
    public List<ThreadOverview> getThreadOverviews() {
        return getThreadOverviews(3);
    }

    public List<ThreadOverview> getThreadOverviews(int scanHistoryLimit) {
        Map<String, String> mappings = getAllThreadMappings();
        if (mappings.isEmpty()) {
            return new ArrayList<>();
        }

        ScanResultsManager scanResultsManager = ScanResultsManager.getInstance(appContext);
        List<ThreadOverview> overviews = new ArrayList<>(mappings.size());

        for (Map.Entry<String, String> entry : mappings.entrySet()) {
            String vin = entry.getKey();
            String threadId = entry.getValue();
            JSONObject metadata = getThreadMetadata(vin);
            List<ScanResultsManager.ScanSummary> history =
                scanResultsManager.getRecentScanSummariesForVin(vin, Math.max(1, scanHistoryLimit));
            overviews.add(new ThreadOverview(vin, threadId, metadata, history));
        }

        return overviews;
    }

    // ========== PRIVATE HELPERS ==========

    private void saveThreadMapping(String vin, String threadId, JSONObject metadata) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_THREAD_PREFIX + vin, threadId);

        if (metadata != null) {
            editor.putString(KEY_METADATA_PREFIX + vin, metadata.toString());
        }

        editor.apply();
    }

    /**
     * Container for per-thread diagnostics + scan history.
     * Provides convenience helpers for the upcoming Thread Manager UI.
     */
    public static final class ThreadOverview {
        private static final SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);

        public final String vin;
        public final String threadId;
        public final JSONObject metadata;
        public final List<ScanResultsManager.ScanSummary> scanHistory;

        ThreadOverview(String vin,
                       String threadId,
                       JSONObject metadata,
                       List<ScanResultsManager.ScanSummary> scanHistory) {
            this.vin = vin;
            this.threadId = threadId;
            this.metadata = metadata;
            this.scanHistory = scanHistory != null ? scanHistory : new ArrayList<>();
        }

        public boolean hasScanHistory() {
            return !scanHistory.isEmpty();
        }

        public ScanResultsManager.ScanSummary latestScan() {
            return hasScanHistory() ? scanHistory.get(0) : null;
        }

        public ScanResultsManager.ScanSummary previousScan() {
            return scanHistory.size() > 1 ? scanHistory.get(1) : null;
        }

        public String latestScanLabel() {
            ScanResultsManager.ScanSummary latest = latestScan();
            if (latest == null) {
                return "No scans yet";
            }
            return formatScanLabel(latest);
        }

        public String buildComparePrompt() {
            ScanResultsManager.ScanSummary latest = latestScan();
            ScanResultsManager.ScanSummary previous = previousScan();
            if (latest == null || previous == null) {
                return null;
            }
            return "Compare scan " + latest.scanId + " from " + formatDate(latest.timestamp) +
                " with scan " + previous.scanId + " from " + formatDate(previous.timestamp) +
                " for VIN " + vin + ". Highlight new or cleared fault codes, monitor state changes, " +
                "and any significant live data differences.";
        }

        private String formatScanLabel(ScanResultsManager.ScanSummary summary) {
            return summary.scanId + " • " + formatDate(summary.timestamp) +
                " • " + (summary.success ? "Success" : "Issues");
        }

        private String formatDate(long timestamp) {
            return DATE_FORMAT.format(new Date(timestamp));
        }
    }
}
