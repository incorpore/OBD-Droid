package com.obddroid.ecu;

import com.obddroid.pvs.IndexedProcessVar;

/**
 * OBD Vehicle identification Item
 * The VID item does not contain any units, since it is all alphanumeric IDs
 *
 * @author root
 */
public class ObdVidItem
	extends IndexedProcessVar
{
	/**
	 *
	 */
	private static final long serialVersionUID = 955050909875054165L;
	public static final int FID_DESCRIPT = 0;
	public static final int FID_VALUE = 1;

	/**
	 * description of all relevant fields for VID item
	 */
	private static final String[] fields =
		{
			"Description",
			"Value"
		};

	@Override
	public String[] getFields()
	{
		return (fields);
	}

	/**
	 * Descriptions of mode 9 PIDs
	 */
	private static final String[] descriptions =
		{
	/* PID 0 */ "Supported PIDs",
    /* PID 1 */ "VIN Count",
    /* PID 2 */ "Vehicle ID Number",
    /* PID 3 */ "Cal ID Count",
    /* PID 4 */ "Calibration ID",
    /* PID 5 */ "Cal Version Count",
    /* PID 6 */ "Cal Version",
    /* PID 7 */ "IPT Count",
    /* PID 8 */ "IPT",
    /* PID 9 */ "Control System count",
    /* PID A */ "Control System ID",
		};

	/**
	 * Return description for Mode $09 PID
	 *
	 * @param pid
	 * @return description for selected PID
	 */
	public static String getPidDescription(int pid)
	{
		String result;
		try
		{
			result = descriptions[pid];
		} catch (Exception e)
		{
			// PID is not defined -> create dummy
			result = String.format("PID %02X", pid);
		}
		return (result);
	}
}
