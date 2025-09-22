package com.obddroid.database;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manufacturer-aware DTC database handler
 * Provides intelligent DTC lookups based on vehicle manufacturer
 *
 * Author: Wal33D
 */
public class DTCDatabase extends SQLiteOpenHelper {
    private static final String TAG = "DTCDatabase";
    private static final String DB_NAME = "dtc_perfect.db";
    private static final int DB_VERSION = 1;

    private final Context context;
    private SQLiteDatabase database;

    // Comprehensive WMI to Manufacturer mapping - 100% DTC database coverage
    private static final Map<String, String> WMI_MAP = new HashMap<String, String>() {{
        // US - Ford
        put("1FA", "FORD"); put("1FB", "FORD"); put("1FC", "FORD"); put("1FD", "FORD");
        put("1FM", "FORD"); put("1FT", "FORD"); put("1FU", "FORD"); put("1FV", "FORD");
        put("1ZV", "FORD");

        // US - General Motors
        put("1G1", "CHEVY"); put("1G2", "PONTIAC"); put("1G3", "OLDSMOBILE");
        put("1G4", "BUICK"); put("1G6", "CADILLAC"); put("1G8", "SATURN");
        put("1GC", "CHEVY"); put("1GM", "PONTIAC"); put("1GT", "GMC");
        put("1GE", "CADILLAC"); put("1GY", "CADILLAC"); put("1G9", "GEO");

        // US - Chrysler Group
        put("1C3", "CHRYSLER"); put("1C4", "CHRYSLER"); put("1C6", "CHRYSLER");
        put("1D3", "DODGE"); put("1D4", "DODGE"); put("1D7", "DODGE"); put("1D8", "DODGE");
        put("1J4", "JEEP"); put("1J8", "JEEP");
        put("1P3", "PLYMOUTH"); put("1P4", "PLYMOUTH"); put("1P7", "PLYMOUTH");

        // US - Other
        put("1HG", "HONDA"); put("1LN", "LINCOLN"); put("1ME", "MERCURY");
        put("1N4", "NISSAN"); put("1N6", "NISSAN"); put("1VW", "VOLKSWAGEN");
        put("1YV", "MAZDA");

        // Canadian
        put("2FA", "FORD"); put("2FB", "FORD"); put("2FC", "FORD"); put("2FM", "FORD");
        put("2FT", "FORD"); put("2FU", "FORD"); put("2FV", "FORD"); put("2FZ", "FORD");
        put("2G1", "CHEVY"); put("2G2", "PONTIAC"); put("2G3", "OLDSMOBILE");
        put("2G4", "BUICK"); put("2G5", "GMC"); put("2G6", "CADILLAC"); put("2G9", "GEO");
        put("2HG", "HONDA"); put("2HK", "HONDA"); put("2HM", "HONDA");
        put("2LM", "LINCOLN"); put("2ME", "MERCURY");
        put("2C3", "CHRYSLER"); put("2C4", "CHRYSLER"); put("2D3", "DODGE");
        put("2P3", "PLYMOUTH"); put("2P4", "PLYMOUTH");

        // Mexican
        put("3FA", "FORD"); put("3FE", "FORD"); put("3G1", "CHEVY");
        put("3G2", "PONTIAC"); put("3G4", "BUICK"); put("3G5", "BUICK");
        put("3G6", "CADILLAC"); put("3G7", "GMC"); put("3GC", "CHEVY");
        put("3VW", "VOLKSWAGEN"); put("3C4", "CHRYSLER"); put("3D3", "DODGE");
        put("3D4", "DODGE"); put("3P3", "PLYMOUTH");

        // US continued (4-5 prefix)
        put("4F2", "MAZDA"); put("4F3", "MAZDA"); put("4F4", "MAZDA");
        put("4J8", "JEEP"); put("4JG", "MERCEDES"); put("4US", "BMW");
        put("4T1", "TOYOTA"); put("4T3", "TOYOTA");
        put("5FN", "HONDA"); put("5J6", "HONDA"); put("5J8", "ACURA");
        put("5NP", "KIA"); put("5TB", "TOYOTA"); put("5TD", "TOYOTA");
        put("5TE", "TOYOTA"); put("5TF", "TOYOTA"); put("5YJ", "TESLA");

        // US - Mitsubishi
        put("4A3", "MITSUBISHI"); put("4A4", "MITSUBISHI"); put("4A5", "MITSUBISHI");
        put("4B3", "MITSUBISHI");

        // Japanese
        put("JA3", "MITSUBISHI"); put("JA4", "MITSUBISHI"); put("JA7", "MITSUBISHI");
        put("JF1", "SUBARU"); put("JF2", "SUBARU"); put("JF3", "SUBARU");
        put("JH4", "ACURA"); put("JHG", "HONDA"); put("JHL", "HONDA"); put("JHM", "HONDA");
        put("JM1", "MAZDA"); put("JM3", "MAZDA"); put("JM7", "MAZDA");
        put("JN1", "NISSAN"); put("JN3", "NISSAN"); put("JN6", "NISSAN"); put("JN8", "NISSAN");
        put("JS1", "SUZUKI"); put("JS2", "SUZUKI"); put("JS3", "SUZUKI");
        put("JT2", "TOYOTA"); put("JT3", "TOYOTA"); put("JT4", "TOYOTA");
        put("JT5", "TOYOTA"); put("JT6", "TOYOTA"); put("JT8", "LEXUS");
        put("JTD", "TOYOTA"); put("JTE", "TOYOTA"); put("JTH", "LEXUS");
        put("JTJ", "LEXUS"); put("JTK", "TOYOTA"); put("JTL", "TOYOTA");
        put("JTM", "TOYOTA"); put("JTN", "TOYOTA");

        // Korean
        put("KM8", "KIA"); put("KN8", "KIA"); put("KNA", "KIA");
        put("KND", "KIA"); put("KNH", "KIA"); put("KNJ", "KIA");
        put("KMH", "HYUNDAI"); put("KMF", "HYUNDAI"); put("KM1", "HYUNDAI");

        // European - French
        put("VF1", "RENAULT"); put("VF2", "RENAULT"); put("VF3", "PEUGEOT");
        put("VF4", "PEUGEOT"); put("VF6", "RENAULT"); put("VF7", "CITROEN");
        put("VF8", "MATRA"); put("VF9", "BUGATTI");

        // European - German
        put("W0L", "OPEL"); put("W0V", "OPEL");
        put("WA1", "AUDI"); put("WAU", "AUDI"); put("WUA", "AUDI");
        put("WBA", "BMW"); put("WBM", "BMW"); put("WBS", "BMW"); put("WBX", "BMW");
        put("WBY", "BMW"); put("WB1", "BMW"); put("WB2", "BMW"); put("WB3", "BMW");
        put("WD0", "MERCEDES"); put("WD1", "MERCEDES"); put("WD2", "MERCEDES");
        put("WD3", "MERCEDES"); put("WD4", "MERCEDES"); put("WD5", "MERCEDES");
        put("WD8", "MERCEDES"); put("WDA", "MERCEDES"); put("WDB", "MERCEDES");
        put("WDC", "MERCEDES"); put("WDD", "MERCEDES"); put("WDE", "MERCEDES");
        put("WDF", "MERCEDES"); put("WMW", "MINI");
        put("WP0", "PORSCHE"); put("WP1", "PORSCHE");
        put("WVG", "VOLKSWAGEN"); put("WVW", "VOLKSWAGEN");
        put("WV1", "VOLKSWAGEN"); put("WV2", "VOLKSWAGEN"); put("WV3", "VOLKSWAGEN");

        // European - British
        put("SAJ", "JAGUAR"); put("SAD", "JAGUAR"); put("SAF", "JAGUAR"); put("SAX", "JAGUAR");
        put("SCC", "LOTUS"); put("SCF", "ASTON_MARTIN");
        put("SAL", "LAND_ROVER"); put("SAR", "ROVER"); put("SHH", "LAND_ROVER");
        put("SJN", "NISSAN");  // Nissan UK

        // European - Swedish
        put("YV1", "VOLVO"); put("YV2", "VOLVO"); put("YV3", "VOLVO");
        put("YV4", "VOLVO"); put("YV5", "VOLVO"); put("YS3", "SAAB");

        // European - Italian
        put("ZA9", "LAMBORGHINI"); put("ZAM", "MASERATI"); put("ZAR", "ALFA_ROMEO");
        put("ZCF", "IVECO"); put("ZFA", "FIAT"); put("ZFF", "FERRARI");
        put("ZHW", "LAMBORGHINI"); put("ZLA", "LANCIA");

        // Asian - Mitsubishi (additional)
        put("MA3", "MITSUBISHI"); put("MB3", "MITSUBISHI"); put("ML3", "MITSUBISHI");
        put("MMB", "MITSUBISHI"); put("MMC", "MITSUBISHI"); put("MMD", "MITSUBISHI");
        put("MMT", "MITSUBISHI"); put("MZ2", "MITSUBISHI"); put("MZ3", "MITSUBISHI");

        // Asian - Chinese manufacturers
        put("LVS", "FORD"); put("LVY", "VOLVO"); put("LVG", "TOYOTA");
        put("L56", "HYUNDAI"); put("L5Y", "MAZDA");

        // South American manufacturers
        put("9BW", "VOLKSWAGEN"); put("9BF", "FORD"); put("9BG", "CHEVY");
        put("9BD", "FIAT"); put("9BM", "MERCEDES"); put("9BN", "JAGUAR");
        put("93H", "HONDA"); put("93W", "PEUGEOT"); put("93X", "CITROEN");
        put("93Y", "RENAULT"); put("94D", "NISSAN"); put("9FB", "RENAULT");
    }};

