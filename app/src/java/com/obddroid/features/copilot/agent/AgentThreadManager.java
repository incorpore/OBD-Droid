package com.obddroid.features.copilot.agent;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

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

    private final SharedPreferences prefs;
    private final AgentApiClient apiClient;

    public AgentThreadManager(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.apiClient = new AgentApiClient(context);
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

    // ========== PRIVATE HELPERS ==========

    private void saveThreadMapping(String vin, String threadId, JSONObject metadata) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_THREAD_PREFIX + vin, threadId);

        if (metadata != null) {
            editor.putString(KEY_METADATA_PREFIX + vin, metadata.toString());
        }

        editor.apply();
    }
}
