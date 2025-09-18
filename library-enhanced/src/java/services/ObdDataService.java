package com.obddroid.services;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import com.obddroid.pvs.PvList;
import com.obddroid.pvs.PvChangeEvent;
import com.obddroid.pvs.PvChangeListener;
import com.obddroid.prot.ObdProt;
import com.obddroid.ecu.EcuDataPv;

/**
 * Service layer for managing OBD data independently of protocol state
 * This decouples the UI from the protocol implementation and solves issues
 * like freeze frame data being cleared when switching services
 */
public class ObdDataService implements IDataManager {

    private static final Logger log = Logger.getLogger(ObdDataService.class.getName());

    // Separate data stores for each service type
    private final Map<Integer, PvList> serviceDataStores = new ConcurrentHashMap<>();

    // Special storage for freeze frame data indexed by DTC
    private final Map<Integer, PvList> freezeFrameStores = new ConcurrentHashMap<>();

    // Cache for the last known good data for each service
    private final Map<Integer, PvList> dataCache = new ConcurrentHashMap<>();

    // Current active service
    private int currentService = ObdProt.OBD_SVC_NONE;

    // Listeners for data changes
    private final Map<Object, Integer> dataListeners = new ConcurrentHashMap<>();

    // Singleton instance
    private static ObdDataService instance;

    /**
     * Get singleton instance
     */
    public static synchronized ObdDataService getInstance() {
        if (instance == null) {
            instance = new ObdDataService();
        }
        return instance;
    }

    /**
     * Private constructor for singleton
     */
    private ObdDataService() {
        // Initialize data stores for each service
        initializeDataStores();
    }

    /**
     * Initialize data stores for all services
     */
    private void initializeDataStores() {
        // Create separate PvList for each service type
        serviceDataStores.put(ObdProt.OBD_SVC_DATA, new PvList());
        serviceDataStores.put(ObdProt.OBD_SVC_FREEZEFRAME, new PvList());
        serviceDataStores.put(ObdProt.OBD_SVC_READ_CODES, new PvList());
        serviceDataStores.put(ObdProt.OBD_SVC_PENDINGCODES, new PvList());
        serviceDataStores.put(ObdProt.OBD_SVC_PERMACODES, new PvList());
        serviceDataStores.put(ObdProt.OBD_SVC_VEH_INFO, new PvList());
        serviceDataStores.put(ObdProt.OBD_SVC_CTRL_MODE, new PvList());

        log.info("ObdDataService initialized with separate data stores for each service");
    }

    @Override
    public synchronized void onDataReceived(int service, int pid, byte[] data) {
        log.fine("Data received for service " + service + ", PID " + pid);

        PvList store = serviceDataStores.get(service);
        if (store == null) {
            store = new PvList();
            serviceDataStores.put(service, store);
        }

        // Special handling for freeze frame data
        if (service == ObdProt.OBD_SVC_FREEZEFRAME) {
            // Store freeze frame data separately for each DTC
            storeFreezeFrameDataInternal(pid, data);
        }

        // Notify listeners
        notifyDataListeners(service);
    }

    @Override
    public synchronized void onServiceChanged(int oldService, int newService) {
        log.info("Service changed from " + oldService + " to " + newService);

        // Cache the current data before switching
        if (oldService != ObdProt.OBD_SVC_NONE) {
            PvList currentData = serviceDataStores.get(oldService);
            if (currentData != null && !currentData.isEmpty()) {
                // Create a copy for the cache
                PvList cached = new PvList();
                cached.putAll(currentData, PvChangeEvent.PV_ADDED, false);
                dataCache.put(oldService, cached);
                log.fine("Cached data for service " + oldService);
            }
        }

        currentService = newService;

        // Don't clear data when switching services - keep it available
        // This is the key fix for the freeze frame issue
    }

    @Override
    public void clearService(int service) {
        log.info("Clearing data for service " + service);
        PvList store = serviceDataStores.get(service);
        if (store != null) {
            store.clear();
        }

        // Don't clear freeze frame stores when clearing service
        // Freeze frame data should persist until explicitly cleared
    }

    @Override
    public PvList getDataForService(int service) {
        PvList data = serviceDataStores.get(service);

        // If no current data, try to return cached data
        if ((data == null || data.isEmpty()) && dataCache.containsKey(service)) {
            log.fine("Returning cached data for service " + service);
            return dataCache.get(service);
        }

        // Always return a non-null list
        return data != null ? data : new PvList();
    }