    public DTCDatabase(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
        this.context = context;
        copyDatabaseFromAssets();
    }

    private void copyDatabaseFromAssets() {
        String dbPath = context.getDatabasePath(DB_NAME).getPath();
        File dbFile = new File(dbPath);

        if (!dbFile.exists()) {
            try {
                // Create directories if needed
                dbFile.getParentFile().mkdirs();

                // Copy from assets
                InputStream input = context.getAssets().open(DB_NAME);
                OutputStream output = new FileOutputStream(dbPath);

                byte[] buffer = new byte[1024];
                int length;
                while ((length = input.read(buffer)) > 0) {
                    output.write(buffer, 0, length);
                }

                output.flush();
                output.close();
                input.close();

                Log.i(TAG, "Database copied from assets");
            } catch (IOException e) {
                Log.e(TAG, "Error copying database", e);
            }
        }
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // Database is copied from assets, no need to create
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Handle database upgrades if needed
    }

    /**
     * Get manufacturer from VIN
     */
    public String getManufacturerFromVIN(String vin) {
        if (vin == null || vin.length() < 3) {
            return null;
        }

        String wmi = vin.substring(0, 3).toUpperCase();

        // Check exact WMI match
        if (WMI_MAP.containsKey(wmi)) {
            return WMI_MAP.get(wmi);
        }

        // Check first 2 characters for broader matches
        String wmi2 = vin.substring(0, 2).toUpperCase();

        // Chrysler Group fallbacks
        if (wmi2.equals("1C")) return "CHRYSLER";
        if (wmi2.equals("1D")) return "DODGE";
        if (wmi2.equals("1P")) return "PLYMOUTH";
        if (wmi2.equals("2C")) return "CHRYSLER";
        if (wmi2.equals("2D")) return "DODGE";
        if (wmi2.equals("2P")) return "PLYMOUTH";
        if (wmi2.equals("3C")) return "CHRYSLER";
        if (wmi2.equals("3D")) return "DODGE";
        if (wmi2.equals("3P")) return "PLYMOUTH";

        // GM fallback for any unmatched 1G/2G/3G prefix
        if (wmi2.equals("1G")) return "GM";
        if (wmi2.equals("2G")) return "GM";
        if (wmi2.equals("3G")) return "GM";

        // Note: INFINITI shares NISSAN VINs (JN prefix)
        // Both are handled by NISSAN mapping above

        return null;
    }

