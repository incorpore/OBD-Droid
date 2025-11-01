package com.obddroid.custompid;

import com.obddroid.ecu.Conversion;
import com.obddroid.ecu.PidDefinition;

/**
 * Adapter that converts CustomPid objects to PidDefinition objects
 * Bridges the custom PID system with the existing OBD infrastructure
 *
 * @author Wal33D
 */
public class CustomPidAdapter {
    private static final String TAG = "CustomPidAdapter";

    /**
     * Convert a CustomPid to a PidDefinition that can be used with existing infrastructure
     *
     * @param customPid The custom PID to convert
     * @return PidDefinition compatible with EcuDataItem system
     */
    public static PidDefinition toPidDefinition(CustomPid customPid) {
        // Create conversion array with custom formula conversion
        CustomPidConversion conversion = new CustomPidConversion(customPid);
        Conversion[] conversions = new Conversion[] { conversion, conversion };

        // Parse PID hex to integer
        int pidInt = customPid.getPidInt();
        if (pidInt == -1) {
            throw new IllegalArgumentException("Invalid PID hex: " + customPid.getPidHex());
        }

        // Generate a unique mnemonic for this custom PID
        String mnemonic = generateMnemonic(customPid);

        // Build PidDefinition
        return PidDefinition.builder()
            .pid(pidInt)
            .offset(0)                          // Custom PIDs start at offset 0
            .lengthBytes(4)                     // Support up to 4 bytes (A, B, C, D)
            .bitOffset(0)
            .bitLength(32)                      // Full 32-bit value
            .bitMask(0xFFFFFFFFL)
            .conversions(conversions)
            .format(getFormat(customPid))
            .label(customPid.getName())
            .mnemonic(mnemonic)
            .minUpdatePeriodMs(customPid.getUpdatePeriod())
            .build();
    }

    /**
     * Generate a unique mnemonic for a custom PID
     * Format: custom_<make>_<name>_<pid>
     *
     * @param pid Custom PID
     * @return Unique mnemonic string
     */
    private static String generateMnemonic(CustomPid pid) {
        StringBuilder mnemonic = new StringBuilder("custom_");

        // Add manufacturer prefix if available
        if (pid.getVehicleMake() != null && !pid.getVehicleMake().isEmpty()) {
            mnemonic.append(pid.getVehicleMake().toLowerCase().replaceAll("[^a-z0-9]", "_"));
            mnemonic.append("_");
        }

        // Add sanitized name
        String name = pid.getName().toLowerCase().replaceAll("[^a-z0-9]", "_");
        mnemonic.append(name);

        // Add PID hex for uniqueness
        mnemonic.append("_");
        mnemonic.append(pid.getPidHex().toLowerCase());

        return mnemonic.toString();
    }

    /**
     * Determine appropriate format string based on units and formula
     *
     * @param pid Custom PID
     * @return Format string for display
     */
    private static String getFormat(CustomPid pid) {
        String units = pid.getUnits();

        if (units == null || units.isEmpty()) {
            return "%.2f";
        }

        // Determine decimal places based on units
        switch (units.toLowerCase()) {
            case "rpm":
            case "km/h":
            case "mph":
            case "%":
                return "%.0f";  // No decimals for these

            case "°c":
            case "°f":
            case "v":           // Voltage
            case "a":           // Current
            case "psi":
            case "kpa":
            case "bar":
                return "%.1f";  // 1 decimal

            default:
                return "%.2f";  // 2 decimals by default
        }
    }

    /**
     * Check if a custom PID is compatible with current vehicle
     *
     * @param pid Custom PID to check
     * @param vehicleMake Current vehicle make
     * @param vehicleModel Current vehicle model
     * @param vehicleYear Current vehicle year
     * @return true if PID matches vehicle or is universal
     */
    public static boolean isCompatible(CustomPid pid, String vehicleMake, String vehicleModel, int vehicleYear) {
        return pid.matchesVehicle(vehicleMake, vehicleModel, vehicleYear);
    }

    /**
     * Convert a raw OBD response to physical value using custom PID
     *
     * @param pid Custom PID with formula
     * @param bytes OBD response bytes
     * @return Converted physical value
     * @throws PidFormulaParser.FormulaException if formula evaluation fails
     */
    public static double evaluateResponse(CustomPid pid, int[] bytes) throws PidFormulaParser.FormulaException {
        return PidFormulaParser.evaluate(pid.getFormula(), bytes);
    }

    /**
     * Validate that a custom PID can be converted to PidDefinition
     *
     * @param pid Custom PID to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValid(CustomPid pid) {
        if (pid == null) return false;
        if (pid.getPidHex() == null || pid.getPidHex().isEmpty()) return false;
        if (pid.getFormula() == null || pid.getFormula().isEmpty()) return false;
        if (pid.getName() == null || pid.getName().isEmpty()) return false;

        // Check if PID hex is valid
        if (pid.getPidInt() == -1) return false;

        // Check if formula is valid
        return PidFormulaParser.isValidFormula(pid.getFormula());
    }
}
