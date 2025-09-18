package com.obddroid.ui.adapters;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.obddroid.core.ecu.EcuDataPv;
import com.obddroid.api.nhtsa.VINDecoderService;
import com.obddroid.api.nhtsa.VehicleData;
import com.obddroid.vehicle.VehicleManager;
import com.obddroid.core.pvs.PvList;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import com.obddroid.R;

/**
 * Adapter to display OBD VID items from a process variable list
 * Includes automatic VIN decoding using NHTSA API
 */
public class VehicleInfoAdapter extends ObdItemAdapter
{
	private static final String TAG = "VehicleInfoAdapter";

	// Cache for decoded VINs and their display views
	private final Map<String, VehicleData> decodedVins = new HashMap<>();
	private final VINDecoderService vinDecoder;

	public VehicleInfoAdapter(Context context, int resource, PvList pvs)
	{
		super(context, resource, pvs);
		vinDecoder = VINDecoderService.getInstance();
	}

	@Override
	public Collection getPreferredItems(PvList pvs)
	{
		return pvs.values();
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent)
	{
		// Get the base view from parent
		convertView = super.getView(position, convertView, parent);

		try {
			// Get data PV
			EcuDataPv currPv = (EcuDataPv) getItem(position);
			if (currPv == null) {
				return convertView;
			}

			// Check if this is the VIN field
			String description = String.valueOf(currPv.get(EcuDataPv.FID_DESCRIPT));
			if (description != null && description.toLowerCase().contains("vehicle identification"))
			{
				// Get the VIN value
				Object vinValue = currPv.get(EcuDataPv.FID_VALUE);
				if (vinValue != null)
				{
					String vinString = vinValue.toString().trim();

					// Just display the raw VIN - decoding is handled by the footer bar
					TextView tvValue = convertView.findViewById(R.id.obd_value);
					if (tvValue != null)
					{
						tvValue.setText(vinString);
						tvValue.setMaxLines(1);
						tvValue.setSingleLine(true);
					}

					// Notify VehicleManager about the VIN (for the footer bar)
					// Only for valid 17-character VINs
					if (vinString.length() == 17)
					{
						VehicleManager.getInstance().setVIN(vinString);
					}
				}
			}
		} catch (Exception e) {
			Log.e(TAG, "Error in getView", e);
		}

		return convertView;
	}

	/**
	 * Update the VIN display with decoded information
	 */
	private void updateVINDisplay(TextView tvValue, TextView tvUnits, String vin, VehicleData vehicleData)
	{
		if (tvValue == null || vehicleData == null) return;

		StringBuilder displayText = new StringBuilder();
		displayText.append(vin);

		// Add decoded vehicle information
		String displayName = vehicleData.getDisplayName();
		if (displayName != null && !displayName.isEmpty())
		{
			displayText.append("\n").append(displayName);
		}

		// Add body class if available and different from vehicle type
		if (vehicleData.bodyClass != null && !vehicleData.bodyClass.isEmpty()
				&& !vehicleData.bodyClass.equals("Not Applicable")
				&& !vehicleData.bodyClass.equals(vehicleData.vehicleType))
		{
			displayText.append("\n").append(vehicleData.bodyClass);
		}

		// Add engine info if available
		String engineDesc = vehicleData.getEngineDescription();
		if (engineDesc != null && !engineDesc.isEmpty())
		{
			displayText.append("\n").append(engineDesc);
		}

		// Add country if available
		if (vehicleData.plantCountry != null && !vehicleData.plantCountry.isEmpty()
				&& !vehicleData.plantCountry.equals("Not Applicable"))
		{
			displayText.append("\n").append(vehicleData.plantCountry);
		}

		tvValue.setText(displayText.toString());
		tvValue.setMaxLines(6);  // Allow multiple lines
		tvValue.setSingleLine(false);

		// Clear the units field since we're showing country in main text
		if (tvUnits != null)
		{
			tvUnits.setText("");
			tvUnits.setVisibility(View.GONE);
		}
	}
}