    /**
     * Smart DTC lookup with manufacturer context
     */
    public DTCResult getSmartDefinition(String code, String vin, String manufacturer) {
        if (database == null) {
            database = getReadableDatabase();
        }

        code = code.toUpperCase();
        DTCResult result = new DTCResult(code);

        // Determine if manufacturer-specific
        if (code.startsWith("P") && code.length() == 5) {
            char secondChar = code.charAt(1);
            result.isManufacturerSpecific = (secondChar == '1' || secondChar == '3');
        }

        // Extract manufacturer from VIN if provided
        if (manufacturer == null && vin != null) {
            manufacturer = getManufacturerFromVIN(vin);
        }

        // Get all definitions for this code
        List<DTCDefinition> allDefs = getAllDefinitions(code);
        result.totalDefinitions = allDefs.size();

        if (allDefs.isEmpty()) {
            return result;
        }

        // Find best match
        DTCDefinition primary = null;

        // 1. Try manufacturer-specific match
        if (manufacturer != null) {
            for (DTCDefinition def : allDefs) {
                if (def.manufacturer.equalsIgnoreCase(manufacturer)) {
                    primary = def;
                    break;
                }
            }
        }

        // 2. Try generic
        if (primary == null) {
            for (DTCDefinition def : allDefs) {
                if (def.isGeneric) {
                    primary = def;
                    break;
                }
            }
        }

        // 3. Use most common definition
        if (primary == null && !allDefs.isEmpty()) {
            Map<String, Integer> descCounts = new HashMap<>();
            Map<String, DTCDefinition> descFirstDef = new HashMap<>();

            for (DTCDefinition def : allDefs) {
                int count = descCounts.getOrDefault(def.description, 0) + 1;
                descCounts.put(def.description, count);
                if (!descFirstDef.containsKey(def.description)) {
                    descFirstDef.put(def.description, def);
                }
            }

            // Find most common
            String mostCommonDesc = null;
            int maxCount = 0;
            for (Map.Entry<String, Integer> entry : descCounts.entrySet()) {
                if (entry.getValue() > maxCount) {
                    maxCount = entry.getValue();
                    mostCommonDesc = entry.getKey();
                }
            }

            if (mostCommonDesc != null) {
                primary = descFirstDef.get(mostCommonDesc);
            }
        }

        result.primary = primary;

        // Add alternatives
        for (DTCDefinition def : allDefs) {
            if (def != primary) {
                result.alternatives.add(def);
            }
        }

        return result;
    }

