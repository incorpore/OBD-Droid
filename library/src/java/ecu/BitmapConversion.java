package ecu;

import com.obddroid.prot.Messages;

import java.util.Map;
import java.util.TreeMap;

/**
 * conversion of numeric values based on a Bitmap
 *

 */
public class BitmapConversion extends NumericConversion
{
	/** SerialVersion UID */
	private static final long serialVersionUID = -8498739122873083420L;
	/* the HashMap Data */
	private final TreeMap<Long,String> hashData = new TreeMap<Long,String>();

	/**
	 * create a new hash converter which is initialized with values from map data
	 * The map data needs to contain Bit position and the meaning of it
	 *
	 * @param data map data for conversions
	 */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public BitmapConversion(Map data)
	{
		hashData.putAll(data);
	}

	/**
	 * create a new hash converter which is initialized with avlues from an array
	 * of strings in the format "BitPos=value"
	 *
	 * @param initData initializer strings for conversions in the format "BitPos=value[;BitPos=value[...]]"
	 */
	public BitmapConversion(String[] initData)
	{
		initFromStrings(initData);
	}

	/**
	 * initialize hash map with values from an array of strings in the format "BitPos=value"
	 *
	 * @param initData initializer strings for conversions in the format "BitPos=value[;BitPos=value[...]]"
	 */
	private void initFromStrings(String[] initData)
	{
		Long key;
		String value;
		String[] data;
		// clear old hash data
		hashData.clear();

		// loop through all string entries ...
		for (String anInitData : initData)
		{
			data = anInitData.split(";");
			for (String aData : data)
			{
				// ... split key and value ...
				String[] words = aData.split("=");
				key = (long) (1 << Long.valueOf(words[0]));
				value = words[1];
				
				// attempt to translate ...
				String xlatKey = value;
				xlatKey = xlatKey.replaceAll("[ -]", "_").toLowerCase();
				value = Messages.getString(xlatKey, value);
				// debug log translated message
				log.finer(String.format("%s=%s", xlatKey, value));
				
				// ... and enter into hash map
				hashData.put(key, value);
			}
		}
	}

	public Number memToPhys(long value)
	{
		return value;
	}

	public Number physToMem(Number value)
	{
		return value;
	}

	@Override
	public String physToPhysFmtString(Number physVal, String format)
	{
		StringBuilder result = null;
		long val = physVal.longValue();

		for(Map.Entry<Long,String> item : hashData.entrySet())
		{
			// if this is NOT the first entry, then add a new line
			if (result == null)
				result = new StringBuilder();
			else
				result.append(System.lineSeparator());
			// now add the result
			result.append(String.format("%s  %s",
				((val & item.getKey()) != 0) ? "(*)" : "(  )",
				item.getValue()));
		}
		// if we haven't found a string representation, return numeric value
		if (result == null) result = new StringBuilder(super.physToPhysFmtString(physVal, format));
		return (result.toString());
	}
}
