package com.obddroid.ecu;

import com.obddroid.common.UTF8Bundle;
import com.obddroid.prot.Messages;

import java.util.HashSet;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.Set;

/**
 * Vehicle fault code list
 *

 */
public class EcuCodeList
	implements Conversion
{
	private static final long serialVersionUID = 219865459629423028L;
	private final transient ResourceBundle codes;
	private transient int radix = 10;

	/**
	 * construct a new code list
	 */
	EcuCodeList()
	{
		this("prot.codes");
	}

	/**
	 * Construct a new code list and initialize it with ressources files
	 *
	 * @param resourceBundleName name of used resource bundle
	 */
	public EcuCodeList(String resourceBundleName)
	{
		codes = UTF8Bundle.getBundle(resourceBundleName);
	}

	/**
	 * Construct a new code list and initialize it with ressources files
	 *
	 * @param resourceBundleName name of used resource bundle
	 * @param idRadix    radix of numeric code id
	 */
	public EcuCodeList(String resourceBundleName, int idRadix)
	{
		this(resourceBundleName);
		radix = idRadix;
	}

	String getCode(Number value)
	{
		return(Long.toString(value.longValue(),radix));
	}

	public EcuCodeItem get(Number value)
	{
		EcuCodeItem result = null;
		if (codes != null)
		{
			String key = getCode(value);
			try
			{
				result = new EcuCodeItem(key, codes.getString(key));
			} catch (MissingResourceException e)
			{
				result = new EcuCodeItem(key,
				                         Messages.getString(
					                         "customer.specific.trouble.code.see.manual"));
			}
		}
		return result;
	}

	/**
	 * return all known values
	 * @return all known ressource values
	 */
	public Set<String> values()
	{
		Set<String> values = new HashSet<String>();
		for( String key : codes.keySet())
		{
			values.add(codes.getString(key));
		}
		return values;
	}

	@Override
	public String getUnits()
	{
		return "";
	}

	@Override
	public Number memToPhys(long value)
	{
		return (float) value;
	}

	@Override
	public String memToString(Number value, int numDecimals)
	{
		String fmt = "%." + numDecimals + "f";
		return physToPhysFmtString(memToPhys(value.longValue()), fmt);
	}

	@Override
	public Number physToMem(Number value)
	{
		return value;
	}

	@Override
	public String physToPhysFmtString(Number value, String format)
	{
		return (get(value).toString());
	}
}
