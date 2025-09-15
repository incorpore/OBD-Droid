package com.obddroid.ecu.gui.androbd;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.obddroid.ecu.EcuDataPv;
import com.obddroid.pvs.PvList;

import java.util.Collection;

/**
 * Adapter to display OBD TID items from a process variable list
 *

 */
public class TidItemAdapter extends ObdItemAdapter
{
    public TidItemAdapter(Context context, int resource, PvList pvs)
    {
        super(context, resource, pvs);
    }

    @Override
    public Collection getPreferredItems(PvList pvs)
    {
        return pvs.values();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent)
    {
        // get data PV
        EcuDataPv currPv = (EcuDataPv) getItem(position);

        if (convertView == null)
        {
            convertView = mInflater.inflate(R.layout.obd_item, parent, false);
        }

        // Show icon
        ImageView ivIcon = convertView.findViewById(R.id.obd_icon);
        ivIcon.setVisibility(View.VISIBLE);

        // Hide value
        TextView tvValue = convertView.findViewById(R.id.obd_value);
        tvValue.setVisibility(View.GONE);

        // set description
        TextView tvUnits = convertView.findViewById(R.id.obd_units);
        tvUnits.setText(String.valueOf(currPv.get(EcuDataPv.FID_DESCRIPT)));

        return convertView;
    }
}
