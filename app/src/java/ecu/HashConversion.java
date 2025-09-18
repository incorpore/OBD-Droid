package ecu;

import prot.Messages;

import java.util.HashMap;
import java.util.Map;

/**
 * conversion of numeric values based on a hash map
 *

 */
public class HashConversion extends NumericConversion
{
	/**
	 *
	 */
	private static final long serialVersionUID = -1077047688974749271L;
	/* the HashMap Data */
	private final HashMap<Long, String> hashData = new HashMap<Long, String>();

	/**
	 * create a new hash converter which is initialized with values from map data
	 *
	 * @param data map data for conversions
	 */
	@SuppressWarnings({"rawtypes", "unchecked"})
	public HashConversion(Map data)
	{
		hashData.putAll(data);
	}

	/**
	 * create a new hash converter which is initialized with avlues from an array
	 * of strings in the format "key=value"
	 *
	 * @param initData initializer strings for conversions in the format "key=value[;key=value[...]]"
	 */
	public HashConversion(String[] initData)
	{
		initFromStrings(initData);
	}

	/**
	 * initialize hash map with values from an array of strings in the format "key=value"
	 *
	 * @param initData initializer strings for conversions in the format "key=value[;key=value[...]]"
	 */
	private void initFromStrings(String[] initData)
	{
		Long key;
		String value;
		String[] data;
		// clear old hash data
		hashData.clear();

		// loop through all entries ...
		for (String anInitData : initData)
		{
			data = anInitData.split(";");
			for (String aData : data)
			{
				// ... split key and value ...
				String[] words = aData.split("=");
				key = Long.valueOf(words[0]);
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
		String result = hashData.get(physVal.longValue());
		// if we haven't found a string representation, return numeric value
		if (result == null)
			result = "Unknown state: "+super.physToPhysFmtString(physVal, format);

		return (result);
	}

}
