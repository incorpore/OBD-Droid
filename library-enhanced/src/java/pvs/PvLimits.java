package com.obddroid.pvs;

/**
 * Range check to allow checking a process var to be within certain range
 *

 */
public class PvLimits
{
	/** result codes */
	/** value is within specified range */
	private static final byte RC_WITHIN_RANGE = 0x00;
	/** value is above specified range */
	private static final byte RC_ABOVE_RANGE = 0x01;
	/** value is below specified range */
	private static final byte RC_BELOW_RANGE = 0x02;

	/** minimum limit for range check */
	@SuppressWarnings("rawtypes")
	private Comparable minValue;
	/** maximum limit for range check */
	@SuppressWarnings("rawtypes")
	private Comparable maxValue;

	/**
	 * Creates a new instance of PvLimits
	 */
	public PvLimits()
	{
	}

	/**
	 * Creates a new instance of PvLimits
	 *
	 * @param minVal minimum Limit for range check. Setting this value to NULL disables
	 *               the check for value above limit
	 * @param maxVal maximum Limit for range check. Setting this value to NULL disables
	 *               the check for value below limit
	 */
	@SuppressWarnings("rawtypes")
	public PvLimits(Comparable minVal, Comparable maxVal)
	{
		minValue = minVal;
		maxValue = maxVal;
	}

	/**
	 * Getter for property minValue.
	 *
	 * @return Value of property minValue.
	 */
	@SuppressWarnings("rawtypes")
	public Comparable getMinValue()
	{
		return this.minValue;
	}

	/**
	 * Setter for property minValue.
	 *
	 * @param minValue New value of property minValue.
	 */
	@SuppressWarnings("rawtypes")
	public void setMinValue(Comparable minValue)
	{
		this.minValue = minValue;
	}

	/**
	 * Getter for property maxValue.
	 *
	 * @return Value of property maxValue.
	 */
	@SuppressWarnings("rawtypes")
	public Comparable getMaxValue()
	{
		return this.maxValue;
	}

	/**
	 * Setter for property maxValue.
	 *
	 * @param maxValue New value of property maxValue.
	 */
	@SuppressWarnings("rawtypes")
	public void setMaxValue(Comparable maxValue)
	{
		this.maxValue = maxValue;
	}

	/**
	 * check a value against defined range This method may be called from a static
	 * context
	 *
	 * @param value    the value to be checked
	 * @param minLimit minimum Limit for range check.
	 * @param maxLimit maximum Limit for range check.
	 * @return bitmasked check result
	 * @see PvLimits#RC_WITHIN_RANGE
	 * @see PvLimits#RC_ABOVE_RANGE
	 * @see PvLimits#RC_BELOW_RANGE
	 */
	private static byte checkRange(Object value, Comparable<Object> minLimit,
	                               Comparable<Object> maxLimit)
	{
		byte retVal = RC_WITHIN_RANGE;
		// check Range Minimum
		if (minLimit != null && minLimit.compareTo(value) > 0)
			retVal |= RC_BELOW_RANGE;
		// check Range Maximum
		if (maxLimit != null && maxLimit.compareTo(value) < 0)
			retVal |= RC_ABOVE_RANGE;
		return (retVal);
	}

	/**
	 * check a value against range defined within class instance
	 *
	 * @param value the value to be checked
	 * @return bitmasked check result
	 * @see PvLimits#RC_WITHIN_RANGE
	 * @see PvLimits#RC_ABOVE_RANGE
	 * @see PvLimits#RC_BELOW_RANGE
	 */
	@SuppressWarnings("unchecked")
	public byte checkRange(Object value)
	{
		return (checkRange(value, minValue, maxValue));
	}

	/**
	 * return value limited against range defined within class instance
	 *
	 * @param value the value to be checked
	 * @return value limited to limits
	 */
	@SuppressWarnings("unchecked")
	public Object limitedValue(Object value)
	{
		Object result;
		switch (checkRange(value, minValue, maxValue))
		{
			case RC_BELOW_RANGE:
				result = minValue;
				break;

			case RC_ABOVE_RANGE:
				result = maxValue;
				break;

			default:
				result = value;
		}
		return result;
	}
}
