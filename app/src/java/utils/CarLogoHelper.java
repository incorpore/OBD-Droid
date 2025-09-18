package com.obddroid.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Helper class to map car manufacturer names to their logo assets
 */
public class CarLogoHelper {

    private static final Map<String, String> LOGO_MAP = new HashMap<>();

    static {
        // Initialize logo filename mappings
        LOGO_MAP.put("MERCEDES-BENZ", "logo_mercedes_benz.png");
        LOGO_MAP.put("MERCEDES BENZ", "logo_mercedes_benz.png");
        LOGO_MAP.put("MERCEDES", "logo_mercedes_benz.png");
        LOGO_MAP.put("BMW", "logo_bmw.png");
        LOGO_MAP.put("AUDI", "logo_audi.png");
        LOGO_MAP.put("TOYOTA", "logo_toyota.png");
        LOGO_MAP.put("HONDA", "logo_honda.png");
        LOGO_MAP.put("FORD", "logo_ford.png");
        LOGO_MAP.put("CHEVROLET", "logo_chevrolet.png");
        LOGO_MAP.put("CHEVY", "logo_chevrolet.png");
        LOGO_MAP.put("VOLKSWAGEN", "logo_volkswagen.png");
        LOGO_MAP.put("VW", "logo_volkswagen.png");
        LOGO_MAP.put("NISSAN", "logo_nissan.png");
        LOGO_MAP.put("HYUNDAI", "logo_hyundai.png");
        LOGO_MAP.put("MAZDA", "logo_mazda.png");
        LOGO_MAP.put("SUBARU", "logo_subaru.png");
        LOGO_MAP.put("PORSCHE", "logo_porsche.png");
        LOGO_MAP.put("TESLA", "logo_tesla.png");
        LOGO_MAP.put("LEXUS", "logo_lexus.png");
        LOGO_MAP.put("ACURA", "logo_acura.png");
        LOGO_MAP.put("INFINITI", "logo_infiniti.png");
        LOGO_MAP.put("JAGUAR", "logo_jaguar.png");
        LOGO_MAP.put("LAND ROVER", "logo_land_rover.png");
        LOGO_MAP.put("LAND-ROVER", "logo_land_rover.png");
        LOGO_MAP.put("VOLVO", "logo_volvo.png");
        LOGO_MAP.put("MITSUBISHI", "logo_mitsubishi.png");
        LOGO_MAP.put("KIA", "logo_kia.png");
        LOGO_MAP.put("GENESIS", "logo_genesis.png");
        LOGO_MAP.put("GMC", "logo_gmc.png");
        LOGO_MAP.put("JEEP", "logo_jeep.png");
        LOGO_MAP.put("RAM", "logo_ram.png");
        LOGO_MAP.put("DODGE", "logo_dodge.png");
        LOGO_MAP.put("CHRYSLER", "logo_chrysler.png");
        LOGO_MAP.put("BUICK", "logo_buick.png");
        LOGO_MAP.put("CADILLAC", "logo_cadillac.png");
        LOGO_MAP.put("LINCOLN", "logo_lincoln.png");
        LOGO_MAP.put("MINI", "logo_mini.png");
        LOGO_MAP.put("FIAT", "logo_fiat.png");
        LOGO_MAP.put("ALFA ROMEO", "logo_alfa_romeo.png");
        LOGO_MAP.put("MASERATI", "logo_maserati.png");
        LOGO_MAP.put("FERRARI", "logo_ferrari.png");
        LOGO_MAP.put("LAMBORGHINI", "logo_lamborghini.png");
        LOGO_MAP.put("BENTLEY", "logo_bentley.png");
        LOGO_MAP.put("ROLLS ROYCE", "logo_rolls_royce.png");
        LOGO_MAP.put("ROLLS-ROYCE", "logo_rolls_royce.png");
        LOGO_MAP.put("ASTON MARTIN", "logo_aston_martin.png");
        LOGO_MAP.put("PEUGEOT", "logo_peugeot.png");
        LOGO_MAP.put("RENAULT", "logo_renault.png");
        LOGO_MAP.put("CITROEN", "logo_citroen.png");
        LOGO_MAP.put("SEAT", "logo_seat.png");
        LOGO_MAP.put("SKODA", "logo_skoda.png");
        LOGO_MAP.put("OPEL", "logo_opel.png");
    }

    /**
     * Get logo bitmap for a given manufacturer name
     * @param context Context to access assets
     * @param manufacturer The manufacturer name (case insensitive)
     * @return Bitmap of the logo, or null if not found
     */
    public static Bitmap getLogoBitmap(Context context, String manufacturer) {
        if (manufacturer == null || manufacturer.isEmpty()) {
            return null;
        }

        // Convert to uppercase for case-insensitive lookup
        String upperMake = manufacturer.toUpperCase().trim();

        // Direct lookup
        String logoFile = LOGO_MAP.get(upperMake);

        // Try partial matches if no direct match
        if (logoFile == null) {
            for (Map.Entry<String, String> entry : LOGO_MAP.entrySet()) {
                if (upperMake.contains(entry.getKey()) || entry.getKey().contains(upperMake)) {
                    logoFile = entry.getValue();
                    break;
                }
            }
        }

        if (logoFile == null) {
            return null;
        }

        // Load bitmap from assets
        try {
            InputStream is = context.getAssets().open("car_logos/" + logoFile);
            Bitmap bitmap = BitmapFactory.decodeStream(is);
            is.close();
            return bitmap;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Check if a logo exists for the given manufacturer
     * @param manufacturer The manufacturer name
     * @return true if a logo exists
     */
    public static boolean hasLogo(String manufacturer) {
        if (manufacturer == null || manufacturer.isEmpty()) {
            return false;
        }

        String upperMake = manufacturer.toUpperCase().trim();

        // Check direct match
        if (LOGO_MAP.containsKey(upperMake)) {
            return true;
        }

        // Check partial matches
        for (String key : LOGO_MAP.keySet()) {
            if (upperMake.contains(key) || key.contains(upperMake)) {
                return true;
            }
        }

        return false;
    }
}