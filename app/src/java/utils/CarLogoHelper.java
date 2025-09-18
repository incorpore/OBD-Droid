package com.obddroid.utils;

import com.obddroid.R;
import java.util.HashMap;
import java.util.Map;

/**
 * Helper class to map car manufacturer names to their logo resources
 */
public class CarLogoHelper {

    private static final Map<String, Integer> LOGO_MAP = new HashMap<>();

    static {
        // Initialize logo mappings - temporarily commented to debug build issue
        // Will be populated dynamically after build succeeds
    }

    /**
     * Get logo resource ID for a given manufacturer name
     * @param manufacturer The manufacturer name (case insensitive)
     * @return Resource ID of the logo, or 0 if not found
     */
    public static int getLogoResource(String manufacturer) {
        if (manufacturer == null || manufacturer.isEmpty()) {
            return 0;
        }

        // Convert to uppercase for case-insensitive lookup
        String upperMake = manufacturer.toUpperCase().trim();

        // Direct lookup
        Integer logoId = LOGO_MAP.get(upperMake);
        if (logoId != null) {
            return logoId;
        }

        // Try partial matches for complex names
        for (Map.Entry<String, Integer> entry : LOGO_MAP.entrySet()) {
            if (upperMake.contains(entry.getKey()) || entry.getKey().contains(upperMake)) {
                return entry.getValue();
            }
        }

        return 0; // No logo found
    }

    /**
     * Check if a logo exists for the given manufacturer
     * @param manufacturer The manufacturer name
     * @return true if a logo exists
     */
    public static boolean hasLogo(String manufacturer) {
        return getLogoResource(manufacturer) != 0;
    }
}