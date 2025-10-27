package com.obddroid.ecu;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages persistent caching of ECU scan results tied to specific VINs.
 * Saves scan results to SharedPreferences and loads them when the same vehicle is connected.
 *
 */
public class EcuCacheManager {
    private static final String TAG = "EcuCacheManager";
    private static final String PREFS_NAME = "ecu_cache";
    private static final String KEY_LAST_VIN = "last_vin";
    private static final String KEY_LAST_SCAN_TIME = "last_scan_time";
    private static final String KEY_ECU_DATA = "ecu_data";

    private final SharedPreferences prefs;

    public EcuCacheManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Save ECU scan results for a specific VIN
     */
    public void saveScanResults(String vin, List<EcuInfo> ecuList) {
        if (vin == null || vin.isEmpty()) {
            Log.w(TAG, "Cannot save ECU data - VIN is empty");
            return;
        }

        try {
            JSONArray ecuArray = new JSONArray();

            for (EcuInfo ecu : ecuList) {
                JSONObject ecuJson = new JSONObject();
                ecuJson.put("address", ecu.getAddress());
                ecuJson.put("name", ecu.getName() != null ? ecu.getName() : "");
                ecuJson.put("calibrationId", ecu.getCalibrationId() != null ? ecu.getCalibrationId() : "");
                ecuJson.put("calibrationVerification", ecu.getCalibrationVerification() != null ? ecu.getCalibrationVerification() : "");
                ecuJson.put("ecuType", ecu.getEcuType() != null ? ecu.getEcuType() : "");
                ecuArray.put(ecuJson);
            }

            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(KEY_LAST_VIN, vin);
            editor.putLong(KEY_LAST_SCAN_TIME, System.currentTimeMillis());
            editor.putString(KEY_ECU_DATA, ecuArray.toString());
            editor.apply();

            Log.i(TAG, "Saved " + ecuList.size() + " ECUs for VIN: " + maskVin(vin));

        } catch (JSONException e) {
            Log.e(TAG, "Error saving ECU data: " + e.getMessage(), e);
        }
    }

    /**
     * Load cached ECU scan results for a specific VIN
     * Returns null if no cache exists or VIN doesn't match
     */
    public List<EcuInfo> loadScanResults(String vin) {
        if (vin == null || vin.isEmpty()) {
            Log.w(TAG, "Cannot load ECU data - VIN is empty");
            return null;
        }

        String cachedVin = prefs.getString(KEY_LAST_VIN, null);
        if (cachedVin == null || !cachedVin.equals(vin)) {
            Log.i(TAG, "No cached data for VIN: " + maskVin(vin));
            return null;
        }

        String ecuDataJson = prefs.getString(KEY_ECU_DATA, null);
        if (ecuDataJson == null) {
            return null;
        }

        try {
            JSONArray ecuArray = new JSONArray(ecuDataJson);
            List<EcuInfo> ecuList = new ArrayList<>();

            for (int i = 0; i < ecuArray.length(); i++) {
                JSONObject ecuJson = ecuArray.getJSONObject(i);

                int address = ecuJson.getInt("address");
                String name = ecuJson.optString("name", "");
                String calId = ecuJson.optString("calibrationId", "");
                String cvn = ecuJson.optString("calibrationVerification", "");

                EcuInfo ecu = new EcuInfo(address);
                if (!name.isEmpty()) ecu.setName(name);
                if (!calId.isEmpty()) ecu.setCalibrationId(calId);
                if (!cvn.isEmpty()) ecu.setCalibrationVerification(cvn);

                ecuList.add(ecu);
            }

            long scanTime = prefs.getLong(KEY_LAST_SCAN_TIME, 0);
            Log.i(TAG, "Loaded " + ecuList.size() + " cached ECUs for VIN: " + maskVin(vin) +
                  " (scanned " + getTimeAgo(scanTime) + ")");

            return ecuList;

        } catch (JSONException e) {
            Log.e(TAG, "Error loading ECU data: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Clear all cached ECU data
     */
    public void clearCache() {
        prefs.edit().clear().apply();
        Log.i(TAG, "ECU cache cleared");
    }

    /**
     * Get the VIN of the last cached scan
     */
    public String getLastVin() {
        return prefs.getString(KEY_LAST_VIN, null);
    }

    /**
     * Get the timestamp of the last scan
     */
    public long getLastScanTime() {
        return prefs.getLong(KEY_LAST_SCAN_TIME, 0);
    }

    /**
     * Mask VIN for logging (show only last 4 digits)
     */
    private String maskVin(String vin) {
        if (vin == null || vin.length() < 4) return "****";
        return "***" + vin.substring(vin.length() - 4);
    }

    /**
     * Get human-readable time ago string
     */
    private String getTimeAgo(long timestamp) {
        if (timestamp == 0) return "unknown time";

        long diff = System.currentTimeMillis() - timestamp;
        long seconds = diff / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) return days + " day(s) ago";
        if (hours > 0) return hours + " hour(s) ago";
        if (minutes > 0) return minutes + " minute(s) ago";
        return "just now";
    }
}
