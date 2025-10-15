package com.obddroid.core.ecu;

import android.content.Context;
import com.dtcdatabase.DTCDatabase;
import com.obddroid.core.obd.Messages;

import java.util.HashSet;
import java.util.Set;

/**
 * OBD Code List implementation using dtc-database SQLite backend
 * Replaces ResourceBundle-based code lookup with comprehensive 28K+ code database
 *
 * Features:
 * - 28,220+ diagnostic trouble codes
 * - All code types: P (Powertrain), B (Body), C (Chassis), U (Network)
 * - Manufacturer-specific definitions for 33+ brands
 * - i18n support for future translations
 *
 * @author Wal33D
 */
public class DTCDatabaseCodeList extends ObdCodeList {

    private static final long serialVersionUID = 3198654596294230437L;

    private final DTCDatabase database;
    private final String manufacturer;

    /**
     * Creates a new instance of DTCDatabaseCodeList for generic codes
     *
     * @param context Android context
     */
    public DTCDatabaseCodeList(Context context) {
        this(context, null);
    }

    /**
     * Creates a new instance of DTCDatabaseCodeList with manufacturer context
     *
     * @param context Android context
     * @param manufacturer Manufacturer name (e.g., "FORD", "TOYOTA", null for generic)
     */
    public DTCDatabaseCodeList(Context context, String manufacturer) {
        // Pass empty string to avoid loading resource bundle
        // We'll override get() method to use database instead
        super();
        this.database = DTCDatabase.getInstance(context);
        this.manufacturer = manufacturer;
    }

    /**
     * Get EcuCodeItem for a numeric code value
     * Converts numeric value to P/B/C/U code format and looks up in database
     *
     * @param value Numeric code value
     * @return EcuCodeItem with code and description, or fallback if not found
     */
    @Override
    public EcuCodeItem get(Number value) {
        if (value == null) {
            return new EcuCodeItem("", Messages.getString("unknown.code"));
        }

        // Convert numeric value to P/B/C/U code string (e.g., P0420)
        String code = ObdCodeItem.getPCode(value.intValue());

        // Look up in dtc-database
        String description = database.getDescription(code, manufacturer);

        // Fallback if not found
        if (description == null || description.isEmpty()) {
            description = Messages.getString("customer.specific.trouble.code.see.manual");
        }

        return new EcuCodeItem(code, description);
    }

    /**
     * Returns all known code descriptions
     * Note: This would be expensive with 28K+ codes, so we limit this
     *
     * @return Set of code descriptions
     */
    @Override
    public Set<String> values() {
        // For backwards compatibility, return empty set
        // The full database is too large to enumerate casually
        return new HashSet<String>();
    }

    /**
     * Set the current locale for code lookups
     *
     * @param locale Locale code (e.g., "en", "es", "de")
     */
    public void setLocale(String locale) {
        if (database != null) {
            database.setLocale(locale);
        }
    }

    /**
     * Get the current locale
     *
     * @return Current locale code
     */
    public String getLocale() {
        return database != null ? database.getLocale() : "en";
    }

    /**
     * Get the underlying DTCDatabase instance for advanced queries
     *
     * @return DTCDatabase instance
     */
    public DTCDatabase getDatabase() {
        return database;
    }
}
