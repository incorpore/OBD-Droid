package com.obddroid.custompid;

import android.util.Log;
import com.obddroid.ecu.Conversion;

/**
 * Conversion implementation that wraps CustomPid formula evaluation
 * Bridges custom PIDs with the existing conversion system
 *
 * @author Wal33D
 */
public class CustomPidConversion implements Conversion {
    private static final long serialVersionUID = 1L;
    private static final String TAG = "CustomPidConversion";

    private final CustomPid customPid;
    private final String formula;
    private final String units;

    /**
     * Create a conversion for a custom PID
     *
     * @param pid The custom PID with formula and units
     */
    public CustomPidConversion(CustomPid pid) {
        this.customPid = pid;
        this.formula = pid.getFormula();
        this.units = pid.getUnits() != null ? pid.getUnits() : "";
    }

    /**
     * Get physical units of measurement
     *
     * @return physical units
     */
    @Override
    public String getUnits() {
        return units;
    }

    /**
     * Convert measurement from storage format to physical value
     *
     * @param value memory value
     * @param numDecimals number of decimals for string formatting
     * @return string representation of numeric value
     */
    @Override
    public String memToString(Number value, int numDecimals) {
        Number physValue = memToPhys(value.longValue());
        String fmt = "%." + numDecimals + "f";
        return String.format(fmt, physValue);
    }

    /**
     * Convert physical value to formatted string
     *
     * @param physVal physical value
     * @param format format string
     * @return formatted string
     */
    @Override
    public String physToPhysFmtString(Number physVal, String format) {
        return String.format(format, physVal);
    }

    /**
     * Convert raw OBD bytes to physical value using custom formula
     *
     * @param value raw memory value (will be converted to byte array)
     * @return physical value after formula evaluation
     */
    public Number memToPhys(long value) {
        try {
            // Convert long value to byte array (up to 4 bytes for A, B, C, D)
            int[] bytes = new int[4];
            bytes[0] = (int) ((value >> 24) & 0xFF); // A
            bytes[1] = (int) ((value >> 16) & 0xFF); // B
            bytes[2] = (int) ((value >> 8) & 0xFF);  // C
            bytes[3] = (int) (value & 0xFF);         // D

            // Evaluate formula with bytes
            double result = PidFormulaParser.evaluate(formula, bytes);

            // Cache the result
            customPid.setCachedValue(result);

            return result;
        } catch (PidFormulaParser.FormulaException e) {
            Log.e(TAG, "Error evaluating formula for PID: " + customPid.getName(), e);
            return 0.0;
        }
    }

    /**
     * Convert raw OBD response bytes directly to physical value
     * This is the preferred method for OBD data processing
     *
     * @param bytes OBD response bytes (A=bytes[0], B=bytes[1], etc.)
     * @return physical value after formula evaluation
     */
    public Number bytesToPhys(int[] bytes) {
        try {
            double result = PidFormulaParser.evaluate(formula, bytes);
            customPid.setCachedValue(result);
            return result;
        } catch (PidFormulaParser.FormulaException e) {
            Log.e(TAG, "Error evaluating formula for PID: " + customPid.getName(), e);
            return 0.0;
        }
    }

    /**
     * Convert physical value back to raw format
     * Note: This is challenging for custom formulas and may not be accurate
     *
     * @param value physical value to convert
     * @return raw memory representation
     */
    public Number physToMem(Number value) {
        // For most custom PIDs, reverse conversion is not straightforward
        // This is mainly for display purposes
        Log.w(TAG, "physToMem called for custom PID - returning approximate value");
        return value.longValue();
    }

    /**
     * Get the custom PID associated with this conversion
     *
     * @return CustomPid object
     */
    public CustomPid getCustomPid() {
        return customPid;
    }

    /**
     * Get the formula being used
     *
     * @return formula string
     */
    public String getFormula() {
        return formula;
    }

    /**
     * Test if this conversion is valid
     *
     * @return true if formula can be evaluated
     */
    public boolean isValid() {
        return PidFormulaParser.isValidFormula(formula);
    }

    @Override
    public String toString() {
        return String.format("CustomPidConversion[%s: %s -> %s]",
                           customPid.getName(),
                           formula,
                           units);
    }
}
