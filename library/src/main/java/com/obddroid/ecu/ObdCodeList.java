package com.obddroid.ecu;

/**
 * List of all known OBD failure codes
 * This list is initialized by reading data files 'res/pcodes' and 'res/ucodes'
 *

 */
public class ObdCodeList
	extends EcuCodeList
{

	/**
	 *
	 */
	private static final long serialVersionUID = 2198654596294230437L;

	/** Creates a new instance of ObdCodeList */
	public ObdCodeList()
	{
		super("com.obddroid.ecu.prot.obd.res.codes");
	}

	/**
	 * Construct a new code list and initialize it with ressources files
	 *
	 * @param resourceBundleName name of used resource bundle
	 */
	public ObdCodeList(String resourceBundleName)
	{
		super(resourceBundleName);
	}

	@Override
	protected String getCode(Number value)
	{
		return ObdCodeItem.getPCode(value.intValue());
	}
}