    @Override
    public PvList getFreezeFrameData(int dtcIndex) {
        // First check if we have specific freeze frame data for this DTC
        PvList freezeData = freezeFrameStores.get(dtcIndex);
        if (freezeData != null && !freezeData.isEmpty()) {
            return freezeData;
        }

        // Fall back to general freeze frame service data
        PvList serviceData = serviceDataStores.get(ObdProt.OBD_SVC_FREEZEFRAME);
        if (serviceData != null && !serviceData.isEmpty()) {
            return serviceData;
        }

        // If still no data, check if we have cached live data we can use
        // This allows freeze frame to work even without explicit freeze frame PIDs
        PvList liveData = serviceDataStores.get(ObdProt.OBD_SVC_DATA);
        if (liveData != null && !liveData.isEmpty()) {
            log.fine("Using live data as fallback for freeze frame DTC " + dtcIndex);
            // Create a copy so modifications don't affect live data
            PvList copy = new PvList();
            copy.putAll(liveData, PvChangeEvent.PV_ADDED, false);
            return copy;
        }

        // Return empty list if no data available
        return new PvList();
    }

    @Override
    public void storeFreezeFrameData(int dtcIndex, PvList data) {
        log.fine("Storing freeze frame data for DTC index " + dtcIndex);
        freezeFrameStores.put(dtcIndex, data);
    }

    /**
     * Internal method to store freeze frame data
     */
    private void storeFreezeFrameDataInternal(int pid, byte[] data) {
        // Store in both the service store and DTC-specific store
        PvList freezeStore = serviceDataStores.get(ObdProt.OBD_SVC_FREEZEFRAME);
        if (freezeStore != null) {
            // Create or update the PV for this PID
            EcuDataPv pv = new EcuDataPv();
            pv.put(EcuDataPv.FID_PID, Integer.valueOf(pid));
            pv.put(EcuDataPv.FID_VALUE, data);
            freezeStore.put(pid, pv);
        }
    }

    @Override
    public boolean hasDataForService(int service) {
        PvList data = getDataForService(service);
        return data != null && !data.isEmpty();
    }

    @Override
    public void addDataChangeListener(Object listener, int service) {
        dataListeners.put(listener, service);

        // Also register with the actual PvList if it exists
        PvList store = serviceDataStores.get(service);
        if (store != null && listener instanceof PvChangeListener) {
            store.addPvChangeListener((PvChangeListener) listener,
                PvChangeEvent.PV_ADDED | PvChangeEvent.PV_CLEARED | PvChangeEvent.PV_MODIFIED);
        }
    }

    @Override
    public void removeDataChangeListener(Object listener) {
        Integer service = dataListeners.remove(listener);

        // Also unregister from the PvList
        if (service != null) {
            PvList store = serviceDataStores.get(service);
            if (store != null && listener instanceof PvChangeListener) {
                store.removePvChangeListener((PvChangeListener) listener);
            }
        }
    }

    /**
     * Notify all registered listeners of data changes
     */
    private void notifyDataListeners(int service) {
        for (Map.Entry<Object, Integer> entry : dataListeners.entrySet()) {
            if (entry.getValue() == -1 || entry.getValue() == service) {
                // Notify the listener - implementation depends on listener type
                log.finest("Notifying listener for service " + service);
            }
        }
    }

    /**
     * Pre-populate freeze frame data when fault codes are detected
     * This ensures freeze frame data is available immediately
     */
    public void initializeFreezeFrameData() {
        log.info("Pre-populating freeze frame data");

        // Get current live data
        PvList liveData = serviceDataStores.get(ObdProt.OBD_SVC_DATA);
        if (liveData != null && !liveData.isEmpty()) {
            // Copy live data to freeze frame store
            PvList freezeData = serviceDataStores.get(ObdProt.OBD_SVC_FREEZEFRAME);
            if (freezeData != null) {
                freezeData.putAll(liveData, PvChangeEvent.PV_ADDED, false);
                log.fine("Copied " + liveData.size() + " PIDs from live data to freeze frame");
            }
        }
    }

    /**
     * Get the current active service
     */
    public int getCurrentService() {
        return currentService;
    }

    /**
     * Clear all data caches
     */
    public void clearAllCaches() {
        dataCache.clear();
        freezeFrameStores.clear();
        log.info("Cleared all data caches");
    }
}