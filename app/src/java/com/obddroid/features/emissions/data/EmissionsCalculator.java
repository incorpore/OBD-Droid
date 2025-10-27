package com.obddroid.features.emissions.data;

import java.util.Map;

/**
 * Calculator for OBD-II emissions readiness status
 *
 * Implements EPA standards for emissions test readiness:
 * - For 2001+ vehicles: ≤1 monitor may be "not ready"
 * - Vehicle is ready if at least some monitors are available
 *   AND no more than 1 monitor is not ready
 *
 * This class has no Android dependencies and can be unit tested independently.
 */
public class EmissionsCalculator {

    /**
     * Emissions readiness status
     */
    public static class ReadinessStatus {
        public final boolean isReady;
        public final boolean hasData;
        public final int availableCount;
        public final int readyCount;
        public final int notReadyCount;

        public ReadinessStatus(boolean isReady, boolean hasData, int availableCount,
                               int readyCount, int notReadyCount) {
            this.isReady = isReady;
            this.hasData = hasData;
            this.availableCount = availableCount;
            this.readyCount = readyCount;
            this.notReadyCount = notReadyCount;
        }

        /**
         * Get status description for UI display
         */
        public String getStatusText() {
            if (isReady) {
                return "READY FOR EMISSIONS TEST";
            } else if (!hasData) {
                return "WAITING FOR DATA";
            } else {
                return "NOT READY FOR EMISSIONS TEST";
            }
        }

        /**
         * Get status subtext for UI display
         */
        public String getSubtext() {
            if (isReady) {
                return String.format("%d of %d monitors complete • Passes EPA standards",
                    readyCount, availableCount);
            } else if (!hasData) {
                return "Connect to vehicle to retrieve emissions monitor status";
            } else {
                return String.format("%d of %d monitors complete • %d monitor(s) need drive cycle",
                    readyCount, availableCount, notReadyCount);
            }
        }
    }

    /**
     * Calculate overall emissions test readiness based on EPA standards
     *
     * EPA standard for 2001+ vehicles:
     * - At least some monitors must be available
     * - No more than 1 monitor may be "not ready"
     *
     * @param monitorDataMap Map of monitor keys to MonitorData objects
     * @return ReadinessStatus with calculation results
     */
    public static ReadinessStatus calculateReadiness(Map<String, MonitorData> monitorDataMap) {
        int availableCount = 0;
        int readyCount = 0;

        // Count available and ready monitors
        for (MonitorData monitor : monitorDataMap.values()) {
            if (monitor.isAvailable) {
                availableCount++;
                if (monitor.isReady) {
                    readyCount++;
                }
            }
        }

        int notReadyCount = availableCount - readyCount;

        // Determine overall status
        // EPA standard: For 2001+ vehicles, 1 monitor may be not ready
        // Vehicle is ready if: (1) at least some monitors are available, AND
        //                      (2) no more than 1 monitor is not ready
        boolean hasData = availableCount > 0;
        boolean isReady = hasData && (notReadyCount <= 1);

        return new ReadinessStatus(isReady, hasData, availableCount, readyCount, notReadyCount);
    }

    /**
     * Count number of available monitors
     *
     * @param monitorDataMap Map of monitors
     * @return Count of available monitors
     */
    public static int countAvailableMonitors(Map<String, MonitorData> monitorDataMap) {
        int count = 0;
        for (MonitorData monitor : monitorDataMap.values()) {
            if (monitor.isAvailable) {
                count++;
            }
        }
        return count;
    }

    /**
     * Count number of ready monitors
     *
     * @param monitorDataMap Map of monitors
     * @return Count of ready monitors
     */
    public static int countReadyMonitors(Map<String, MonitorData> monitorDataMap) {
        int count = 0;
        for (MonitorData monitor : monitorDataMap.values()) {
            if (monitor.isReady) {
                count++;
            }
        }
        return count;
    }

    /**
     * Check if vehicle is ready for emissions test (EPA standards)
     *
     * @param monitorDataMap Map of monitors
     * @return true if ready
     */
    public static boolean isEmissionsTestReady(Map<String, MonitorData> monitorDataMap) {
        ReadinessStatus status = calculateReadiness(monitorDataMap);
        return status.isReady;
    }
}
