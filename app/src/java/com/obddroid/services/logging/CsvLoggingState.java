package com.obddroid.services.logging;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * In-memory state container for the CSV logging feature.
 * Provides quick access for UI components without needing to bind to the service.
 */
public final class CsvLoggingState {

    private static final AtomicBoolean recording = new AtomicBoolean(false);
    private static final AtomicInteger dataPoints = new AtomicInteger();
    private static final AtomicInteger dataRows = new AtomicInteger();
    private static final AtomicReference<String> lastFileName = new AtomicReference<>();

    private CsvLoggingState() {
        // utility holder
    }

    public static boolean isRecording() {
        return recording.get();
    }

    static void setRecording(boolean active) {
        recording.set(active);
    }

    public static int getDataPoints() {
        return dataPoints.get();
    }

    public static int getDataRows() {
        return dataRows.get();
    }

    static void resetCounters() {
        dataPoints.set(0);
        dataRows.set(0);
    }

    static void incrementDataPoint() {
        dataPoints.incrementAndGet();
    }

    static void incrementDataRow() {
        dataRows.incrementAndGet();
    }

    public static String getLastFileName() {
        return lastFileName.get();
    }

    static void setLastFileName(String fileName) {
        lastFileName.set(fileName);
    }
}
