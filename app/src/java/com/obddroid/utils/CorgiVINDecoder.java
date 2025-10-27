package com.obddroid.utils;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Corgi VIN Decoder - Offline VIN decoding using NHTSA vPIC SQLite database
 *
 * Uses the complete vPIC database (66MB) for full offline VIN decoding.
 * Provides dealer-grade data without internet connection.
 *
 * Database: vpic.lite.db from @cardog/corgi
 * Source: https://github.com/cardog-ai/corgi
 */
public class CorgiVINDecoder {

    private static final String TAG = "CorgiVINDecoder";
    private static final String DB_NAME = "vpic.lite.db";
    private static final int DB_VERSION = 1;

    private final Context context;
    private SQLiteDatabase database;
    private final Object databaseLock = new Object();

    /**
     * VIN decode result with all available fields
     */
    public static class VehicleInfo {
        public String vin;
        public String make;
        public String model;
        public String modelYear;
        public String series;
        public String trim;
        public String bodyClass;
        public String bodyStyle;
        public String driveType;
        public String fuelType;
        public String fuelTypePrimary;
        public String engineConfiguration;
        public String transmission;
        public String transmissionStyle;
        public String transmissionSpeeds;
        public String vehicleType;
        public String manufacturer;
        public String plantCountry;
        public String displacementL;
        public String displacementCC;
        public String engineCylinders;
        public String engineModel;
        public String doors;
        public String wheelBase;
        public String gvwr;
        public String curbWeight;
        public String engineManufacturer;
        public String turbo;
        public boolean valid;
        public String errorMessage;

        @Override
        public String toString() {
            if (!valid) {
                return "Invalid VIN: " + errorMessage;
            }
            return String.format("%s %s %s (%s)",
                    modelYear != null ? modelYear : "Unknown",
                    make != null ? make : "Unknown",
                    model != null ? model : "Unknown",
                    bodyClass != null ? bodyClass : "Unknown Type");
        }
    }

    public CorgiVINDecoder(Context context) {
        this.context = context.getApplicationContext();
        initDatabase();
    }

    /**
     * Initialize database - copy from assets if needed
     */
    private void initDatabase() {
        try {
            File dbFile = context.getDatabasePath(DB_NAME);

            // Copy database from assets if it doesn't exist
            if (!dbFile.exists()) {
                Log.d(TAG, "Copying vPIC database from assets...");
                copyDatabaseFromAssets(dbFile);
                Log.d(TAG, "✓ Database copied successfully");
            }

            // Open database in read-only mode
            database = SQLiteDatabase.openDatabase(
                    dbFile.getAbsolutePath(),
                    null,
                    SQLiteDatabase.OPEN_READONLY
            );

            Log.d(TAG, "✓ vPIC database opened: " + dbFile.length() + " bytes");

        } catch (IOException e) {
            Log.e(TAG, "Failed to initialize database", e);
        }
    }

    /**
     * Copy database from assets to app database directory
     */
    private void copyDatabaseFromAssets(File dbFile) throws IOException {
        dbFile.getParentFile().mkdirs();

        try (InputStream input = context.getAssets().open("databases/" + DB_NAME);
             OutputStream output = new FileOutputStream(dbFile)) {

            byte[] buffer = new byte[8192];
            int length;
            while ((length = input.read(buffer)) > 0) {
                output.write(buffer, 0, length);
            }
            output.flush();
        }
    }

    /**
     * Decode VIN - main entry point
     */
    public VehicleInfo decode(String vin) {
        VehicleInfo info = new VehicleInfo();
        info.vin = vin;

        Log.d(TAG, "=== Decoding VIN: " + vin + " ===");

        // Validate VIN format
        if (!validateVIN(vin)) {
            info.valid = false;
            info.errorMessage = "Invalid VIN format";
            Log.w(TAG, "✗ Invalid VIN format: " + vin);
            return info;
        }

        synchronized (databaseLock) {
            if (database == null || !database.isOpen()) {
                Log.d(TAG, "Database not open, initializing...");
                initDatabase();
            }

            try {
            // Extract components
            String wmi = vin.substring(0, 3);
            char yearChar = vin.charAt(9);
            Integer year = decodeYear(yearChar, vin.charAt(6));
            Log.d(TAG, "Extracted - WMI: " + wmi + ", Year: " + year);

            // Query database for make/manufacturer
            queryMakeAndManufacturer(wmi, info);

            // If Make is null but we have a manufacturer, try to extract make from manufacturer name
            if (info.make == null && info.manufacturer != null) {
                info.make = extractMakeFromManufacturer(info.manufacturer);
                Log.d(TAG, "Extracted make from manufacturer: " + info.make);
            }

            // Set model year
            if (year != null) {
                info.modelYear = String.valueOf(year);
            }

            // Try to get detailed info using VIN schema patterns
            if (year != null) {
                queryVinSchemaDetails(vin, wmi, year, info);
            }

            // Valid if we have either make or manufacturer
            info.valid = (info.make != null || info.manufacturer != null);
            if (!info.valid) {
                info.errorMessage = "WMI not found in database";
            }

            } catch (Exception e) {
                Log.e(TAG, "Error decoding VIN: " + vin, e);
                info.valid = false;
                info.errorMessage = "Decode error: " + e.getMessage();
            }

            return info;
        }
    }

