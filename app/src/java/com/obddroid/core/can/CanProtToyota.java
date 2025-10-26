package com.obddroid.core.can;

import com.obddroid.core.ecu.Conversions;

/**
 * Toyota-specific CAN protocol implementation for monitoring vehicle telemetry.
 *
 * This protocol handler decodes proprietary CAN messages from Toyota and Lexus vehicles.
 * Messages include engine parameters, hybrid system data (where applicable), vehicle
 * dynamics, and climate control information.
 *
 * <p>Protocol Status: EXPERIMENTAL - Message IDs and parameter mappings may
 * vary across different Toyota/Lexus models, years, and platforms (TNGA, MC, etc.).
 * Validation recommended before production use.
 *
 * <p>Tested platforms: Toyota Camry, Prius, RAV4, Lexus IS
 *
 * @author Wal33D <aquataze@yahoo.com>
 */
public class CanProtToyota extends CanProt
{

	/**
	 * Toyota CAN message parameter mapping table.
	 *
	 * <p>Each entry defines how to extract a specific parameter from a CAN message:
	 * <ul>
	 *   <li>START - Byte offset within the CAN message payload</li>
	 *   <li>LEN - Parameter length in bytes</li>
	 *   <li>PARAM-TYPE - Data type (PT_HEX, PT_HEX_S16, etc.)</li>
	 *   <li>CONVERSION - Conversion function ID to apply to raw value</li>
	 *   <li>DEC - Number of decimal places for display</li>
	 *   <li>MSG_ID - CAN message ID this parameter belongs to</li>
	 * </ul>
	 */
	private static final int[][] MSG_PARAMETERS =
	/*  START,  LEN,   PARAM-TYPE   CONVERSION                       , DEC, MSG_ID    // REMARKS                   */
    /* ----------------------------------------------------------------------------------------------------------- */
		{
    /* MSG 0x2C4 - Engine control module */
			{0, 2, PT_HEX, Conversions.CNV_ID_RPM, 0, 0x2C4},   // Engine speed (RPM)
			{2, 1, PT_HEX, Conversions.CNV_ID_PERCENT, 1, 0x2C4},   // Throttle position (%)
			{3, 1, PT_HEX, Conversions.CNV_ID_TEMPERATURE, 0, 0x2C4},   // Intake air temperature (°C)
    /* MSG 0x3B7 - Vehicle dynamics */
			{0, 2, PT_HEX, Conversions.CNV_ID_SPEED_HIGHRES, 1, 0x3B7},   // Vehicle speed (km/h)
			{4, 2, PT_HEX_S16, Conversions.CNV_ID_ONETOONE, 2, 0x3B7},   // Steering angle (degrees)
    /* MSG 0x3BC - Engine temperature and pressure */
			{0, 1, PT_HEX, Conversions.CNV_ID_TEMPERATURE, 0, 0x3BC},   // Engine coolant temperature (°C)
			{1, 1, PT_HEX, Conversions.CNV_ID_ONETOONE, 0, 0x3BC},   // Engine oil pressure (kPa)
    /* MSG 0x4C1 - Fuel and emissions */
			{0, 2, PT_HEX, Conversions.CNV_ID_PERCENT, 1, 0x4C1},   // Fuel level (%)
			{2, 2, PT_HEX, Conversions.CNV_ID_ONETOONE, 2, 0x4C1},   // Instantaneous fuel consumption (L/100km)
    /* MSG 0x5A4 - Hybrid system (Prius, Camry Hybrid, etc.) */
			{0, 2, PT_HEX, Conversions.CNV_ID_ONETOONE, 1, 0x5A4},   // Battery state of charge (%)
			{2, 2, PT_HEX, Conversions.CNV_ID_ONETOONE, 0, 0x5A4},   // Electric motor RPM
			{4, 1, PT_HEX, Conversions.CNV_ID_TEMPERATURE, 0, 0x5A4},   // Battery temperature (°C)
    /* MSG 0x611 - VSC/Traction control */
			{0, 1, PT_HEX, Conversions.CNV_ID_ONETOONE, 0, 0x611},   // VSC system status
			{1, 1, PT_HEX, Conversions.CNV_ID_ONETOONE, 0, 0x611},   // Traction control status
		};

	/**
	 * Human-readable descriptions for each parameter defined in MSG_PARAMETERS.
	 *
	 * <p>Array indices correspond directly to the MSG_PARAMETERS array.
	 * Used for displaying parameter names in the UI.
	 */
	private static final String[] MSG_DESCRIPTORS =
		{
			"Engine RPM",
			"Throttle position",
			"Intake air temperature",
			"Vehicle speed",
			"Steering angle",
			"Engine coolant temperature",
			"Engine oil pressure",
			"Fuel level",
			"Fuel consumption",
			"Hybrid battery SOC",
			"Electric motor RPM",
			"Hybrid battery temperature",
			"VSC status",
			"Traction control status",
		};

	/**
	 * Returns the Toyota-specific CAN message parameter mapping table.
	 *
	 * <p>This table defines how to parse each parameter from incoming CAN messages,
	 * including byte offsets, data types, conversions, and message IDs.
	 *
	 * @return 2D array where each row defines one parameter's extraction rules
	 * @see #MSG_PARAMETERS for detailed structure documentation
	 */
	public int[][] getMsgParameters()
	{
		return (MSG_PARAMETERS);
	}

	/**
	 * Returns human-readable descriptions for each CAN message parameter.
	 *
	 * <p>These descriptions are displayed in the UI to identify what each
	 * parameter represents (e.g., "Engine RPM", "Vehicle speed").
	 *
	 * @return Array of parameter description strings, indexed to match MSG_PARAMETERS
	 */
	public String[] getMsgDescriptors()
	{
		return (MSG_DESCRIPTORS);
	}
}
