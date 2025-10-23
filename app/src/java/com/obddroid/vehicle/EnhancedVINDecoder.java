package com.obddroid.vehicle;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import io.github.vindecoder.android.VINDecoderAndroid;
import io.github.vindecoder.nhtsa.VINDecoderService;
import io.github.vindecoder.nhtsa.VehicleData;

/**
 * Enhanced VIN Decoder that tries online NHTSA API first, falls back to offline
 *
 * This provides the best of both worlds:
 * - Full 140+ field data from NHTSA when online
 * - Basic offline decoding when no network available
 */
public class EnhancedVINDecoder {

    private static final String TAG = "EnhancedVINDecoder";

    private final Context context;
    private final VINDecoderService onlineDecoder;
    private final VINDecoderAndroid offlineDecoder;

    public interface DecodeCallback {
        void onSuccess(VehicleData vehicleData);
        void onError(String error);
    }

    public EnhancedVINDecoder(Context context) {
        this.context = context.getApplicationContext();
        this.onlineDecoder = VINDecoderService.getInstance();
        this.offlineDecoder = new VINDecoderAndroid(this.context);
    }

    /**
     * Decode VIN - tries online first, falls back to offline
     */
    public void decodeAsync(String vin, DecodeCallback callback) {
        if (isNetworkAvailable()) {
            // Try online first (full data)
            Log.d(TAG, "Attempting online decode (NHTSA API)");
            onlineDecoder.decodeVIN(vin, new VINDecoderService.VINDecoderCallback() {
                @Override
                public void onSuccess(VehicleData vehicleData) {
                    Log.d(TAG, "✓ Online decode successful - Full data available");
                    callback.onSuccess(vehicleData);
                }

                @Override
                public void onError(String error) {
                    Log.w(TAG, "Online decode failed: " + error + ", falling back to offline");
                    decodeOffline(vin, callback);
                }
            });
        } else {
            // No network - use offline
            Log.d(TAG, "No network - using offline decoder");
            decodeOffline(vin, callback);
        }
    }

    private void decodeOffline(String vin, DecodeCallback callback) {
        offlineDecoder.decodeAsync(vin, new VINDecoderAndroid.DecodeCallback() {
            @Override
            public void onSuccess(VehicleData vehicleData) {
                Log.d(TAG, "✓ Offline decode successful - Basic data only");
                callback.onSuccess(vehicleData);
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "✗ Offline decode failed: " + error);
                callback.onError(error);
            }
        });
    }

    /**
     * Synchronous decode (uses offline decoder only)
     * For async with online support, use decodeAsync()
     */
    public VehicleData decode(String vin) {
        return offlineDecoder.decode(vin);
    }

    /**
     * Get manufacturer name from VIN (offline, fast)
     */
    public String getManufacturer(String vin) {
        return offlineDecoder.getManufacturer(vin);
    }

    public boolean validate(String vin) {
        return offlineDecoder.validate(vin);
    }

    private boolean isNetworkAvailable() {
        try {
            ConnectivityManager cm = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                NetworkInfo networkInfo = cm.getActiveNetworkInfo();
                return networkInfo != null && networkInfo.isConnected();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking network: " + e.getMessage());
        }
        return false;
    }
}
