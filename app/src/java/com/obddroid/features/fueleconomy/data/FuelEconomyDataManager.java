package com.obddroid.features.fueleconomy.data;

import java.util.LinkedList;
import java.util.Queue;

/**
 * Manages historical fuel economy data and averaging
 *
 * Tracks three time periods:
 * - Recent (0-5 min): Last 5 data points
 * - Medium (0-30 min): Last 6 averaged data points
 * - Long (0-3 hours): Overall average
 *
 * This class is thread-safe for concurrent updates.
 */
public class FuelEconomyDataManager {

    private static final int RECENT_QUEUE_SIZE = 5;
    private static final int MEDIUM_QUEUE_SIZE = 6;

    // Historical data queues
    private final Queue<Float> recentMpgData = new LinkedList<>();
    private final Queue<Float> mediumMpgData = new LinkedList<>();

    // Long-term tracking
    private float longTermMpgSum = 0f;
    private int dataPointCount = 0;

    /**
     * Add a new MPG data point and update all historical averages
     */
    public synchronized void addDataPoint(float mpg) {
        if (mpg <= 0 || Float.isNaN(mpg) || Float.isInfinite(mpg)) {
            return;  // Skip invalid data
        }

        // Update recent data (0-5 min)
        recentMpgData.offer(mpg);
        if (recentMpgData.size() > RECENT_QUEUE_SIZE) {
            recentMpgData.poll();
        }

        // Update medium data (0-30 min) - add average of recent
        if (recentMpgData.size() == RECENT_QUEUE_SIZE) {
            float recentAvg = calculateAverage(recentMpgData);
            mediumMpgData.offer(recentAvg);
            if (mediumMpgData.size() > MEDIUM_QUEUE_SIZE) {
                mediumMpgData.poll();
            }
        }

        // Update long-term data (0-3 hours)
        longTermMpgSum += mpg;
        dataPointCount++;
    }

    /**
     * Get average MPG for recent period (0-5 min)
     */
    public synchronized float getRecentAverage() {
        return calculateAverage(recentMpgData);
    }

    /**
     * Get average MPG for medium period (0-30 min)
     */
    public synchronized float getMediumAverage() {
        return calculateAverage(mediumMpgData);
    }

    /**
     * Get average MPG for long period (0-3 hours)
     */
    public synchronized float getLongTermAverage() {
        if (dataPointCount == 0) {
            return 0f;
        }
        return longTermMpgSum / dataPointCount;
    }

    /**
     * Get all three averages at once for chart display
     */
    public synchronized HistoricalData getHistoricalData() {
        return new HistoricalData(
            getRecentAverage(),
            getMediumAverage(),
            getLongTermAverage()
        );
    }

    /**
     * Reset all historical data
     */
    public synchronized void reset() {
        recentMpgData.clear();
        mediumMpgData.clear();
        longTermMpgSum = 0f;
        dataPointCount = 0;
    }

    /**
     * Check if we have enough data for display
     */
    public synchronized boolean hasData() {
        return !recentMpgData.isEmpty();
    }

    /**
     * Calculate average from a queue of values
     */
    private float calculateAverage(Queue<Float> queue) {
        if (queue.isEmpty()) {
            return 0f;
        }

        float sum = 0f;
        int count = 0;

        for (Float value : queue) {
            if (value != null && !Float.isNaN(value) && !Float.isInfinite(value)) {
                sum += value;
                count++;
            }
        }

        return count > 0 ? sum / count : 0f;
    }

    /**
     * Data class to hold all three historical averages
     */
    public static class HistoricalData {
        public final float recentAverage;   // 0-5 min
        public final float mediumAverage;   // 0-30 min
        public final float longTermAverage; // 0-3 hours

        public HistoricalData(float recent, float medium, float longTerm) {
            this.recentAverage = recent;
            this.mediumAverage = medium;
            this.longTermAverage = longTerm;
        }
    }
}