    /**
     * Query make and manufacturer from WMI
     */
    private void queryMakeAndManufacturer(String wmi, VehicleInfo info) {
        String query = "SELECT m.Name, mfr.Name, c.Name " +
                      "FROM Wmi w " +
                      "LEFT JOIN Make m ON w.MakeId = m.Id " +
                      "LEFT JOIN Manufacturer mfr ON w.ManufacturerId = mfr.Id " +
                      "LEFT JOIN Country c ON w.CountryId = c.Id " +
                      "WHERE w.Wmi = ? LIMIT 1";

        Log.d(TAG, "Querying database for WMI: " + wmi);
        try (Cursor cursor = database.rawQuery(query, new String[]{wmi})) {
            if (cursor.moveToFirst()) {
                info.make = cursor.getString(0);
                info.manufacturer = cursor.getString(1);
                info.plantCountry = cursor.getString(2);
                Log.d(TAG, "✓ Found: Make=" + info.make + ", Mfr=" + info.manufacturer + ", Country=" + info.plantCountry);
            } else {
                Log.w(TAG, "✗ WMI '" + wmi + "' not found in database");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error querying make for WMI '" + wmi + "': " + e.getMessage(), e);
        }
    }

    /**
     * Query detailed vehicle info using VIN schema patterns
     */
    private void queryVinSchemaDetails(String vin, String wmi, int year, VehicleInfo info) {
        // Get VIN schema for this WMI and year
        String schemaQuery = "SELECT vs.Id, vs.Name " +
                           "FROM Wmi w " +
                           "JOIN Wmi_VinSchema wvs ON w.Id = wvs.WmiId " +
                           "JOIN VinSchema vs ON wvs.VinSchemaId = vs.Id " +
                           "WHERE w.Wmi = ? " +
                           "AND wvs.YearFrom <= ? " +
                           "AND (wvs.YearTo IS NULL OR wvs.YearTo >= ?) " +
                           "LIMIT 1";

        try (Cursor cursor = database.rawQuery(schemaQuery,
                new String[]{wmi, String.valueOf(year), String.valueOf(year)})) {

            if (cursor.moveToFirst()) {
                int schemaId = cursor.getInt(0);
                String schemaName = cursor.getString(1);
                Log.d(TAG, "Found VIN schema: " + schemaName + " (ID: " + schemaId + ")");

                // Query patterns for detailed attributes
                queryPatternAttributes(schemaId, vin, info);

                // Fallback: Extract model from schema name only if pattern query didn't find it
                if (info.model == null && schemaName != null) {
                    extractModelFromSchema(schemaName, info);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error querying schema: " + e.getMessage());
        }
    }

    /**
     * Extract model name from VIN schema name
     */
    private void extractModelFromSchema(String schemaName, VehicleInfo info) {
        // Schema names often contain model info: "Honda Accord Schema 2003"
        String cleaned = schemaName.replaceAll("Schema.*", "").trim();

        // Remove make name if present
        if (info.make != null) {
            cleaned = cleaned.replace(info.make, "").trim();
        }

        if (!cleaned.isEmpty() && !cleaned.equalsIgnoreCase(info.make)) {
            info.model = cleaned;
        }
    }

    /**
     * Query pattern attributes for body class, drivetrain, etc.
     */
    private void queryPatternAttributes(int schemaId, String vin, VehicleInfo info) {
        // Query Model first (ElementId = 28) - this is the most important (needs Model table lookup)
        queryPatternByElement(schemaId, vin, 28, "Model", s -> info.model = s);

        // Query identification fields - these are DIRECT VALUES in AttributeId, not table lookups!
        queryAttributeByElementId(schemaId, vin, 34, s -> info.series = s); // Element 34 = Series (direct value)
        queryAttributeByElementId(schemaId, vin, 38, s -> info.trim = s); // Element 38 = Trim (direct value)

        // Query vehicle classification attributes
        queryPatternByElement(schemaId, vin, 5, "BodyClass", s -> info.bodyClass = s); // Needs BodyClass table
        queryAttribute(schemaId, vin, "BodyStyle", s -> info.bodyStyle = s);
        queryAttribute(schemaId, vin, "VehicleType", s -> info.vehicleType = s);
        queryAttributeByElementId(schemaId, vin, 14, s -> info.doors = s); // Element 14 = Doors (direct value)
        queryAttributeByElementId(schemaId, vin, 109, s -> info.wheelBase = s); // Element 109 = Wheelbase (direct value)

        // Query drivetrain and transmission
        queryAttribute(schemaId, vin, "DriveType", s -> info.driveType = s);
        queryPatternByElement(schemaId, vin, 37, "Transmission", s -> info.transmissionStyle = s); // Needs Transmission table
        queryAttributeByElementId(schemaId, vin, 63, s -> info.transmissionSpeeds = s); // Direct value

        // Query engine attributes
        queryAttributeByElementId(schemaId, vin, 18, s -> info.engineModel = s); // Element 18 = Engine Model (direct value: "M276")
        queryAttributeByElementId(schemaId, vin, 146, s -> info.engineManufacturer = s); // Element 146 = Engine Manufacturer (direct value: "Daimler")
        queryAttribute(schemaId, vin, "FuelType", s -> info.fuelType = s);
        queryPatternByElement(schemaId, vin, 24, "FuelType", s -> info.fuelTypePrimary = s); // Needs FuelType table
        queryPatternByElement(schemaId, vin, 64, "EngineConfiguration", s -> info.engineConfiguration = s); // Needs EngineConfiguration table
        queryPatternByElement(schemaId, vin, 135, "Turbo", s -> info.turbo = s); // Element 135 = Turbo

        // Displacement and cylinders are raw numeric values (not lookup tables)
        queryAttributeByElementId(schemaId, vin, 13, s -> info.displacementL = s); // Displacement L
        queryAttributeByElementId(schemaId, vin, 12, s -> info.displacementCC = s); // Displacement CC
        queryAttributeByElementId(schemaId, vin, 9, s -> info.engineCylinders = s); // Engine Cylinders

        // Weight specifications
        queryPatternByElement(schemaId, vin, 25, "GrossVehicleWeightRating", s -> info.gvwr = s); // Element 25 = GVWR From

        queryPatternByElement(schemaId, vin, 62, "ValvetrainDesign", s -> {
            // Store valvetrain if we don't have engine config yet
            if (info.engineConfiguration == null) {
                info.engineConfiguration = s;
            }
        });
    }

    /**
     * Query a specific attribute from patterns
     */
    private void queryAttribute(int schemaId, String vin, String tableName, AttributeSetter setter) {
        String query = "SELECT a.Name " +
                      "FROM Pattern p " +
                      "JOIN Element e ON p.ElementId = e.Id " +
                      "JOIN " + tableName + " a ON CAST(p.AttributeId AS INTEGER) = a.Id " +
                      "WHERE p.VinSchemaId = ? " +
                      "AND p.Keys = ? " +
                      "LIMIT 1";

        // Extract relevant VIN positions based on element
        // Simplified: just try a few common pattern positions
        String[] positions = extractVinPositions(vin);

        for (String pos : positions) {
            try (Cursor cursor = database.rawQuery(query, new String[]{String.valueOf(schemaId), pos})) {
                if (cursor.moveToFirst()) {
                    setter.set(cursor.getString(0));
                    return; // Found it
                }
            } catch (Exception e) {
                // Continue trying other positions
            }
        }
    }

    /**
     * Query pattern by Element ID with lookup table join
     * This queries the Pattern table and joins to the attribute table (Model, BodyClass, etc.)
     */
    private void queryPatternByElement(int schemaId, String vin, int elementId, String tableName, AttributeSetter setter) {
        String query = "SELECT a.Name " +
                      "FROM Pattern p " +
                      "JOIN " + tableName + " a ON CAST(p.AttributeId AS INTEGER) = a.Id " +
                      "WHERE p.VinSchemaId = ? " +
                      "AND p.ElementId = ? " +
                      "AND p.Keys = ? " +
                      "LIMIT 1";

        String[] positions = extractVinPositions(vin);

        for (String pos : positions) {
            try (Cursor cursor = database.rawQuery(query,
                    new String[]{String.valueOf(schemaId), String.valueOf(elementId), pos})) {
                if (cursor.moveToFirst()) {
                    String value = cursor.getString(0);
                    Log.d(TAG, "✓ Pattern match: Element=" + tableName + ", Keys=" + pos + ", Value=" + value);
                    setter.set(value);
                    return; // Found it
                }
            } catch (Exception e) {
                // Continue trying other positions or table doesn't exist
            }
        }
    }

    /**
     * Query attribute by Element ID (for elements that don't have lookup tables)
     */
    private void queryAttributeByElementId(int schemaId, String vin, int elementId, AttributeSetter setter) {
        String query = "SELECT p.AttributeId " +
                      "FROM Pattern p " +
                      "WHERE p.VinSchemaId = ? " +
                      "AND p.ElementId = ? " +
                      "AND p.Keys = ? " +
                      "LIMIT 1";

        String[] positions = extractVinPositions(vin);

        for (String pos : positions) {
            try (Cursor cursor = database.rawQuery(query,
                    new String[]{String.valueOf(schemaId), String.valueOf(elementId), pos})) {
                if (cursor.moveToFirst()) {
                    setter.set(cursor.getString(0));
                    return; // Found it
                }
            } catch (Exception e) {
                // Continue trying other positions
            }
        }
    }

    /**
     * Extract various VIN position patterns to try matching against database
     * The database uses different key formats (e.g., "DA5H", "DA7F", etc.)
     */
    private String[] extractVinPositions(String vin) {
        return new String[]{
            vin.substring(3, 7),   // Positions 4-7 (most common for model: "DA5H")
            vin.substring(3, 8),   // Positions 4-8
            vin.substring(3, 6),   // Positions 4-6
            vin.substring(4, 8),   // Positions 5-8
            vin.substring(4, 7),   // Positions 5-7
            vin.substring(4, 6),   // Positions 5-6
            vin.substring(5, 8),   // Positions 6-8
            String.valueOf(vin.charAt(6)), // Position 7 only
            String.valueOf(vin.charAt(7)), // Position 8 only
        };
    }

    @FunctionalInterface
    private interface AttributeSetter {
        void set(String value);
    }

    /**
     * Validate VIN format
     */
    private boolean validateVIN(String vin) {
        if (vin == null || vin.length() != 17) {
            return false;
        }

        // VIN cannot contain I, O, Q
        if (vin.matches(".*[IOQ].*")) {
            return false;
        }

        return vin.matches("[A-HJ-NPR-Z0-9]{17}");
    }

    /**
     * Decode model year from VIN position 10
     */
    private Integer decodeYear(char yearChar, char digitChar) {
        // Year code mapping (simplified)
        int baseYear;

        if (yearChar >= 'A' && yearChar <= 'H') {
            baseYear = 2010 + (yearChar - 'A');
        } else if (yearChar >= 'J' && yearChar <= 'N') {
            baseYear = 2018 + (yearChar - 'J');
        } else if (yearChar >= 'P' && yearChar <= 'Y') {
            baseYear = 2023 + (yearChar - 'P');
        } else if (yearChar >= '1' && yearChar <= '9') {
            baseYear = 2001 + (yearChar - '1');
        } else {
            return null;
        }

        return baseYear;
    }

    /**
     * Extract make name from manufacturer string
     * Examples:
     *   "MERCEDES-BENZ OF NORTH AMERICA, INC." -> "Mercedes-Benz"
     *   "AMERICAN HONDA MOTOR CO., INC." -> "Honda"
     */
    private String extractMakeFromManufacturer(String manufacturer) {
        if (manufacturer == null) return null;

        String lower = manufacturer.toLowerCase();

        // Common patterns
        if (lower.contains("mercedes") || lower.contains("benz")) {
            return "Mercedes-Benz";
        }
        if (lower.contains("honda")) {
            return "Honda";
        }
        if (lower.contains("toyota")) {
            return "Toyota";
        }
        if (lower.contains("ford")) {
            return "Ford";
        }
        if (lower.contains("chevrolet") || lower.contains("chevy")) {
            return "Chevrolet";
        }
        if (lower.contains("nissan")) {
            return "Nissan";
        }
        if (lower.contains("bmw")) {
            return "BMW";
        }
        if (lower.contains("volkswagen") || lower.contains("vw")) {
            return "Volkswagen";
        }
        if (lower.contains("audi")) {
            return "Audi";
        }
        if (lower.contains("porsche")) {
            return "Porsche";
        }
        if (lower.contains("hyundai")) {
            return "Hyundai";
        }
        if (lower.contains("kia")) {
            return "Kia";
        }
        if (lower.contains("mazda")) {
            return "Mazda";
        }
        if (lower.contains("subaru")) {
            return "Subaru";
        }
        if (lower.contains("lexus")) {
            return "Lexus";
        }
        if (lower.contains("acura")) {
            return "Acura";
        }
        if (lower.contains("infiniti")) {
            return "Infiniti";
        }
        if (lower.contains("jeep")) {
            return "Jeep";
        }
        if (lower.contains("dodge")) {
            return "Dodge";
        }
        if (lower.contains("ram")) {
            return "Ram";
        }
        if (lower.contains("chrysler")) {
            return "Chrysler";
        }
        if (lower.contains("tesla")) {
            return "Tesla";
        }
        if (lower.contains("volvo")) {
            return "Volvo";
        }
        if (lower.contains("jaguar")) {
            return "Jaguar";
        }
        if (lower.contains("land rover")) {
            return "Land Rover";
        }
        if (lower.contains("mini")) {
            return "Mini";
        }
        if (lower.contains("fiat")) {
            return "Fiat";
        }
        if (lower.contains("alfa romeo")) {
            return "Alfa Romeo";
        }
        if (lower.contains("maserati")) {
            return "Maserati";
        }
        if (lower.contains("ferrari")) {
            return "Ferrari";
        }
        if (lower.contains("lamborghini")) {
            return "Lamborghini";
        }
        if (lower.contains("buick")) {
            return "Buick";
        }
        if (lower.contains("cadillac")) {
            return "Cadillac";
        }
        if (lower.contains("gmc")) {
            return "GMC";
        }
        if (lower.contains("lincoln")) {
            return "Lincoln";
        }
        if (lower.contains("genesis")) {
            return "Genesis";
        }

        // If no match, try to extract first word before "MOTOR", "AUTOMOBILE", "OF", "CORP", etc.
        String[] stopWords = {" motor", " automobile", " of ", " corp", " inc", " llc", " ltd", " co."};
        String result = manufacturer;
        for (String stop : stopWords) {
            int idx = lower.indexOf(stop);
            if (idx > 0) {
                result = manufacturer.substring(0, idx).trim();
                break;
            }
        }

        // Capitalize properly
        if (result.equals(manufacturer.toUpperCase()) || result.equals(manufacturer.toLowerCase())) {
            // Convert "MERCEDES-BENZ" or "mercedes-benz" to "Mercedes-Benz"
            String[] words = result.split("[\\s-]");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < words.length; i++) {
                if (words[i].length() > 0) {
                    if (i > 0) sb.append(result.contains("-") ? "-" : " ");
                    sb.append(Character.toUpperCase(words[i].charAt(0)));
                    sb.append(words[i].substring(1).toLowerCase());
                }
            }
            result = sb.toString();
        }

        return result;
    }

    /**
     * Get manufacturer name from VIN
     */
    public String getManufacturer(String vin) {
        if (vin == null || vin.length() < 3) return null;
        VehicleInfo info = decode(vin);
        return info.manufacturer;
    }

    /**
     * Get make name from VIN
     */
    public String getMake(String vin) {
        if (vin == null || vin.length() < 3) return null;
        VehicleInfo info = decode(vin);
        return info.make;
    }

    /**
     * Validate VIN (public method)
     */
    public boolean validate(String vin) {
        return validateVIN(vin);
    }

    /**
     * Close database connection
     */
    public void close() {
        synchronized (databaseLock) {
            if (database != null && database.isOpen()) {
                database.close();
                database = null;
                Log.d(TAG, "Database closed");
            }
        }
    }

    /**
     * Reload database (called after update)
     */
    public void reloadDatabase() {
        synchronized (databaseLock) {
            close();
            initDatabase();
            Log.d(TAG, "Database reloaded");
        }
    }
}
