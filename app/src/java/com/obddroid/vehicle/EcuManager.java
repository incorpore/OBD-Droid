package com.obddroid.vehicle;

import com.obddroid.core.ecu.EcuDataPv;
import com.obddroid.core.obd.ElmProt;
import com.obddroid.core.obd.ObdProt;
import com.obddroid.core.pvs.PvChangeEvent;
import com.obddroid.core.pvs.PvChangeListener;
import com.obddroid.services.CommService;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.logging.Logger;

/**
 * Singleton manager for tracking and discovering ECUs in the vehicle.
 * Listens to Mode 9 data and ECU address discovery to build a comprehensive ECU list.
 */
public class EcuManager {
    private static final Logger log = Logger.getLogger(EcuManager.class.getName());
    private static EcuManager instance;

    private final Map<Integer, EcuInfo> ecuMap = new HashMap<>();
    private final List<EcuManagerListener> listeners = new ArrayList<>();

    // Property change listener for ECU addresses from ElmProt
    private final PropertyChangeListener ecuAddressListener = this::onEcuAddressChange;

    // PV change listener for Mode 9 data
    private final PvChangeListener mode9Listener = this::onMode9DataChange;

    private boolean isListening = false;

    /**
     * Listener interface for ECU discovery updates
     */
    public interface EcuManagerListener {
        void onEcuDiscovered(EcuInfo ecu);
        void onEcuUpdated(EcuInfo ecu);
    }

    private EcuManager() {
        // Private constructor for singleton
    }

    public static synchronized EcuManager getInstance() {
        if (instance == null) {
            instance = new EcuManager();
        }
        return instance;
    }

    /**
     * Start listening for ECU data
     */
    public synchronized void startListening() {
        if (!isListening) {
            log.info("EcuManager: Starting to listen for ECU data");

            // Listen to Mode 9 data changes
            if (ObdProt.VidPvs != null) {
                ObdProt.VidPvs.addPvChangeListener(mode9Listener);
                log.info("EcuManager: Added listener to VidPvs");
            }

            isListening = true;

            // Load any existing Mode 9 data that was already collected
            loadExistingMode9Data();
        }
    }

    /**
     * Load existing Mode 9 data that may have been collected before we started listening
     */
    private void loadExistingMode9Data() {
        try {
            // First, load discovered ECU addresses from ElmProt
            loadEcuAddresses();

            // Then, process Mode 9 data to add names and calibration info
            if (ObdProt.VidPvs != null && !ObdProt.VidPvs.isEmpty()) {
                log.info("EcuManager: Loading existing Mode 9 data, VidPvs size: " + ObdProt.VidPvs.size());

                // Process all existing Mode 9 data
                for (Object key : ObdProt.VidPvs.keySet()) {
                    Object item = ObdProt.VidPvs.get(key);
                    if (item instanceof EcuDataPv) {
                        processMode9Data((EcuDataPv) item);
                    }
                }

                log.info("EcuManager: Finished loading existing Mode 9 data, found " + ecuMap.size() + " ECUs");
            }
        } catch (Exception e) {
            log.warning("EcuManager: Error loading existing Mode 9 data: " + e.getMessage());
        }
    }

    /**
     * Load ECU addresses that were discovered during connection
     */
    private void loadEcuAddresses() {
        try {
            if (CommService.elm != null) {
                // Listen for future ECU address discoveries
                CommService.elm.addPropertyChangeListener(ecuAddressListener);
                log.info("EcuManager: Added property change listener for ECU addresses");

                // Try to load existing ECU addresses using reflection
                try {
                    java.lang.reflect.Field addressField = ElmProt.class.getDeclaredField("ecuAddresses");
                    addressField.setAccessible(true);
                    Object addresses = addressField.get(CommService.elm);

                    if (addresses instanceof TreeSet) {
                        @SuppressWarnings("unchecked")
                        TreeSet<Integer> ecuAddresses = (TreeSet<Integer>) addresses;
                        log.info("EcuManager: Loading " + ecuAddresses.size() + " ECU addresses from ElmProt");

                        for (Integer address : ecuAddresses) {
                            if (!ecuMap.containsKey(address)) {
                                EcuInfo ecu = new EcuInfo(address);
                                ecuMap.put(address, ecu);
                                log.info("EcuManager: Added ECU at address " + ecu.getAddressHex());
                                notifyEcuDiscovered(ecu);
                            }
                        }
                    }
                } catch (Exception reflectionEx) {
                    log.warning("EcuManager: Could not access ECU addresses via reflection: " + reflectionEx.getMessage());
                }
            }
        } catch (Exception e) {
            log.warning("EcuManager: Error loading ECU addresses: " + e.getMessage());
        }
    }

