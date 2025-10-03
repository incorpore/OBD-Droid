package com.obddroid.vehicle;

import android.util.Log;
import io.github.vindecoder.nhtsa.VINDecoderService;
import io.github.vindecoder.nhtsa.VehicleData;
import io.github.vindecoder.offline.OfflineVINDecoder;
import com.obddroid.core.obd.ElmProt;
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
    private ElmProt.STAT ecuConnectionState = ElmProt.STAT.UNDEFINED;
    private boolean ecuSelected = false;
    private boolean vinRetrievalAttempted = false;
    private boolean vinRetrievalFailed = false;

    // Listeners for vehicle changes
    private final List<VehicleChangeListener> listeners = new ArrayList<>();

    // VIN decoder services
    private final VINDecoderService vinDecoder;
    private final OfflineVINDecoder offlineDecoder;

    /**
     * Listener interface for vehicle changes
     */
    public interface VehicleChangeListener {
        void onVINChanged(String vin);
        void onVehicleDecoded(VehicleData vehicleData);
        void onVehicleDisconnected();
        void onECUConnectionChanged(ElmProt.STAT state);
        void onVINRetrievalFailed();
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
        @Override public void onECUConnectionChanged(ElmProt.STAT state) {}
        @Override public void onVINRetrievalFailed() {}
        @Override public void onDecodingStarted() {}
        @Override public void onDecodingError(String error) {}
    }

    private VehicleManager() {
        vinDecoder = VINDecoderService.getInstance();
        offlineDecoder = new OfflineVINDecoder();
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
            // VIN retrieval failed (Mode 9 not supported)
            vinRetrievalAttempted = true;
            vinRetrievalFailed = true;

            // Notify listeners of failure
            for (VehicleChangeListener listener : listeners) {
                listener.onVINRetrievalFailed();
            }
            return;
        }

        vin = vin.trim().toUpperCase();
        vinRetrievalAttempted = true;
        vinRetrievalFailed = false;

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

        // For demo VIN or no internet, use offline decoder directly
        if (vin.equals("4JGDA5HB7JB158144")) {
            Log.d(TAG, "Demo VIN detected - using offline decoder for: " + vin);
            try {
                VehicleData offlineData = offlineDecoder.decode(vin);
                offlineData.setMessage("Decoded using offline VIN database");
                Log.d(TAG, "Offline decode successful: " + offlineData.getDisplayName());
                handleDecodedVehicle(offlineData);
            } catch (Exception e) {
                Log.e(TAG, "Offline decode failed: " + e.getMessage());
                // Fall back to hardcoded demo data if offline fails
                VehicleData demoData = createDemoVehicleData(vin);
                handleDecodedVehicle(demoData);
            }
            return;
        }

        // Start async decoding for real VINs
        Log.d(TAG, "Starting VIN decode - trying online API first for: " + vin);
        vinDecoder.decodeVIN(vin, new VINDecoderService.VINDecoderCallback() {
            @Override
            public void onSuccess(VehicleData vehicleData) {
                Log.d(TAG, "Online API decode successful");
                handleDecodedVehicle(vehicleData);
            }

            @Override
            public void onError(String error) {
                Log.d(TAG, "Online API decode failed: " + error);
                handleDecodingError(error);
            }
        });
    }

    /**
     * Create demo VehicleData for testing
     */
    private VehicleData createDemoVehicleData(String vin) {
        VehicleData data = new VehicleData();

        // Core info
        data.vin = vin;
        data.make = "MERCEDES-BENZ";
        data.manufacturer = "Mercedes-Benz (Daimler AG)";
        data.model = "GLE-Class";
        data.modelYear = "2018";

        // Body and Structure
        data.bodyClass = "Sport Utility Vehicle (SUV)";
        data.doors = "4";
        data.vehicleType = "Multipurpose Passenger Vehicle (MPV)";
        data.wheelBase = "114.8";

        // Engine Information
        data.engineCylinders = "6";
        data.displacementCC = "3498";
        data.displacementCI = "213.5";
        data.displacementL = "3.50";
        data.engineModel = "M276 DE35";
        data.engineManufacturer = "Mercedes-Benz";
        data.fuelTypePrimary = "Gasoline";

        // Drivetrain
        data.driveType = "All Wheel Drive (AWD)";
        data.transmissionStyle = "Automatic";
        data.transmissionSpeeds = "9";

        // Manufacturing
        data.plantCity = "Tuscaloosa";
        data.plantState = "Alabama";
        data.plantCountry = "United States";

        // Weight
        data.gvwr = "6062";
        data.curbWeight = "4630";

        // Trim/Series
        data.series = "GLE 350";
        data.trim = "4MATIC";

        // Safety (some examples)
        data.abs = "Standard";
        data.airBagLocFront = "1st Row (Driver and Passenger)";
        data.airBagLocSide = "1st and 2nd Rows";
        data.airBagLocCurtain = "All Rows";

        // Set as valid
        data.errorCode = "0";
        data.errorText = "";

        Log.d(TAG, "Created demo vehicle data for Mercedes GLE");
        return data;
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
     * Handle decoding error - try offline decoder as fallback
     */
    private void handleDecodingError(String error) {
        Log.e(TAG, "Failed to decode VIN online: " + error);
        Log.d(TAG, "Attempting offline VIN decode for: " + currentVIN);

        // Try offline decoding as fallback
        try {
            VehicleData offlineData = offlineDecoder.decode(currentVIN);

            if (offlineData != null && offlineData.getMake() != null) {
                // Add note that this was decoded offline
                offlineData.setMessage("Decoded offline (no internet)");
                Log.d(TAG, "Successfully decoded VIN offline: " + offlineData.getDisplayName());

                // Handle as successful decode
                handleDecodedVehicle(offlineData);
                return;
            } else {
                Log.e(TAG, "Offline decode failed or returned incomplete data");
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception during offline decode: " + e.getMessage());
        }

        // If offline also failed, report the error
        isDecoding = false;
        currentVehicleData = null;

        // Notify listeners of error
        for (VehicleChangeListener listener : listeners) {
            listener.onDecodingError("Unable to decode VIN: " + error);
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
        ecuSelected = false;
        vinRetrievalAttempted = false;
        vinRetrievalFailed = false;
        // Don't reset ECU state here - it's managed separately

        // Notify all listeners
        for (VehicleChangeListener listener : listeners) {
            listener.onVehicleDisconnected();
        }
    }

    /**
     * Handle VIN retrieval timeout
     */
    public void handleVINTimeout() {
        Log.d(TAG, "VIN retrieval timed out");
        vinRetrievalAttempted = true;
        // DON'T mark as permanently failed - allow manual retry via vehicle info page
        // vinRetrievalFailed = true;
        isDecoding = false;

        // Notify listeners that decoding failed
        for (VehicleChangeListener listener : listeners) {
            listener.onDecodingError("VIN retrieval timed out - you can manually view vehicle info");
        }
    }

    /**
     * Update ECU connection state
     */
    public void setECUConnectionState(ElmProt.STAT state) {
        Log.d(TAG, "ECU connection state changed: " + state);
        ElmProt.STAT oldState = ecuConnectionState;
        ecuConnectionState = state;

        // Don't notify about ECU connection until actually selected
        // ECU_DETECTED means ECUs are found but not selected yet
        if (state != ElmProt.STAT.ECU_DETECTED) {
            // Notify all listeners
            for (VehicleChangeListener listener : listeners) {
                listener.onECUConnectionChanged(state);
            }
        }

        // If ECU disconnected or no longer detected, clear selection state
        if (state == ElmProt.STAT.UNDEFINED ||
            state == ElmProt.STAT.DISCONNECTED ||
            state == ElmProt.STAT.STOPPED ||
            state == ElmProt.STAT.NODATA) {
            ecuSelected = false;
            vinRetrievalAttempted = false;
            vinRetrievalFailed = false;
        }

        // If going from connected/initialized back to initializing, we're reconnecting
        if ((oldState == ElmProt.STAT.CONNECTED || oldState == ElmProt.STAT.INITIALIZED) &&
            state == ElmProt.STAT.INITIALIZING) {
            // Clear vehicle data for new connection
            clearVehicle();
        }
    }

    /**
     * Get current ECU connection state
     */
    public ElmProt.STAT getECUConnectionState() {
        return ecuConnectionState;
    }

    /**
     * Mark that ECU has been selected by user
     */
    public void setECUSelected(boolean selected) {
        Log.d(TAG, "ECU selected: " + selected);
        ecuSelected = selected;
        vinRetrievalAttempted = false;
        vinRetrievalFailed = false;

        if (selected) {
            // Now notify listeners that ECU is truly connected
            // But don't assume Mode 9 failure yet
            for (VehicleChangeListener listener : listeners) {
                listener.onECUConnectionChanged(ElmProt.STAT.CONNECTED);
            }
        }
    }

    /**
     * Check if VIN retrieval has been attempted and failed
     */
    public boolean hasVINRetrievalFailed() {
        return vinRetrievalAttempted && vinRetrievalFailed;
    }

    /**
     * Check if ECU is connected (even without VIN)
     */
    public boolean isECUConnected() {
        // Only consider connected if ECU is actually selected
        return ecuSelected && (ecuConnectionState == ElmProt.STAT.ECU_DETECTED ||
                               ecuConnectionState == ElmProt.STAT.CONNECTED);
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

    /**
     * Validate a VIN format (without decoding)
     */
    public boolean validateVIN(String vin) {
        return offlineDecoder.validate(vin);
    }

    /**
     * Force offline decoding (useful when no internet)
     */
    public void decodeVINOffline(String vin) {
        if (vin == null || vin.trim().isEmpty()) {
            return;
        }

        vin = vin.trim().toUpperCase();

        // Check if VIN actually changed
        if (vin.equals(currentVIN) && currentVehicleData != null) {
            Log.d(TAG, "VIN unchanged and already decoded: " + vin);
            return;
        }

        Log.d(TAG, "Forcing offline VIN decode: " + vin);
        currentVIN = vin;
        isDecoding = true;

        // Notify listeners of VIN change
        for (VehicleChangeListener listener : listeners) {
            listener.onVINChanged(vin);
            listener.onDecodingStarted();
        }

        try {
            VehicleData offlineData = offlineDecoder.decode(vin);
            offlineData.setMessage("Decoded offline");
            handleDecodedVehicle(offlineData);
        } catch (Exception e) {
            handleDecodingError("Offline decode failed: " + e.getMessage());
        }
    }

    /**
     * Get quick manufacturer info without full decode
     */
    public String getQuickManufacturer(String vin) {
        return offlineDecoder.getManufacturer(vin);
    }
}