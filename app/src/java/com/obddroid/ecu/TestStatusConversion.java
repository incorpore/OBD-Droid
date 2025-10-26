package com.obddroid.core.ecu;

import android.util.Log;
import java.util.logging.Logger;

/**
 * Specialized conversion for OBD-II test/monitor status fields per SAE J1979
 *
 * According to the OBD-II standard (SAE J1979):
 * - Test availability bits: 0 = NOT supported, 1 = supported
 * - Test readiness bits: 0 = complete/ready, 1 = incomplete/not ready
 *
 * For TEST_STATUS_4 (used for continuous monitors):
 * - Bit 0: Test availability (1 = available)
 * - Bit 4: Test readiness (1 = incomplete)
 *
 * For TEST_STATUS_8 (used for non-continuous monitors):
 * - Bit 0: Test readiness (1 = incomplete)
 * - Bit 8: Test availability (1 = available)
 */
public class TestStatusConversion extends NumericConversion {

    /** SerialVersion UID */
    private static final long serialVersionUID = -7498739122873083421L;

    /** Logger for this class */
    private static final Logger log = Logger.getLogger("data.cnv.test");

    /** Bit offset for the second status bit */
    private final int bitOffset;

    /** Whether this is inverted logic (TEST_STATUS_8) */
    private final boolean inverted;

    /**
     * Create a test status conversion
     * @param bitOffset The offset between the two status bits (4 or 8)
     * @param inverted Whether to use inverted logic (true for TEST_STATUS_8)
     */
    public TestStatusConversion(int bitOffset, boolean inverted) {
        this.bitOffset = bitOffset;
        this.inverted = inverted;
    }

    /**
     * Create a TEST_STATUS_4 conversion (default)
     */
    public TestStatusConversion() {
        this(4, false);
    }

    @Override
    public Number memToPhys(long value) {
        return value;
    }

    @Override
    public Number physToMem(Number value) {
        return value;
    }

    @Override
    public String physToPhysFmtString(Number physVal, String format) {
        long val = physVal.longValue();

        // Extract the two status bits
        boolean bit0 = (val & 0x01) != 0;
        boolean bitOffsetValue = (val & (1 << bitOffset)) != 0;

        // Log the raw values for debugging
        String convType = inverted ? "TEST_STATUS_8" : "TEST_STATUS_4";
        Log.d("TestStatus", String.format(
            "%s: value=0x%02X, bit0=%b, bit%d=%b",
            convType, val, bit0, bitOffset, bitOffsetValue));

        // Interpret based on the conversion type
        boolean isAvailable;
        boolean isComplete;

        if (inverted) {
            // TEST_STATUS_8 logic (non-continuous monitors)
            // Per SAE J1979:
            // Bit 0: Test readiness (0=complete, 1=incomplete)
            // Bit 8: Test availability (0=not available, 1=available)
            isComplete = !bit0;  // Bit 0 clear means complete
            isAvailable = bitOffsetValue;  // Bit 8 set means available
        } else {
            // TEST_STATUS_4 logic (continuous monitors)
            // Per SAE J1979:
            // Bit 0: Test availability (0=not available, 1=available)
            // Bit 4: Test readiness (0=complete, 1=incomplete)
            isAvailable = bit0;  // Bit 0 set means available
            isComplete = !bitOffsetValue;  // Bit 4 clear means complete
        }

        // Format the output
        StringBuilder result = new StringBuilder();

        // Show availability with appropriate checkbox
        if (isAvailable) {
            result.append("(*) Available");
        } else {
            result.append("(  ) Not available");
        }
        result.append(System.lineSeparator());

        // Show completion status with appropriate checkbox
        if (!isAvailable) {
            // If not available, show N/A
            result.append("(  ) N/A");
        } else if (isComplete) {
            // If complete, show with asterisk
            result.append("(*) Complete");
        } else {
            // If incomplete, show without asterisk
            result.append("(  ) Incomplete");
        }

        // Final result logging
        Log.d("TestStatus", String.format(
            "  -> Result: available=%b, complete=%b", isAvailable, isComplete));

        return result.toString();
    }
}