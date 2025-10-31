package com.obddroid.services;

/**
 * Priority levels for OBD service requests.
 *
 * Used by ObdServiceCoordinator to determine request ordering when the ELM327
 * adapter is busy. Higher priority requests jump to the front of the queue.
 *
 * PRIORITY RATIONALE:
 * - CRITICAL: Operations that affect safety or must run immediately (clear codes, reset)
 * - CONNECTION: Connection lifecycle operations (connect/disconnect/stop)
 * - HIGH: User-initiated diagnostic operations (fault scan, ECU discovery)
 * - NORMAL: Batch operations like full vehicle scans (sequential stages)
 * - LOW: Background polling that can be deferred (live data, fuel economy)
 */
public enum RequestPriority {
    /**
     * CRITICAL priority - Must run immediately, cannot be deferred.
     *
     * Use cases:
     * - Clear fault codes (user safety - clearing MIL)
     * - Emergency adapter reset
     * - Critical state restoration
     *
     * Timeout: 60 seconds (generous for critical ops)
     */
    CRITICAL(5, 60_000L),

    /**
     * CONNECTION priority - Connection lifecycle operations.
     *
     * Use cases:
     * - Connect/disconnect operations (to prevent hang-ups)
     * - Stop service (to deduplicate multiple calls)
     * - setService() state transitions
     *
     * Timeout: 45 seconds (connection ops need time)
     */
    CONNECTION(4, 45_000L),

    /**
     * HIGH priority - User-initiated diagnostic operations.
     *
     * Use cases:
     * - Fault code scanning (Mode 03/07/0A)
     * - ECU discovery with headers
     * - Freeze frame retrieval
     * - Manual VIN lookup
     *
     * Timeout: 30 seconds (diagnostic operations can be slow)
     */
    HIGH(3, 30_000L),

    /**
     * NORMAL priority - Batch operations and scan stages.
     *
     * Use cases:
     * - Full vehicle scan stages (sequential execution)
     * - Vehicle info retrieval (Mode 09)
     * - Component testing (Mode 08)
     *
     * Timeout: 20 seconds (reasonable for batch operations)
     */
    NORMAL(2, 20_000L),

    /**
     * LOW priority - Background polling and continuous operations.
     *
     * Use cases:
     * - Live data polling (Mode 01 continuous)
     * - Fuel economy monitoring
     * - Emissions monitoring
     * - Dashboard gauges
     *
     * Timeout: 10 seconds (can retry quickly if deferred)
     */
    LOW(1, 10_000L);

    /** Numeric priority value (higher = more important) */
    private final int value;

    /** Maximum time to wait for lock acquisition (milliseconds) */
    private final long timeoutMs;

    /**
     * Constructor
     * @param value Priority value (higher number = higher priority)
     * @param timeoutMs Maximum wait time for lock acquisition
     */
    RequestPriority(int value, long timeoutMs) {
        this.value = value;
        this.timeoutMs = timeoutMs;
    }

    /**
     * Get the numeric priority value
     * @return Priority value (5 = CRITICAL, 4 = CONNECTION, 3 = HIGH, 2 = NORMAL, 1 = LOW)
     */
    public int getValue() {
        return value;
    }

    /**
     * Get the timeout for lock acquisition
     * @return Timeout in milliseconds
     */
    public long getTimeout() {
        return timeoutMs;
    }

    /**
     * Compare this priority to another
     * @param other Other priority to compare
     * @return true if this priority is higher than the other
     */
    public boolean isHigherThan(RequestPriority other) {
        return this.value > other.value;
    }

    @Override
    public String toString() {
        return name() + "(priority=" + value + ", timeout=" + timeoutMs + "ms)";
    }
}
