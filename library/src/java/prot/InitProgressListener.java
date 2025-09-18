package com.obddroid.prot;

/**
 * Listener interface for comprehensive initialization progress
 * Provides detailed callbacks for each phase of vehicle data initialization
 *
 * @author Wal33D
 */
public interface InitProgressListener {

    /**
     * Initialization phases
     */
    enum Phase {
        STARTING("Initializing connection"),
        PID_DISCOVERY("Discovering supported PIDs"),
        PID_DISCOVERY_COMPLETE("PID discovery complete"),
        VEHICLE_INFO("Loading vehicle information"),
        VEHICLE_INFO_COMPLETE("Vehicle info loaded"),
        FAULT_CODES("Reading fault codes"),
        FAULT_CODES_COMPLETE("Fault codes loaded"),
        FREEZE_FRAMES("Loading freeze frame data"),
        FREEZE_FRAMES_COMPLETE("Freeze frames loaded"),
        COMPLETE("Initialization complete"),
        ERROR("Initialization error"),
        CANCELLED("Initialization cancelled");

        private final String description;

        Phase(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Called when entering a new phase
     * @param phase Current phase
     * @param message Optional detailed message
     */
    void onPhaseChanged(Phase phase, String message);

    /**
     * Called to report progress within a phase
     * @param phase Current phase
     * @param current Current item number
     * @param total Total items in phase
     * @param itemDescription Description of current item
     */
    void onProgress(Phase phase, int current, int total, String itemDescription);

    /**
     * Called when data is discovered/loaded
     * @param dataType Type of data (PIDs, DTCs, etc)
     * @param count Number of items discovered
     */
    void onDataLoaded(String dataType, int count);

    /**
     * Called when an error occurs
     * @param phase Phase where error occurred
     * @param error Error message
     * @param recoverable Whether initialization can continue
     */
    void onError(Phase phase, String error, boolean recoverable);

    /**
     * Called when initialization completes
     * @param success Whether initialization was successful
     * @param duration Time taken in milliseconds
     */
    void onComplete(boolean success, long duration);
}