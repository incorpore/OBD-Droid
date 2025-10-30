package com.obddroid.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Hybrid VIN Decoder - Online-first with Offline Fallback
 *
 * Strategy:
 * 1. Try NHTSA API first (when internet available) - most complete data
 * 2. Fall back to offline vPIC database if online fails
 * 3. Mark fields that require internet when using offline mode
 *
 * Powered by:
 * - Primary: NHTSA vPIC API (online)
 * - Fallback: NHTSA Offline Decoder - 66MB vPIC database (offline)
 *
 * @author Wal33D <aquataze@yahoo.com>
 */
public class EnhancedVINDecoder {

    private static final String TAG = "EnhancedVINDecoder";

    private final NhtsaVINDecoder onlineDecoder;
    private final NhtsaOfflineVINDecoder offlineDecoder;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface DecodeCallback {
        void onSuccess(VehicleData vehicleData);
        void onError(String error);
    }

    public EnhancedVINDecoder(Context context) {
        this.onlineDecoder = new NhtsaVINDecoder(context);
        this.offlineDecoder = new NhtsaOfflineVINDecoder(context);
        Log.d(TAG, "✓ Hybrid VIN Decoder initialized (Online + Offline)");
    }

    /**
     * Decode VIN asynchronously
     * Tries online first, falls back to offline if needed
     */
    public void decodeAsync(String vin, DecodeCallback callback) {
        executor.execute(() -> {
            try {
                Log.d(TAG, "Decoding VIN: " + vin);

                VehicleData vehicleData = decodeHybrid(vin);

                if (vehicleData.isValid()) {
                    Log.d(TAG, String.format("✓ Decoded (%s): %s %s %s",
                            vehicleData.dataSource,
                            vehicleData.getModelYear(),
                            vehicleData.getMake(),
                            vehicleData.getModel()));
                } else {
                    Log.w(TAG, "✗ Decode failed: " + vehicleData.getErrorMessage());
                }

                // Callback on main thread
                mainHandler.post(() -> {
                    if (vehicleData.isValid()) {
                        callback.onSuccess(vehicleData);
                    } else {
                        callback.onError(vehicleData.getErrorMessage());
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Decode error", e);
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    /**
     * Decode VIN synchronously
     * Tries online first, falls back to offline if needed
     */
    public VehicleData decode(String vin) {
        return decodeHybrid(vin);
    }

    /**
     * Hybrid decode strategy
     */
    private VehicleData decodeHybrid(String vin) {
        // Try online first
        VehicleData onlineData = onlineDecoder.decode(vin);
        if (onlineData != null && onlineData.isValid()) {
            Log.d(TAG, "✓ Using online data from NHTSA API");
            return onlineData;
        }

        // Fall back to offline
        Log.d(TAG, "Online decode failed, falling back to offline database");
        NhtsaOfflineVINDecoder.VehicleInfo info = offlineDecoder.decode(vin);
        VehicleData offlineData = VehicleData.fromOfflineDecoder(info);
        offlineData.dataSource = "Offline Database";

        // Add indicators for missing fields
        if (offlineData.isValid()) {
            addOfflineIndicators(offlineData);
        }

        return offlineData;
    }

    /**
     * Add "Requires internet" indicators for fields not available offline
     */
    private void addOfflineIndicators(VehicleData data) {
        // Fields that are typically missing in offline mode
        if (data.engineCylinders == null || data.engineCylinders.isEmpty()) {
            data.engineCylinders = "Requires internet";
        }
        if (data.displacementCC == null || data.displacementCC.isEmpty()) {
            data.displacementCC = "Requires internet";
        }
        if (data.driveType == null || data.driveType.isEmpty()) {
            data.driveType = "Requires internet";
        }
        if (data.transmissionStyle == null || data.transmissionStyle.isEmpty()) {
            data.transmissionStyle = "Requires internet";
        }
        if (data.transmissionSpeeds == null || data.transmissionSpeeds.isEmpty()) {
            data.transmissionSpeeds = "Requires internet";
        }
        if (data.wheelBase == null || data.wheelBase.isEmpty()) {
            data.wheelBase = "Requires internet";
        }
        if (data.plantCity == null || data.plantCity.isEmpty()) {
            data.plantCity = "Requires internet";
        }
    }

    /**
     * Get manufacturer name from VIN (fast, offline only)
     */
    public String getManufacturer(String vin) {
        return offlineDecoder.getManufacturer(vin);
    }

    /**
     * Get make name from VIN (fast, offline only)
     */
    public String getMake(String vin) {
        return offlineDecoder.getMake(vin);
    }

    /**
     * Validate VIN format
     */
    public boolean validate(String vin) {
        return offlineDecoder.validate(vin);
    }

    /**
     * Reload database (called after automatic update)
     */
    public void reloadDatabase() {
        Log.d(TAG, "Reloading VIN database after update");
        offlineDecoder.reloadDatabase();
    }

    /**
     * Shutdown decoder and cleanup resources
     */
    public void shutdown() {
        executor.shutdown();
        offlineDecoder.close();
        Log.d(TAG, "VIN Decoder shutdown");
    }
}
