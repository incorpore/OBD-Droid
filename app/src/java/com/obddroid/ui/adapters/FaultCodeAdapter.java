package com.obddroid.ui.adapters;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.obddroid.ecu.EcuCodeItem;
import com.obddroid.obd.ObdProt;
import com.obddroid.common.ProcessVariables.IndexedProcessVar;
import com.obddroid.common.ProcessVariables.PvList;

import java.util.Collection;
import java.util.Objects;

import com.obddroid.R;

/**
 * Adapter to display OBD Diagnostic Fault Codes with enhanced UI
 *
 */
public class FaultCodeAdapter extends ObdItemAdapter
{
	// Color constants for severity levels
	private static final int COLOR_ERROR = 0xFFD32F2F;      // Red
	private static final int COLOR_WARNING = 0xFFFFA726;    // Orange
	private static final int COLOR_PENDING = 0xFF42A5F5;    // Blue
	private static final int COLOR_DEFAULT = 0xFF757575;    // Gray

	public FaultCodeAdapter(Context context, int resource, PvList pvs)
	{
		super(context, resource, pvs);
	}

	@Override
	@SuppressWarnings("unchecked") // ProcessVar extends raw HashMap - values are always Objects in PvList
	public Collection<Object> getPreferredItems(PvList pvs)
	{
		// Filter out P0000 (code key 0) - it's a placeholder for "no codes" and not a real fault code
		java.util.List<Object> filteredItems = new java.util.ArrayList<>();
		for (Object item : pvs.values())
		{
			if (item instanceof IndexedProcessVar)
			{
				IndexedProcessVar pv = (IndexedProcessVar) item;
				Object keyObj = pv.get(EcuCodeItem.FID_CODE);
				// Skip P0000 which has key 0 - this is just a placeholder message
				if (keyObj != null)
				{
					String code = String.valueOf(keyObj);
					if (!code.equals("P0000") && !code.equals("0"))
					{
						filteredItems.add(item);
					}
				}
			}
		}
		return filteredItems;
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
			v = mInflater.inflate(R.layout.fault_code_item, parent, false);
		}

		// Get references to all views
		View severityIndicator = v.findViewById(R.id.severity_indicator);
		ImageView faultIcon = v.findViewById(R.id.fault_icon);
		TextView tvCode = v.findViewById(R.id.fault_code);
		TextView tvDescription = v.findViewById(R.id.fault_description);
		TextView tvTypeBadge = v.findViewById(R.id.fault_type_badge);
		TextView tvSeverityBadge = v.findViewById(R.id.fault_severity_badge);
		TextView tvSystem = v.findViewById(R.id.fault_system);
		TextView tvFreezeFrame = v.findViewById(R.id.fault_freeze_frame_indicator);

		// Set fault code
		String code = String.valueOf(Objects.requireNonNull(currPv).get(EcuCodeItem.FID_CODE));
		tvCode.setText(code);

		// Set description
		tvDescription.setText(String.valueOf(currPv.get(EcuCodeItem.FID_DESCRIPT)));

		// Determine severity and update UI accordingly
		int severityColor = COLOR_DEFAULT;
		int iconRes = R.drawable.ic_fault_warning;
		String severityText = "STORED";

		try
		{
			Integer svc = (Integer)currPv.get(EcuCodeItem.FID_STATUS);
			if (svc != null) {
				switch(svc)
				{
					case ObdProt.OBD_SVC_PENDINGCODES:
						severityColor = COLOR_PENDING;
						iconRes = R.drawable.ic_fault_pending;
						severityText = "PENDING";
						break;

					case ObdProt.OBD_SVC_PERMACODES:
						severityColor = COLOR_ERROR;
						iconRes = R.drawable.ic_fault_error;
						severityText = "PERMANENT";
						break;

					default:
						severityColor = COLOR_WARNING;
						iconRes = R.drawable.ic_fault_warning;
						severityText = "ACTIVE";
						break;
				}
			} else {
				// FID_STATUS not set - use default
				android.util.Log.w("FaultCodeAdapter", "FID_STATUS not set for code: " + code);
			}
		}
		catch(Exception ex) {
			android.util.Log.e("FaultCodeAdapter", "Error reading fault code status for: " + code, ex);
		}

		// Update severity indicator bar
		severityIndicator.setBackgroundColor(severityColor);

		// Update icon
		faultIcon.setImageResource(iconRes);

		// Update severity badge
		tvSeverityBadge.setText(severityText);
		GradientDrawable badgeBackground = new GradientDrawable();
		badgeBackground.setColor(severityColor);
		badgeBackground.setCornerRadius(8);
		tvSeverityBadge.setBackground(badgeBackground);

		// Determine code type and system from code prefix
		String codeType = "GENERIC";
		String system = "System: Unknown";

		if (code.startsWith("P0") || code.startsWith("P2"))
		{
			codeType = "GENERIC";
			system = determineSystemFromCode(code);
		}
		else if (code.startsWith("P1") || code.startsWith("P3"))
		{
			codeType = "MANUFACTURER";
			system = "System: Manufacturer Specific";
		}
		else if (code.startsWith("B"))
		{
			codeType = "BODY";
			system = "System: Body";
		}
		else if (code.startsWith("C"))
		{
			codeType = "CHASSIS";
			system = "System: Chassis";
		}
		else if (code.startsWith("U"))
		{
			codeType = "NETWORK";
			system = "System: Network";
		}

		tvTypeBadge.setText(codeType);
		tvSystem.setText(system);

		// Check for freeze frame data availability
		// This is a placeholder - you would check actual freeze frame availability
		boolean hasFreezeFrame = position % 2 == 0; // Example: show for even positions
		tvFreezeFrame.setVisibility(hasFreezeFrame ? View.VISIBLE : View.GONE);

		return v;
	}

	/**
	 * Determine the system category based on the fault code
	 */
	private String determineSystemFromCode(String code)
	{
		if (code.startsWith("P00") || code.startsWith("P01") || code.startsWith("P02"))
		{
			return "System: Fuel and Air Metering";
		}
		else if (code.startsWith("P03"))
		{
			return "System: Ignition System";
		}
		else if (code.startsWith("P04"))
		{
			return "System: Emission Controls";
		}
		else if (code.startsWith("P05"))
		{
			return "System: Speed and Idle Control";
		}
		else if (code.startsWith("P06"))
		{
			return "System: Computer and Output";
		}
		else if (code.startsWith("P07") || code.startsWith("P08") || code.startsWith("P09"))
		{
			return "System: Transmission";
		}
		else if (code.startsWith("P2"))
		{
			return "System: Fuel/Air or Emissions";
		}
		else
		{
			return "System: Engine Control";
		}
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
