package com.obddroid.vehicle;

import android.util.Log;
import com.obddroid.api.nhtsa.VINDecoderService;
import com.obddroid.api.nhtsa.VehicleData;
import java.util.ArrayList;
import java.util.List;

/**
 * Global Vehicle Manager - Single source of truth for connected vehicle information
 *
 * This singleton manages:
 * - Current VIN and decoded vehicle data
 * - Notifying all listeners when vehicle info changes
 * - Caching decoded VINs
 * - Providing vehicle info to any component in the app
 *
 * Architecture: This decouples VIN decoding from UI components
 */
public class VehicleManager {

    private static final String TAG = "VehicleManager";
    private static VehicleManager instance;

    // Current vehicle state
    private String currentVIN = null;
    private VehicleData currentVehicleData = null;
    private boolean isDecoding = false;

    // Listeners for vehicle changes
    private final List<VehicleChangeListener> listeners = new ArrayList<>();

    // VIN decoder service
    private final VINDecoderService vinDecoder;

    /**
     * Listener interface for vehicle changes
     */
    public interface VehicleChangeListener {
        void onVINChanged(String vin);
        void onVehicleDecoded(VehicleData vehicleData);
        void onVehicleDisconnected();
        void onDecodingStarted();
        void onDecodingError(String error);
    }

    /**
     * Simple listener adapter - implement only what you need
     */
    public static class SimpleVehicleChangeListener implements VehicleChangeListener {
        @Override public void onVINChanged(String vin) {}
        @Override public void onVehicleDecoded(VehicleData vehicleData) {}
        @Override public void onVehicleDisconnected() {}
        @Override public void onDecodingStarted() {}
        @Override public void onDecodingError(String error) {}
    }

    private VehicleManager() {
        vinDecoder = VINDecoderService.getInstance();
    }

    /**
     * Get singleton instance
     */
    public static synchronized VehicleManager getInstance() {
        if (instance == null) {
            instance = new VehicleManager();
        }
        return instance;
    }

    /**
     * Register a listener for vehicle changes
     */
    public void addListener(VehicleChangeListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);

            // Immediately notify of current state
            if (currentVehicleData != null) {
                listener.onVehicleDecoded(currentVehicleData);
            } else if (currentVIN != null) {
                listener.onVINChanged(currentVIN);
                if (isDecoding) {
                    listener.onDecodingStarted();
                }
            }
        }
    }

    /**
     * Unregister a listener
     */
    public void removeListener(VehicleChangeListener listener) {
        listeners.remove(listener);
    }

    /**
     * Set the current VIN (called when VIN is read from OBD)
     * This will automatically trigger decoding
     */
    public void setVIN(String vin) {
        if (vin == null || vin.trim().isEmpty()) {
            clearVehicle();
            return;
        }

        vin = vin.trim().toUpperCase();

        // Check if VIN actually changed
        if (vin.equals(currentVIN)) {
            Log.d(TAG, "VIN unchanged: " + vin);
            return;
        }

        Log.d(TAG, "New VIN detected: " + vin);
        currentVIN = vin;
        isDecoding = true;

        // Notify listeners of VIN change
        for (VehicleChangeListener listener : listeners) {
            listener.onVINChanged(vin);
            listener.onDecodingStarted();
        }

        // Check if already cached in decoder service
        VehicleData cached = vinDecoder.getCached(vin);
        if (cached != null) {
            handleDecodedVehicle(cached);
            return;
        }

        // Start async decoding
        vinDecoder.decodeVIN(vin, new VINDecoderService.VINDecoderCallback() {
            @Override
            public void onSuccess(VehicleData vehicleData) {
                handleDecodedVehicle(vehicleData);
            }

            @Override
            public void onError(String error) {
                handleDecodingError(error);
            }
        });
    }

    /**
     * Handle successfully decoded vehicle
     */
    private void handleDecodedVehicle(VehicleData vehicleData) {
        Log.d(TAG, "Vehicle decoded: " + vehicleData.getDisplayName());
        currentVehicleData = vehicleData;
        isDecoding = false;

        // Notify all listeners
        for (VehicleChangeListener listener : listeners) {
            listener.onVehicleDecoded(vehicleData);
        }
    }

    /**
     * Handle decoding error
     */
    private void handleDecodingError(String error) {
        Log.e(TAG, "Failed to decode VIN: " + error);
        isDecoding = false;

        // Keep the VIN but clear vehicle data
        currentVehicleData = null;

        // Notify listeners of error
        for (VehicleChangeListener listener : listeners) {
            listener.onDecodingError(error);
        }
    }

    /**
     * Clear current vehicle (called on disconnect)
     */
    public void clearVehicle() {
        Log.d(TAG, "Clearing vehicle data");
        currentVIN = null;
        currentVehicleData = null;
        isDecoding = false;

        // Notify all listeners
        for (VehicleChangeListener listener : listeners) {
            listener.onVehicleDisconnected();
        }
    }

    /**
     * Get current VIN
     */
    public String getCurrentVIN() {
        return currentVIN;
    }

    /**
     * Get current decoded vehicle data
     */
    public VehicleData getCurrentVehicleData() {
        return currentVehicleData;
    }

    /**
     * Check if currently decoding
     */
    public boolean isDecoding() {
        return isDecoding;
    }

    /**
     * Check if vehicle is connected (has VIN)
     */
    public boolean isVehicleConnected() {
        return currentVIN != null;
    }

    /**
     * Get display name for current vehicle
     */
    public String getVehicleDisplayName() {
        if (currentVehicleData != null) {
            return currentVehicleData.getDisplayName();
        } else if (currentVIN != null) {
            return currentVIN;
        }
        return "No Vehicle";
    }

    /**
     * Get manufacturer name (for icon/avatar)
     */
    public String getManufacturer() {
        if (currentVehicleData != null) {
            if (currentVehicleData.make != null && !currentVehicleData.make.isEmpty()) {
                return currentVehicleData.make;
            }
            if (currentVehicleData.manufacturer != null) {
                return currentVehicleData.manufacturer;
            }
        }
        return null;
    }

    /**
     * Get manufacturer initial for avatar
     */
    public String getManufacturerInitial() {
        String manufacturer = getManufacturer();
        if (manufacturer != null && !manufacturer.isEmpty()) {
            return manufacturer.substring(0, 1).toUpperCase();
        }
        return "?";
    }
}