    /**
     * Stop listening for ECU data
     */
    public synchronized void stopListening() {
        if (isListening) {
            log.info("EcuManager: Stopping ECU data listeners");

            if (ObdProt.VidPvs != null) {
                ObdProt.VidPvs.removePvChangeListener(mode9Listener);
            }

            isListening = false;
        }
    }

    /**
     * Clear all discovered ECU data
     */
    public synchronized void clear() {
        log.info("EcuManager: Clearing all ECU data");
        ecuMap.clear();
        notifyListeners();
    }

    /**
     * Handle ECU address discovery from ElmProt
     */
    private void onEcuAddressChange(PropertyChangeEvent evt) {
        if (!ElmProt.PROP_ECU_ADDRESS.equals(evt.getPropertyName())) {
            return;
        }

        Object value = evt.getNewValue();
        if (value instanceof TreeSet) {
            @SuppressWarnings("unchecked")
            TreeSet<Integer> addresses = (TreeSet<Integer>) value;
            log.info("EcuManager: Received ECU addresses: " + addresses);

            synchronized (this) {
                for (Integer address : addresses) {
                    if (!ecuMap.containsKey(address)) {
                        EcuInfo ecu = new EcuInfo(address);
                        ecuMap.put(address, ecu);
                        log.info("EcuManager: Discovered new ECU at address " + ecu.getAddressHex());
                        notifyEcuDiscovered(ecu);
                    }
                }
            }
        }
    }

    /**
     * Handle Mode 9 data changes
     */
    private void onMode9DataChange(PvChangeEvent event) {
        try {
            Object eventValue = event.getValue();

            if (eventValue instanceof EcuDataPv) {
                processMode9Data((EcuDataPv) eventValue);
            } else if (eventValue instanceof Object[]) {
                Object[] arr = (Object[]) eventValue;
                for (Object item : arr) {
                    if (item instanceof EcuDataPv) {
                        processMode9Data((EcuDataPv) item);
                    }
                }
            } else if (event.getSource() instanceof EcuDataPv) {
                processMode9Data((EcuDataPv) event.getSource());
            }
        } catch (Exception e) {
            log.warning("EcuManager: Error processing Mode 9 data: " + e.getMessage());
        }
    }

    /**
     * Process a single Mode 9 data item
     */
    private void processMode9Data(EcuDataPv dataPv) {
        try {
            String description = String.valueOf(dataPv.get(EcuDataPv.FID_DESCRIPT));
            Object value = dataPv.get(EcuDataPv.FID_VALUE);

            if (description == null || value == null) {
                return;
            }

            String descLower = description.toLowerCase();
            String valueStr = value.toString().trim();

            log.fine("EcuManager: Processing Mode 9 data: " + description + " = " + valueStr);

            // ECU Name (PID 0x0A)
            if (descLower.contains("ecu name") || descLower.contains("electronic control")) {
                updateEcuName(valueStr);
            }
            // Calibration ID (PID 0x04)
            else if (descLower.contains("calibration id") || descLower.contains("calib. id")) {
                updateEcuCalibrationId(valueStr);
            }
            // Calibration Verification (PID 0x06)
            else if (descLower.contains("calibration verification") || descLower.contains("cvn")) {
                updateEcuCalibrationVerification(valueStr);
            }
        } catch (Exception e) {
            log.warning("EcuManager: Error processing Mode 9 item: " + e.getMessage());
        }
    }

    /**
     * Sanitize ECU name by removing non-printable characters and cleaning up formatting
     */
    private String sanitizeEcuName(String name) {
        if (name == null) {
            return null;
        }

        // Remove non-printable ASCII characters (keep only 0x20-0x7E)
        StringBuilder cleaned = new StringBuilder();
        for (char c : name.toCharArray()) {
            if (c >= 0x20 && c <= 0x7E) {
                cleaned.append(c);
            }
        }

        // Replace multiple spaces/dashes with a single space
        String result = cleaned.toString()
            .replaceAll("[-\\s]+", " ")
            .trim();

        return result.isEmpty() ? null : result;
    }

