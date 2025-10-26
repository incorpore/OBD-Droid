package com.obddroid.features.emissions.data;

/**
 * Data structure to hold OBD-II emissions monitor information
 *
 * Contains both readiness status (Mode 1 PID 0x01) and performance data (Mode 9 PID 0x08)
 * for a single emissions monitor.
 */
public class MonitorData {

    public String name;
    public boolean isReady;
    public boolean isAvailable;
    public int conditions;      // IUMPR: Number of times conditions were met
    public int completions;     // IUMPR: Number of times monitor completed

    public MonitorData(String name) {
        this.name = name;
        this.isReady = false;
        this.isAvailable = false;  // Default to NOT available - only mark available when we find data
        this.conditions = 0;
        this.completions = 0;
    }

    /**
     * Calculate IUMPR (In-Use Monitor Performance Ratio)
     *
     * @return Ratio of completions to conditions (0.0 to 1.0+)
     */
    public float getRatio() {
        if (conditions == 0) return 0f;
        return (float) completions / conditions;
    }

    /**
     * Get IUMPR as a percentage
     *
     * @return Percentage (0 to 100+)
     */
    public int getPercentage() {
        return Math.round(getRatio() * 100);
    }

    /**
     * Get formatted percentage display string
     *
     * @return Percentage string with "%" suffix, capped at "100%+"
     */
    public String getPercentageDisplay() {
        int percentage = getPercentage();
        if (percentage > 100) {
            return "100%+";
        }
        return String.valueOf(percentage) + "%";
    }

    /**
     * Get IUMPR quality indicator for regulatory compliance
     * NOTE: This is DIFFERENT from emissions readiness!
     * - Emissions Ready: Needs ≥1 completion
     * - IUMPR Quality: Measures how frequently the monitor runs
     *
     * @return Quality indicator string or null if no completions
     */
    public String getIUMPRQuality() {
        if (completions == 0) {
            return null;  // No quality indicator if never completed
        }

        float ratio = getRatio();

        // EVAP has stricter CARB requirement (52%)
        if (name.contains("EVAP")) {
            if (ratio >= 0.52f) return "🟢 Excellent";  // Meets CARB 52% minimum
            if (ratio >= 0.10f) return "🟡 Good";       // Meets basic 10% minimum
            return "🟠 Low Frequency";                    // Below minimums but still ready
        }

        // Other monitors use 10% threshold
        if (ratio >= 0.10f) return "🟢 Excellent";      // Meets minimum
        return "🟠 Low Frequency";                        // Below minimum but still ready
    }

    /**
     * Get explanation of IUMPR quality (for tooltip/help)
     *
     * @return Explanation string or null
     */
    public String getIUMPRQualityExplanation() {
        if (completions == 0) return null;

        String quality = getIUMPRQuality();
        if (quality == null) return null;

        if (quality.contains("Excellent")) {
            return "Monitor runs frequently - exceeds regulatory minimums";
        } else if (quality.contains("Good")) {
            return "Monitor runs regularly - meets regulatory minimums";
        } else {
            return "Monitor runs infrequently but has completed successfully";
        }
    }
}
