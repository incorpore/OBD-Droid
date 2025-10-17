package com.obddroid.services;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.obddroid.vehicle.AutoCheckReport;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Service to fetch AutoCheck vehicle history reports from the API
 * Runs API calls on background thread and returns results on main thread
 */
public class AutoCheckService {
    private static final String TAG = "AutoCheckService";

    // API Configuration - update this to your laptop's IP when testing from phone
    private static final String API_BASE_URL = "http://192.168.0.153:3248";
    private static final int TIMEOUT_MS = 60000; // 60 seconds for browser automation

    private final ExecutorService executor;
    private final Handler mainHandler;

    public interface AutoCheckCallback {
        void onSuccess(AutoCheckReport report);
        void onError(String errorMessage);
    }

    public AutoCheckService(Context context) {
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * Fetch AutoCheck report for a VIN
     * @param vin The 17-character VIN
     * @param callback Callback to receive results on main thread
     */
    public void fetchReport(String vin, AutoCheckCallback callback) {
        if (vin == null || vin.length() != 17) {
            mainHandler.post(() -> callback.onError("Invalid VIN. Must be 17 characters."));
            return;
        }

        executor.execute(() -> {
            try {
                Log.d(TAG, "Fetching AutoCheck report for VIN: " + vin);

                // Build API URL
                URL url = new URL(API_BASE_URL + "/api/autocheck/lookup");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();

                // Configure request
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setConnectTimeout(TIMEOUT_MS);
                conn.setReadTimeout(TIMEOUT_MS);
                conn.setDoOutput(true);

                // Build request body
                JSONObject requestBody = new JSONObject();
                requestBody.put("vin", vin);

                // Send request
                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = requestBody.toString().getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                // Check response code
                int responseCode = conn.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    String error = readErrorStream(conn);
                    Log.e(TAG, "API error " + responseCode + ": " + error);
                    mainHandler.post(() -> callback.onError("API Error: " + error));
                    return;
                }

                // Read response
                StringBuilder response = new StringBuilder();
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line.trim());
                    }
                }

                // Parse response
                JSONObject json = new JSONObject(response.toString());
                JSONObject data = json.getJSONObject("data");
                AutoCheckReport report = AutoCheckReport.fromJSON(data);

                Log.d(TAG, "Successfully fetched AutoCheck report: " + report.getVehicleName());

                // Return on main thread
                mainHandler.post(() -> callback.onSuccess(report));

            } catch (Exception e) {
                Log.e(TAG, "Error fetching AutoCheck report", e);
                String errorMsg = e.getMessage() != null ? e.getMessage() : "Unknown error";
                mainHandler.post(() -> callback.onError("Failed to fetch report: " + errorMsg));
            }
        });
    }

    /**
     * Decode VIN to get basic vehicle info (faster, doesn't count against rate limit)
     */
    public void decodeVIN(String vin, AutoCheckCallback callback) {
        if (vin == null || vin.length() != 17) {
            mainHandler.post(() -> callback.onError("Invalid VIN. Must be 17 characters."));
            return;
        }

        executor.execute(() -> {
            try {
                Log.d(TAG, "Decoding VIN: " + vin);

                URL url = new URL(API_BASE_URL + "/api/autocheck/decode");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                conn.setDoOutput(true);

                JSONObject requestBody = new JSONObject();
                requestBody.put("vin", vin);

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = requestBody.toString().getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int responseCode = conn.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    String error = readErrorStream(conn);
                    mainHandler.post(() -> callback.onError("Decode failed: " + error));
                    return;
                }

                StringBuilder response = new StringBuilder();
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line.trim());
                    }
                }

                JSONObject data = new JSONObject(response.toString());
                AutoCheckReport report = AutoCheckReport.fromJSON(data);

                mainHandler.post(() -> callback.onSuccess(report));

            } catch (Exception e) {
                Log.e(TAG, "Error decoding VIN", e);
                mainHandler.post(() -> callback.onError("Failed to decode: " + e.getMessage()));
            }
        });
    }

    /**
     * Check API health status
     */
    public void checkHealth(HealthCallback callback) {
        executor.execute(() -> {
            try {
                URL url = new URL(API_BASE_URL + "/health");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                int responseCode = conn.getResponseCode();
                boolean healthy = (responseCode == HttpURLConnection.HTTP_OK);

                mainHandler.post(() -> callback.onHealthCheck(healthy));

            } catch (Exception e) {
                Log.e(TAG, "Health check failed", e);
                mainHandler.post(() -> callback.onHealthCheck(false));
            }
        });
    }

    public interface HealthCallback {
        void onHealthCheck(boolean healthy);
    }

    private String readErrorStream(HttpURLConnection conn) {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        } catch (Exception e) {
            return "Unknown error";
        }
    }

    /**
     * Shutdown executor when service is no longer needed
     */
    public void shutdown() {
        executor.shutdown();
    }
}