    /**
     * Update ECU name - try to match to known ECU or create new one
     */
    private synchronized void updateEcuName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return;
        }

        // Sanitize the ECU name first
        String sanitized = sanitizeEcuName(name);
        if (sanitized == null || sanitized.isEmpty()) {
            log.warning("EcuManager: ECU name sanitization resulted in empty string: " + name);
            return;
        }

        log.info("EcuManager: Found ECU name: " + sanitized);

        // Try to find an ECU without a name and assign it
        for (EcuInfo ecu : ecuMap.values()) {
            if (ecu.getName() == null) {
                ecu.setName(sanitized);
                ecu.incrementResponseCount();
                log.info("EcuManager: Assigned name '" + sanitized + "' to ECU " + ecu.getAddressHex());
                notifyEcuUpdated(ecu);
                return;
            }
        }

        // If no ECU found, create a new one with unknown address
        // Use a placeholder address (0xFF + index)
        int newAddress = 0xFF00 + ecuMap.size();
        EcuInfo ecu = new EcuInfo(newAddress);
        ecu.setName(sanitized);
        ecu.incrementResponseCount();
        ecuMap.put(newAddress, ecu);
        log.info("EcuManager: Created new ECU with name: " + sanitized);
        notifyEcuDiscovered(ecu);
    }

    /**
     * Update ECU calibration ID
     */
    private synchronized void updateEcuCalibrationId(String calId) {
        if (calId == null || calId.trim().isEmpty()) {
            return;
        }

        log.info("EcuManager: Found Calibration ID: " + calId);

        // Assign to first ECU without calibration ID
        for (EcuInfo ecu : ecuMap.values()) {
            if (ecu.getCalibrationId() == null) {
                ecu.setCalibrationId(calId);
                ecu.incrementResponseCount();
                log.info("EcuManager: Assigned Calibration ID to ECU " + ecu.getAddressHex());
                notifyEcuUpdated(ecu);
                return;
            }
        }
    }

    /**
     * Update ECU calibration verification
     */
    private synchronized void updateEcuCalibrationVerification(String cvn) {
        if (cvn == null || cvn.trim().isEmpty()) {
            return;
        }

        log.info("EcuManager: Found Calibration Verification: " + cvn);

        // Assign to first ECU without CVN
        for (EcuInfo ecu : ecuMap.values()) {
            if (ecu.getCalibrationVerification() == null) {
                ecu.setCalibrationVerification(cvn);
                ecu.incrementResponseCount();
                log.info("EcuManager: Assigned CVN to ECU " + ecu.getAddressHex());
                notifyEcuUpdated(ecu);
                return;
            }
        }
    }

    /**
     * Get list of all discovered ECUs
     */
    public synchronized List<EcuInfo> getEcuList() {
        return new ArrayList<>(ecuMap.values());
    }

    /**
     * Get count of discovered ECUs
     */
    public synchronized int getEcuCount() {
        return ecuMap.size();
    }

    /**
     * Add a listener for ECU updates
     */
    public synchronized void addListener(EcuManagerListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     * Remove a listener
     */
    public synchronized void removeListener(EcuManagerListener listener) {
        listeners.remove(listener);
    }

    private void notifyEcuDiscovered(EcuInfo ecu) {
        for (EcuManagerListener listener : listeners) {
            try {
                listener.onEcuDiscovered(ecu);
            } catch (Exception e) {
                log.warning("Error notifying listener: " + e.getMessage());
            }
        }
    }

    private void notifyEcuUpdated(EcuInfo ecu) {
        for (EcuManagerListener listener : listeners) {
            try {
                listener.onEcuUpdated(ecu);
            } catch (Exception e) {
                log.warning("Error notifying listener: " + e.getMessage());
            }
        }
    }

    private void notifyListeners() {
        List<EcuInfo> allEcus = getEcuList();
        for (EcuInfo ecu : allEcus) {
            notifyEcuUpdated(ecu);
        }
    }
}
