package ecu;

/**
 * Internal int-based conversion for hex display
 */
public class IntConversion
	extends NumericConversion
{
	/** uid */
	private static final long serialVersionUID = -2551205550381391025L;

	@Override
	public Number memToPhys(long value)
	{
		return value;
	}

	@Override
	public Number physToMem(Number value)
	{
		return value;
	}

	@Override
	public String physToPhysFmtString(Number physVal, String format)
	{
		long val = physVal.longValue();
		return String.format(format, val);
	}
}
