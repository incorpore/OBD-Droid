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

    /**
     * VIN decode result with all available fields
     */
    public static class VehicleInfo {
        public String vin;
        public String make;
        public String model;
        public String modelYear;
        public String bodyClass;
        public String bodyStyle;
        public String driveType;
        public String fuelType;
        public String fuelTypePrimary;
        public String engineConfiguration;
        public String transmission;
        public String vehicleType;
        public String manufacturer;
        public String plantCountry;
        public String displacementL;
        public String engineCylinders;
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

        // Validate VIN format
        if (!validateVIN(vin)) {
            info.valid = false;
            info.errorMessage = "Invalid VIN format";
            return info;
        }

        try {
            // Extract components
            String wmi = vin.substring(0, 3);
            char yearChar = vin.charAt(9);
            Integer year = decodeYear(yearChar, vin.charAt(6));

            // Query database for make/manufacturer
            queryMakeAndManufacturer(wmi, info);

            // Set model year
            if (year != null) {
                info.modelYear = String.valueOf(year);
            }

            // Try to get detailed info using VIN schema patterns
            if (year != null) {
                queryVinSchemaDetails(vin, wmi, year, info);
            }

            info.valid = info.make != null;
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

        try (Cursor cursor = database.rawQuery(query, new String[]{wmi})) {
            if (cursor.moveToFirst()) {
                info.make = cursor.getString(0);
                info.manufacturer = cursor.getString(1);
                info.plantCountry = cursor.getString(2);
            }
        } catch (Exception e) {
            Log.w(TAG, "Error querying make: " + e.getMessage());
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

                // Extract model from schema name if possible
                if (schemaName != null && info.model == null) {
                    extractModelFromSchema(schemaName, info);
                }

                // Query patterns for detailed attributes
                queryPatternAttributes(schemaId, vin, info);
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
        // Query for body and drivetrain
        queryAttribute(schemaId, vin, "BodyStyle", s -> info.bodyStyle = s);
        queryAttribute(schemaId, vin, "DriveType", s -> info.driveType = s);
        queryAttribute(schemaId, vin, "VehicleType", s -> info.vehicleType = s);
        queryAttribute(schemaId, vin, "Transmission", s -> info.transmission = s);

        // Query for engine attributes
        queryAttribute(schemaId, vin, "FuelType", s -> info.fuelType = s);
        queryAttributeByElementId(schemaId, vin, 24, s -> info.fuelTypePrimary = s); // Element 24 = FuelTypePrimary
        queryAttribute(schemaId, vin, "EngineConfiguration", s -> info.engineConfiguration = s);
        queryAttributeByElementId(schemaId, vin, 13, s -> info.displacementL = s); // Element 13 = DisplacementL
        queryAttributeByElementId(schemaId, vin, 9, s -> info.engineCylinders = s); // Element 9 = EngineCylinders
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
     * Extract various VIN position patterns
     */
    private String[] extractVinPositions(String vin) {
        return new String[]{
            vin.substring(3, 6),   // Positions 4-6
            vin.substring(3, 8),   // Positions 4-8
            vin.substring(4, 6),   // Positions 5-6
            vin.substring(4, 8),   // Positions 5-8
            String.valueOf(vin.charAt(6)), // Position 7
            String.valueOf(vin.charAt(7)), // Position 8
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
        if (database != null && database.isOpen()) {
            database.close();
            Log.d(TAG, "Database closed");
        }
    }
}
