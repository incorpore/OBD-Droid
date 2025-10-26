package com.obddroid.can;

import com.obddroid.ecu.Conversions;

/**
 * GM-specific CAN protocol implementation for monitoring vehicle telemetry.
 *
 * This protocol handler decodes proprietary CAN messages from General Motors vehicles
 * using GM LAN (GMLAN) protocol. Supports monitoring of engine parameters, transmission
 * data, vehicle speed, and body control module information.
 *
 * <p>Protocol Status: EXPERIMENTAL - Message IDs and parameter mappings may
 * vary across different GM models, years, and platforms (GMT800, GMT900, etc.).
 * Validation recommended before production use.
 *
 * <p>Tested platforms: Chevrolet Silverado, GMC Sierra, Corvette C6
 *
 * @author Wal33D <aquataze@yahoo.com>
 */
public class CanProtGM extends CanProt
{

	/**
	 * GM CAN message parameter mapping table.
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
    /* MSG 0xC9 - Engine data 1 */
			{0, 2, PT_HEX, Conversions.CNV_ID_RPM, 0, 0xC9},   // Engine speed (RPM)
			{2, 2, PT_HEX, Conversions.CNV_ID_TEMPERATURE, 0, 0xC9},   // Engine coolant temperature (°C)
			{4, 2, PT_HEX, Conversions.CNV_ID_PERCENT, 1, 0xC9},   // Throttle position (%)
    /* MSG 0xF1 - Vehicle speed and odometer */
			{0, 2, PT_HEX, Conversions.CNV_ID_SPEED_HIGHRES, 1, 0xF1},   // Vehicle speed (km/h)
			{4, 4, PT_HEX, Conversions.CNV_ID_ONETOONE, 0, 0xF1},   // Odometer reading (km)
    /* MSG 0x1A4 - Transmission data */
			{0, 2, PT_HEX, Conversions.CNV_ID_TEMPERATURE, 0, 0x1A4},   // Transmission fluid temperature (°C)
			{2, 1, PT_HEX, Conversions.CNV_ID_ONETOONE, 0, 0x1A4},   // Current gear position
    /* MSG 0x135 - Fuel system */
			{0, 2, PT_HEX, Conversions.CNV_ID_PERCENT, 1, 0x135},   // Fuel level (%)
			{2, 2, PT_HEX, Conversions.CNV_ID_ONETOONE, 2, 0x135},   // Fuel flow rate (L/h)
    /* MSG 0x3E9 - Body control module */
			{0, 1, PT_HEX, Conversions.CNV_ID_ONETOONE, 0, 0x3E9},   // Battery voltage status
			{1, 1, PT_HEX, Conversions.CNV_ID_ONETOONE, 0, 0x3E9},   // Ignition key position
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
			"Engine coolant temperature",
			"Throttle position",
			"Vehicle speed",
			"Odometer",
			"Transmission temperature",
			"Gear position",
			"Fuel level",
			"Fuel flow rate",
			"Battery voltage",
			"Ignition position",
		};

	/**
	 * Returns the GM-specific CAN message parameter mapping table.
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
