package ecu;

import java.io.Serializable;

/**
 * interface for various Data conversion types
 *

 */
public interface Conversion extends Serializable
{
	/**
	 * convert measurement item from storage format to physical value
	 *
	 * @param value memory value
	 * @return physical value
	 */
	Number memToPhys(long value);

	/**
	 * convert measurement item from storage format to physical value
	 *
	 * @param value physical value
	 * @return memory value
	 */
	Number physToMem(Number value);

	/**
	 * convert a numerical physical value into a formatted string
	 *
	 * @param physVal  physical value
	 * @param format formatting pattern for text display
	 * @return formatted String
	 */
	String physToPhysFmtString(Number physVal, String format);

	/**
	 * convert measurement item from storage format to physical value
	 *
	 * @param value       memory value
	 * @param numDecimals number of decimal for string formatting of numbers
	 * @return string representation of numeric value
	 */
	String memToString(Number value, int numDecimals);

	/**
	 * return physical units of this conversion
	 *
	 * @return physical units of this conversion
	 */
	String getUnits();
}
