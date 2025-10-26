package com.obddroid.core.can;

import com.obddroid.core.ecu.Conversions;

/**
 * Ford-specific CAN protocol implementation for monitoring vehicle telemetry.
 *
 * This protocol handler decodes proprietary CAN messages from Ford vehicles
 * (tested on Ford Focus 1.8 TDI). Messages include engine parameters, vehicle
 * speed, coolant temperature, and stability control status.
 *
 * <p>Protocol Status: EXPERIMENTAL - Message IDs and parameter mappings may
 * vary across different Ford models and years. Validation recommended before
 * production use.
 *
 * @author Wal33D <aquataze@yahoo.com>
 */
public class CanProtFord extends CanProt
{

	/**
	 * Ford CAN message parameter mapping table.
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
    /* MSG 0x25 - Engine performance metrics */
			{2, 4, PT_HEX, Conversions.CNV_ID_RPM, 0, 0x25},   // Engine speed (RPM)
			{6, 4, PT_HEX_S16, Conversions.CNV_ID_RATIO, 1, 0x25},   // Engine speed governor adjustment (%)
			{10, 2, PT_HEX, Conversions.CNV_ID_PERCENT, 1, 0x25},   // Accelerator pedal position (%)
    /* MSG 0x02 - Vehicle motion */
			{2, 4, PT_HEX, Conversions.CNV_ID_SPEED_HIGHRES, 1, 0x02},   // Vehicle speed (km/h, high resolution)
    /* MSG 0x10 - Engine temperature */
			{2, 2, PT_HEX, Conversions.CNV_ID_TEMPERATURE, 1, 0x10},   // Engine coolant temperature (°C)
    /* MSG 0x14 - Electronic stability control */
			{2, 2, PT_HEX, Conversions.CNV_ID_ONETOONE, 0, 0x14},   // ESP/ASR system status code
			{4, 2, PT_HEX, Conversions.CNV_ID_TORQUE, 1, 0x14},   // ESP torque reduction ratio (%)
    /* MSG 0x12 - Engine warmup state */
			{2, 2, PT_HEX, Conversions.CNV_ID_TORQUE, 1, 0x12},   // Engine warmup enrichment factor
    /* MSG 0x30 - Ignition state */
			{2, 2, PT_HEX, Conversions.CNV_ID_ONETOONE, 0, 0x30},   // T15 ignition status (terminal 15)
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
			"Engine speed regulator",
			"Accelerator pedal position",
			"Vehicle speed",
			"Engine coolant temperature",
			"ESP/ASR system status",
			"ESP torque reduction",
			"Engine warmup enrichment",
			"Ignition T15 status",
		};

	/**
	 * Returns the Ford-specific CAN message parameter mapping table.
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
