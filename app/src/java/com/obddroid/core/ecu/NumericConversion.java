package com.obddroid.core.ecu;

import java.util.logging.Logger;

/**
 * Base class for numeric diagnostic data conversions
 *

 */
public abstract class NumericConversion implements Conversion
{
	/** fixed serial version id  */
	private static final long serialVersionUID = 5506104864792893549L;
	/** Logger object */
	static final Logger log = Logger.getLogger("data.ecu");

	/** physical units of data item */
	String units = "";

	@Override
	public String physToPhysFmtString(Number physVal, String format)
	{
		return String.format(format, physVal);
	}

	NumericConversion()
	{
	}

	/**
	 * return physical units of measurement
	 *
	 * @return physical units
	 */
	public String getUnits()
	{
		return units;
	}

	/**
	 * convert measurement item from storage format to physical value
	 *
	 * @param value       memory value
	 * @param numDecimals number of decimal for string formatting of numbers
	 * @return string representation of numeric value
	 */
	public String memToString(Number value, int numDecimals)
	{
		String fmt = "%." + numDecimals + "d";
		return physToPhysFmtString(memToPhys(value.longValue()), fmt);
	}

	/**
	 * convert measurement item from storage format to physical value
	 *
	 * @param value raw memory value to be converted
	 */
	public abstract Number memToPhys(long value);

	/**
	 * convert measurement item from physical value to raw storage format
	 *
	 * @param value physical value to be converted
	 */
	public abstract Number physToMem(Number value);

}
