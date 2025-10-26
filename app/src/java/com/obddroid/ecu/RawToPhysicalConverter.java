package com.obddroid.ecu;

/**
 * Minimal abstraction for converting between raw ECU payload values
 * and their physical representation.
 */
public interface RawToPhysicalConverter {

    /**
     * Convert a raw numeric value (as obtained from the ECU payload)
     * into its physical representation.
     *
     * @param value raw numeric value sourced from ECU memory
     * @return physical value, preserving numeric scale
     */
    Number convertRawToPhysical(long value);

    /**
     * Convert a physical value back into the raw ECU representation.
     *
     * @param value physical value
     * @return raw numeric value suitable for ECU write operations
     */
    Number convertPhysicalToRaw(Number value);
}
