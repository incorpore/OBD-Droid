package com.obddroid.pvs;

/**
 * Process variable @see ProcessVar which allows indexed access
 * to attribute fields
 *
 
 */
public abstract class IndexedProcessVar extends ProcessVar
{

	/**
	 *
	 */
	private static final long serialVersionUID = 8478458496218575203L;

	/** return all available field names */
	public abstract String[] getFields();

	protected IndexedProcessVar()
	{
		String flds[] = getFields();
		for (int i = 0; i < flds.length; i++)
		{
			put(flds[i], null);
		}
	}

	/** indexed get for specified field id */
	public Object get(int fieldID)
	{
		return (get(getFields()[fieldID]));
	}

	/** indexed put for specified field id */
	public void put(int fieldID, Object newValue)
	{
		put(getFields()[fieldID], newValue);
	}

	/**
	 * get attribute of selected key
	 * overridden method to allow synchronized access
	 *
	 * @param fieldIndex index to key of attribute
	 * @return value of attribute
	 */
	public int getAsInt(int fieldIndex)
	{
		return (getAsInt(getFields()[fieldIndex]));
	}

	/**
	 * set attribute of selected key to selected value
	 * overridden method to allow notification of process var changes
	 *
	 * @param fieldIndex index to key of attribute
	 * @param value      value of attribute
	 */
	public void putAsInt(int fieldIndex, int value)
	{
		putAsInt(getFields()[fieldIndex], value);
	}

	/** indexed put for specified field id */
	public Object remove(int fieldID)
	{
		return (remove(getFields()[fieldID]));
	}
}
