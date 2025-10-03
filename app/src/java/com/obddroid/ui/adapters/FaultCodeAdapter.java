package com.obddroid.ui.adapters;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.obddroid.core.ecu.EcuCodeItem;
import com.obddroid.core.obd.ObdProt;
import com.obddroid.core.pvs.IndexedProcessVar;
import com.obddroid.core.pvs.PvList;

import java.util.Collection;
import java.util.Objects;

import com.obddroid.R;

/**
 * Adapter to display OBD Diagnostic Fault Codes
 *
 */
public class FaultCodeAdapter extends ObdItemAdapter
{
	public FaultCodeAdapter(Context context, int resource, PvList pvs)
	{
		super(context, resource, pvs);
	}

	@Override
	@SuppressWarnings("unchecked") // ProcessVar extends raw HashMap - values are always Objects in PvList
	public Collection<Object> getPreferredItems(PvList pvs)
	{
		return pvs.values();
	}

	/* (non-Javadoc)
	 * @see com.obddroid.adapters.ObdItemAdapter#getView(int, android.view.View, android.view.ViewGroup)
	 */
	@Override
	public View getView(int position, View v, ViewGroup parent)
	{
		// get data PV
		IndexedProcessVar currPv = (IndexedProcessVar) getItem(position);

		if (v == null)
		{
			v = mInflater.inflate(R.layout.obd_item, parent, false);
		}

		// Show icon
		ImageView ivIcon = v.findViewById(R.id.obd_icon);
		ivIcon.setImageResource(android.R.drawable.ic_menu_myplaces);
		try
		{
			// Show icon based on code status (pending/permanent/normal)
			Integer svc = (Integer)currPv.get(EcuCodeItem.FID_STATUS);
			switch(svc)
			{
				case ObdProt.OBD_SVC_PENDINGCODES:
					ivIcon.setImageResource(android.R.drawable.ic_menu_recent_history);
					break;

				case ObdProt.OBD_SVC_PERMACODES:
					ivIcon.setImageResource(android.R.drawable.ic_dialog_alert);
					break;
			}
		}
		catch(Exception ex) { /* ignore */ }

		ivIcon.setVisibility(View.VISIBLE);

		TextView tvDescr = v.findViewById(R.id.obd_label);
		TextView tvValue = v.findViewById(R.id.obd_units);

		tvValue.setText(String.valueOf(Objects.requireNonNull(currPv).get(EcuCodeItem.FID_CODE)));
		tvDescr.setText(String.valueOf(currPv.get(EcuCodeItem.FID_DESCRIPT)));

		return v;
	}

	/* (non-Javadoc)
	 * @see com.obddroid.adapters.ObdItemAdapter#getView(int, android.view.View, android.view.ViewGroup)
	 */
	@Override
	public View getDropDownView(int position, View v, ViewGroup parent)
	{
		return getView(position, v, parent);
	}
}
