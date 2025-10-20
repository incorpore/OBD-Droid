package com.obddroid.core.ecu;

/**
 * Formatting contract for presenting raw or physical values.
 */
public interface PhysicalValueFormatter {

    /**
     * Format a physical value using a supplied pattern.
     *
     * @param physicalValue value expressed in physical units
     * @param formatPattern formatting pattern, typically a printf-style format
     * @return formatted string representation
     */
    String formatPhysicalValue(Number physicalValue, String formatPattern);

    /**
     * Format the raw value representation.
     *
     * @param rawValue raw ECU value
     * @param fractionalDigits number of decimal places to keep
     * @return formatted string representation
     */
    String formatRawValue(Number rawValue, int fractionalDigits);

    /**
     * Resolve the unit metadata associated with the value.
     *
     * @return unit label (empty string when not applicable)
     */
    String getUnits();
}
