package com.obddroid.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Simple VIN Decoder using Corgi's complete offline vPIC database
 *
 * Provides dealer-grade VIN decoding with 140+ fields - all offline!
 * No internet connection required.
 *
 * Powered by Corgi (@cardog/corgi) - 66MB vPIC database
 */
public class EnhancedVINDecoder {

    private static final String TAG = "EnhancedVINDecoder";

    private final CorgiVINDecoder decoder;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface DecodeCallback {
        void onSuccess(VehicleData vehicleData);
        void onError(String error);
    }

    public EnhancedVINDecoder(Context context) {
        this.decoder = new CorgiVINDecoder(context);
        Log.d(TAG, "✓ Corgi VIN Decoder initialized - Full offline vPIC database loaded");
    }

    /**
     * Decode VIN asynchronously
     */
    public void decodeAsync(String vin, DecodeCallback callback) {
        executor.execute(() -> {
            try {
                Log.d(TAG, "Decoding VIN: " + vin);

                CorgiVINDecoder.VehicleInfo info = decoder.decode(vin);
                VehicleData vehicleData = VehicleData.fromCorgiInfo(info);

                if (vehicleData.isValid()) {
                    Log.d(TAG, String.format("✓ Decoded: %s %s %s",
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
     */
    public VehicleData decode(String vin) {
        CorgiVINDecoder.VehicleInfo info = decoder.decode(vin);
        return VehicleData.fromCorgiInfo(info);
    }

    /**
     * Get manufacturer name from VIN (fast)
     */
    public String getManufacturer(String vin) {
        return decoder.getManufacturer(vin);
    }

    /**
     * Get make name from VIN (fast)
     */
    public String getMake(String vin) {
        return decoder.getMake(vin);
    }

    /**
     * Validate VIN format
     */
    public boolean validate(String vin) {
        return decoder.validate(vin);
    }

    /**
     * Shutdown decoder and cleanup resources
     */
    public void shutdown() {
        executor.shutdown();
        decoder.close();
        Log.d(TAG, "VIN Decoder shutdown");
    }
}
