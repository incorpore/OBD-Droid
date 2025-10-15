package com.obddroid.core.ecu;

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

	// Allow injection of database-backed implementation
	private static ObdCodeList databaseInstance = null;

	/** Creates a new instance of ObdCodeList */
	public ObdCodeList()
	{
		super("protocol.codes");
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

	/**
	 * Set a database-backed instance to use globally
	 * Call this during app initialization with DTCDatabaseCodeList
	 *
	 * @param instance Database-backed code list instance
	 */
	public static void setDatabaseInstance(ObdCodeList instance)
	{
		databaseInstance = instance;
	}

	/**
	 * Get the active code list instance (database if set, otherwise creates new)
	 *
	 * @return Active ObdCodeList instance
	 */
	public static ObdCodeList getInstance()
	{
		return databaseInstance != null ? databaseInstance : new ObdCodeList();
	}
}