    /**
     * Get all definitions for a code
     */
    private List<DTCDefinition> getAllDefinitions(String code) {
        List<DTCDefinition> results = new ArrayList<>();

        String query = "SELECT manufacturer, description, is_generic " +
                      "FROM dtc_definitions WHERE code = ? " +
                      "ORDER BY is_generic DESC, manufacturer";

        Cursor cursor = database.rawQuery(query, new String[]{code});

        while (cursor.moveToNext()) {
            DTCDefinition def = new DTCDefinition();
            def.code = code;
            def.manufacturer = cursor.getString(0);
            def.description = cursor.getString(1);
            def.isGeneric = cursor.getInt(2) == 1;
            results.add(def);
        }

        cursor.close();
        return results;
    }

    /**
     * Get simple definition (backward compatibility)
     */
    public String getDefinition(String code, String manufacturer) {
        DTCResult result = getSmartDefinition(code, null, manufacturer);
        if (result.primary != null) {
            return result.primary.description;
        }
        return "Unknown code";
    }

    /**
     * DTC Definition
     */
    public static class DTCDefinition {
        public String code;
        public String manufacturer;
        public String description;
        public boolean isGeneric;

        @Override
        public String toString() {
            String prefix = isGeneric ? "[GENERIC]" : "[" + manufacturer + "]";
            return code + " " + prefix + " " + description;
        }
    }

    /**
     * Smart lookup result
     */
    public static class DTCResult {
        public String code;
        public DTCDefinition primary;
        public List<DTCDefinition> alternatives;
        public int totalDefinitions;
        public boolean isManufacturerSpecific;

        public DTCResult(String code) {
            this.code = code;
            this.alternatives = new ArrayList<>();
            this.totalDefinitions = 0;
            this.isManufacturerSpecific = false;
        }
    }

    @Override
    public synchronized void close() {
        if (database != null) {
            database.close();
        }
        super.close();
    }
}