package com.obddroid.ecu.gui.androbd;

import android.content.Context;

import com.obddroid.pvs.PvList;

import java.util.Collection;

/**
 * Adapter to display OBD VID items from a process variable list
 *

 */
public class VidItemAdapter extends ObdItemAdapter
{
	public VidItemAdapter(Context context, int resource, PvList pvs)
	{
		super(context, resource, pvs);
	}

	@Override
	public Collection getPreferredItems(PvList pvs)
	{
		return pvs.values();
	}
}
