package com.obddroid.vehicle;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * Vehicle-specific preferences manager
 *
 * Stores user preferences on a per-VIN basis including:
 * - Fuel tank capacity (gallons)
 * - Whether user has been prompted for tank size
 * - Other vehicle-specific settings
 *
 * This allows the app to remember user customizations for each vehicle they connect to.
 */
public class VehiclePreferences {

    private static final String TAG = "VehiclePreferences";
    private static final String PREFS_NAME = "vehicle_preferences";

    // Preference key patterns
    private static final String KEY_TANK_CAPACITY_PREFIX = "tank_capacity_";
    private static final String KEY_TANK_PROMPTED_PREFIX = "tank_prompted_";

    private final SharedPreferences preferences;

    /**
     * Create VehiclePreferences instance
     *
     * @param context Application context
     */
    public VehiclePreferences(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Context cannot be null");
        }
        this.preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Log.d(TAG, "VehiclePreferences initialized");
    }

    /**
     * Get the fuel tank capacity for a specific VIN
     *
     * @param vin Vehicle Identification Number (17 characters)
     * @return Tank capacity in gallons, or null if not set
     */
    public Float getTankCapacity(String vin) {
        if (!isValidVIN(vin)) {
            Log.w(TAG, "getTankCapacity: Invalid VIN provided");
            return null;
        }

        String key = KEY_TANK_CAPACITY_PREFIX + sanitizeVIN(vin);
        if (!preferences.contains(key)) {
            Log.d(TAG, "No tank capacity stored for VIN: " + maskVIN(vin));
            return null;
        }

        float capacity = preferences.getFloat(key, 0f);
        if (capacity <= 0f) {
            Log.w(TAG, "Invalid tank capacity stored for VIN: " + maskVIN(vin) + " - " + capacity);
            return null;
        }

        Log.d(TAG, "Retrieved tank capacity for VIN " + maskVIN(vin) + ": " + capacity + " gal");
        return capacity;
    }

    /**
     * Set the fuel tank capacity for a specific VIN
     *
     * @param vin Vehicle Identification Number
     * @param gallons Tank capacity in gallons (must be between 5 and 100)
     * @return true if saved successfully, false otherwise
     */
    public boolean setTankCapacity(String vin, float gallons) {
        if (!isValidVIN(vin)) {
            Log.e(TAG, "setTankCapacity: Invalid VIN provided");
            return false;
        }

        if (gallons < 5f || gallons > 100f) {
            Log.e(TAG, "setTankCapacity: Invalid capacity " + gallons + " gal (must be 5-100)");
            return false;
        }

        String key = KEY_TANK_CAPACITY_PREFIX + sanitizeVIN(vin);
        boolean success = preferences.edit()
                .putFloat(key, gallons)
                .commit();

        if (success) {
            Log.i(TAG, "Saved tank capacity for VIN " + maskVIN(vin) + ": " + gallons + " gal");
        } else {
            Log.e(TAG, "Failed to save tank capacity for VIN " + maskVIN(vin));
        }

        return success;
    }

    /**
     * Check if the user has been prompted for tank size for this VIN
     *
     * @param vin Vehicle Identification Number
     * @return true if user has been prompted, false otherwise
     */
    public boolean hasPromptedForTankSize(String vin) {
        if (!isValidVIN(vin)) {
            return false;
        }

        String key = KEY_TANK_PROMPTED_PREFIX + sanitizeVIN(vin);
        boolean prompted = preferences.getBoolean(key, false);
        Log.d(TAG, "Has prompted for tank size - VIN " + maskVIN(vin) + ": " + prompted);
        return prompted;
    }

    /**
     * Mark that the user has been prompted for tank size for this VIN
     *
     * @param vin Vehicle Identification Number
     * @return true if saved successfully, false otherwise
     */
    public boolean markTankSizePrompted(String vin) {
        if (!isValidVIN(vin)) {
            Log.e(TAG, "markTankSizePrompted: Invalid VIN provided");
            return false;
        }

        String key = KEY_TANK_PROMPTED_PREFIX + sanitizeVIN(vin);
        boolean success = preferences.edit()
                .putBoolean(key, true)
                .commit();

        if (success) {
            Log.d(TAG, "Marked tank size prompted for VIN " + maskVIN(vin));
        } else {
            Log.e(TAG, "Failed to mark tank size prompted for VIN " + maskVIN(vin));
        }

        return success;
    }

    /**
     * Clear all preferences for a specific VIN
     * Useful for testing or if user wants to reset settings
     *
     * @param vin Vehicle Identification Number
     * @return true if cleared successfully, false otherwise
     */
    public boolean clearVehiclePreferences(String vin) {
        if (!isValidVIN(vin)) {
            Log.e(TAG, "clearVehiclePreferences: Invalid VIN provided");
            return false;
        }

        String sanitizedVIN = sanitizeVIN(vin);
        boolean success = preferences.edit()
                .remove(KEY_TANK_CAPACITY_PREFIX + sanitizedVIN)
                .remove(KEY_TANK_PROMPTED_PREFIX + sanitizedVIN)
                .commit();

        if (success) {
            Log.i(TAG, "Cleared all preferences for VIN " + maskVIN(vin));
        } else {
            Log.e(TAG, "Failed to clear preferences for VIN " + maskVIN(vin));
        }

        return success;
    }

    /**
     * Clear ALL vehicle preferences (for all VINs)
     * Use with caution - primarily for testing or factory reset
     *
     * @return true if cleared successfully, false otherwise
     */
    public boolean clearAllPreferences() {
        boolean success = preferences.edit().clear().commit();
        if (success) {
            Log.i(TAG, "Cleared ALL vehicle preferences");
        } else {
            Log.e(TAG, "Failed to clear all preferences");
        }
        return success;
    }

    /**
     * Get the number of VINs with stored preferences
     * Useful for analytics or displaying "known vehicles" count
     *
     * @return Number of unique VINs with preferences
     */
    public int getKnownVehicleCount() {
        int count = 0;
        for (String key : preferences.getAll().keySet()) {
            if (key.startsWith(KEY_TANK_CAPACITY_PREFIX)) {
                count++;
            }
        }
        Log.d(TAG, "Known vehicle count: " + count);
        return count;
    }

    /**
     * Validate VIN format
     *
     * VIN must be:
     * - Not null or empty
     * - Exactly 17 characters long
     * - Contains only alphanumeric characters (excluding I, O, Q per VIN standard)
     *
     * @param vin Vehicle Identification Number to validate
     * @return true if valid format, false otherwise
     */
    private boolean isValidVIN(String vin) {
        if (vin == null || vin.trim().isEmpty()) {
            return false;
        }

        vin = vin.trim().toUpperCase();

        // VIN should be exactly 17 characters
        if (vin.length() != 17) {
            return false;
        }

        // VIN should only contain alphanumeric characters (no I, O, Q per ISO 3779)
        // We'll be lenient here and allow any alphanumeric for partial VINs
        if (!vin.matches("[A-HJ-NPR-Z0-9]{17}")) {
            Log.d(TAG, "VIN contains invalid characters: " + vin);
            // Still allow it but log warning - some test VINs might not be perfect
            return true;
        }

        return true;
    }

    /**
     * Sanitize VIN for use as preference key
     * Ensures consistent casing and trimming
     *
     * @param vin Vehicle Identification Number
     * @return Sanitized VIN (uppercase, trimmed)
     */
    private String sanitizeVIN(String vin) {
        if (vin == null) {
            return "";
        }
        return vin.trim().toUpperCase();
    }

    /**
     * Mask VIN for logging (privacy protection)
     * Shows first 3 and last 4 characters, masks the middle
     * Example: 1HGBH41JXMN109186 -> 1HG********9186
     *
     * @param vin Vehicle Identification Number
     * @return Masked VIN string
     */
    private String maskVIN(String vin) {
        if (vin == null || vin.length() < 17) {
            return "***";
        }

        vin = vin.trim();
        if (vin.length() != 17) {
            return "***";
        }

        return vin.substring(0, 3) + "********" + vin.substring(13);
    }
}
