package com.obddroid.vehicle;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Manages ECU scan history
 * Saves and loads scan snapshots for comparison and tracking
 */
public class EcuScanHistoryManager {
    private static final String TAG = "EcuScanHistory";
    private static final String HISTORY_DIR = "ecu_history";
    private static final int MAX_SCANS_PER_VIN = 50;  // Keep last 50 scans

    private Context context;

    public EcuScanHistoryManager(Context context) {
        this.context = context;
    }

    /**
     * Save a new ECU scan to history
     */
    public boolean saveScan(EcuScan scan) {
        try {
            File historyDir = new File(context.getFilesDir(), HISTORY_DIR);
            if (!historyDir.exists()) {
                historyDir.mkdirs();
            }

            // Generate filename: vin_timestamp.json
            String vinSafe = scan.getVin() != null ? scan.getVin().replaceAll("[^a-zA-Z0-9]", "_") : "unknown";
            String filename = vinSafe + "_" + scan.getTimestamp() + ".json";
            File scanFile = new File(historyDir, filename);

            // Convert scan to JSON
            JSONObject json = scanToJson(scan);

            // Write to file
            FileWriter writer = new FileWriter(scanFile);
            writer.write(json.toString(2));  // Pretty print with indent
            writer.close();

            Log.i(TAG, "Saved scan: " + filename);

            // Clean up old scans if needed
            cleanupOldScans(scan.getVin());

            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error saving scan: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Get all scans for a specific VIN
     */
    public List<EcuScan> getScansForVin(String vin) {
        List<EcuScan> scans = new ArrayList<>();

        try {
            File historyDir = new File(context.getFilesDir(), HISTORY_DIR);
            if (!historyDir.exists()) {
                return scans;
            }

            String vinSafe = vin != null ? vin.replaceAll("[^a-zA-Z0-9]", "_") : "unknown";

            File[] files = historyDir.listFiles((dir, name) ->
                    name.startsWith(vinSafe + "_") && name.endsWith(".json"));

            if (files != null) {
                for (File file : files) {
                    try {
                        EcuScan scan = loadScanFromFile(file);
                        if (scan != null) {
                            scans.add(scan);
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Error loading scan file: " + file.getName(), e);
                    }
                }
            }

            // Sort by timestamp (newest first)
            Collections.sort(scans, new Comparator<EcuScan>() {
                @Override
                public int compare(EcuScan s1, EcuScan s2) {
                    return Long.compare(s2.getTimestamp(), s1.getTimestamp());
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Error getting scans for VIN: " + e.getMessage(), e);
        }

        return scans;
    }

    /**
     * Get most recent scan for a VIN
     */
    public EcuScan getLatestScan(String vin) {
        List<EcuScan> scans = getScansForVin(vin);
        return scans.isEmpty() ? null : scans.get(0);
    }

    /**
     * Delete a scan
     */
    public boolean deleteScan(EcuScan scan) {
        try {
            File historyDir = new File(context.getFilesDir(), HISTORY_DIR);
            String vinSafe = scan.getVin() != null ? scan.getVin().replaceAll("[^a-zA-Z0-9]", "_") : "unknown";
            String filename = vinSafe + "_" + scan.getTimestamp() + ".json";
            File scanFile = new File(historyDir, filename);

            if (scanFile.exists()) {
                return scanFile.delete();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error deleting scan: " + e.getMessage(), e);
        }
        return false;
    }

    /**
     * Clean up old scans, keeping only the most recent MAX_SCANS_PER_VIN
     */
    private void cleanupOldScans(String vin) {
        List<EcuScan> scans = getScansForVin(vin);
        if (scans.size() > MAX_SCANS_PER_VIN) {
            // Delete oldest scans
            for (int i = MAX_SCANS_PER_VIN; i < scans.size(); i++) {
                deleteScan(scans.get(i));
            }
            Log.i(TAG, "Cleaned up " + (scans.size() - MAX_SCANS_PER_VIN) + " old scans");
        }
    }

    /**
     * Convert EcuScan to JSON
     */
    private JSONObject scanToJson(EcuScan scan) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("timestamp", scan.getTimestamp());
        json.put("vin", scan.getVin());
        json.put("label", scan.getLabel());

        JSONArray ecusArray = new JSONArray();
        if (scan.getEcus() != null) {
            for (EcuInfo ecu : scan.getEcus()) {
                JSONObject ecuJson = new JSONObject();
                ecuJson.put("address", ecu.getAddress());
                ecuJson.put("name", ecu.getName());
                ecuJson.put("calibrationId", ecu.getCalibrationId());
                ecuJson.put("cvn", ecu.getCalibrationVerification());
                ecusArray.put(ecuJson);
            }
        }
        json.put("ecus", ecusArray);

        return json;
    }

    /**
     * Load EcuScan from JSON file
     */
    private EcuScan loadScanFromFile(File file) throws IOException, JSONException {
        // Read file
        FileReader reader = new FileReader(file);
        StringBuilder content = new StringBuilder();
        char[] buffer = new char[1024];
        int read;
        while ((read = reader.read(buffer)) != -1) {
            content.append(buffer, 0, read);
        }
        reader.close();

        // Parse JSON
        JSONObject json = new JSONObject(content.toString());
        long timestamp = json.getLong("timestamp");
        String vin = json.optString("vin", null);
        String label = json.optString("label", null);

        List<EcuInfo> ecus = new ArrayList<>();
        JSONArray ecusArray = json.getJSONArray("ecus");
        for (int i = 0; i < ecusArray.length(); i++) {
            JSONObject ecuJson = ecusArray.getJSONObject(i);
            int address = ecuJson.getInt("address");
            EcuInfo ecu = new EcuInfo(address);
            ecu.setName(ecuJson.optString("name", null));
            ecu.setCalibrationId(ecuJson.optString("calibrationId", null));
            ecu.setCalibrationVerification(ecuJson.optString("cvn", null));
            ecus.add(ecu);
        }

        return new EcuScan(timestamp, vin, ecus, label);
    }
}
