package com.obddroid.services;

import com.obddroid.core.pvs.PvList;
import com.obddroid.core.pvs.PvChangeEvent;

/**
 * Interface for managing OBD data independently of protocol implementation
 * This provides a layer of abstraction between the UI and the protocol layer
 */
public interface IDataManager {

    /**
     * Called when data is received from the protocol
     * @param service The OBD service (e.g., LIVE_DATA, FREEZE_FRAME, FAULT_CODES)
     * @param pid Parameter ID
     * @param data Raw data bytes
     */
    void onDataReceived(int service, int pid, byte[] data);

    /**
     * Called when the active service changes
     * @param oldService Previous service
     * @param newService New service
     */
    void onServiceChanged(int oldService, int newService);

    /**
     * Clear data for a specific service
     * @param service Service to clear
     */
    void clearService(int service);

    /**
     * Get data for a specific service
     * @param service The OBD service
     * @return PvList containing the data, never null
     */
    PvList getDataForService(int service);

    /**
     * Get freeze frame data for a specific DTC
     * @param dtcIndex The DTC index (frame ID)
     * @return PvList containing freeze frame data
     */
    PvList getFreezeFrameData(int dtcIndex);

    /**
     * Store freeze frame data for a specific DTC
     * @param dtcIndex The DTC index
     * @param data The freeze frame data
     */
    void storeFreezeFrameData(int dtcIndex, PvList data);

    /**
     * Check if data is available for a service
     * @param service The OBD service
     * @return true if data is available
     */
    boolean hasDataForService(int service);

    /**
     * Add a change listener for data updates
     * @param listener The listener to add
     * @param service The service to monitor (or -1 for all)
     */
    void addDataChangeListener(Object listener, int service);

    /**
     * Remove a change listener
     * @param listener The listener to remove
     */
    void removeDataChangeListener(Object listener);
}