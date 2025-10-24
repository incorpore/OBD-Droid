package com.obddroid.scan;

/**
 * Represents a single stage in the full vehicle scan orchestration.
 * Each stage performs a specific diagnostic operation (e.g., read DTCs, capture live data).
 */
public interface ScanStage {

    /**
     * Unique identifier for this stage (e.g., "MODE_01_LIVE_DATA")
     */
    String getId();

    /**
     * Human-readable name for progress display (e.g., "Capturing Live Data")
     */
    String getDisplayName();

    /**
     * Estimated duration in seconds (for progress calculation)
     */
    int getEstimatedDurationSeconds();

    /**
     * Execute this stage and return the result.
     *
     * @param context The scan execution context with shared state and services
     * @return Result containing status, data, and any errors
     * @throws InterruptedException if the scan is cancelled
     */
    StageResult execute(ScanContext context) throws InterruptedException;

    /**
     * Whether this stage should be skipped based on current context
     * (e.g., skip Mode 02 if no DTCs present)
     */
    boolean shouldSkip(ScanContext context);
}
