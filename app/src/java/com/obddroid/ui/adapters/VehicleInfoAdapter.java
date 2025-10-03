package com.obddroid.ui.adapters;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.obddroid.core.ecu.EcuDataPv;
import com.obddroid.api.nhtsa.VINDecoderService;
import com.obddroid.api.nhtsa.VehicleData;
import com.obddroid.core.pvs.PvList;
import com.obddroid.vehicle.VehicleManager;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;


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
		// Get data PV
		EcuDataPv currPv = (EcuDataPv) getItem(position);
		if (currPv == null)
			return super.getView(position, convertView, parent);

		// Get the value and check if it's a VIN
		Object value = currPv.get(EcuDataPv.FID_VALUE);
		String description = String.valueOf(currPv.get(EcuDataPv.FID_DESCRIPT));

		// Special handling for VIN - if it's showing dummy value, look for real VIN in PV list
		if (description != null && description.toLowerCase().contains("vehicle identification") &&
			value != null && ("0.0".equals(value.toString()) || "0x00000000".equals(value.toString())))
		{
			Log.d(TAG, "Dummy VIN detected, searching for real VIN in PV list");
			// Search through all PVs for a VIN with actual data
			for (Object obj : pvs.values()) {
				if (obj instanceof EcuDataPv) {
					EcuDataPv pv = (EcuDataPv) obj;
					String pvDesc = String.valueOf(pv.get(EcuDataPv.FID_DESCRIPT));
					Object pvValue = pv.get(EcuDataPv.FID_VALUE);
					if (pvDesc != null && pvDesc.toLowerCase().contains("vehicle identification") &&
						pvValue != null && pvValue.toString().length() == 17) {
						Log.d(TAG, "Found real VIN: " + pvValue);
						// Update current PV with real VIN
						currPv.put(EcuDataPv.FID_VALUE, pvValue);
						value = pvValue;
						break;
					}
				}
			}
		}

		// Check if this is a VIN item
		if (value != null && description != null && description.toLowerCase().contains("vehicle identification"))
		{
			String vin = value.toString().trim();
			// Only process if valid VIN length
			if (vin.length() == 17)
			{
				// Notify VehicleManager of the VIN - it will handle decoding and notifying all listeners
				VehicleManager vm = VehicleManager.getInstance();
				String currentVin = vm.getCurrentVIN();
				if (currentVin == null || !currentVin.equals(vin)) {
					Log.d(TAG, "Notifying VehicleManager of VIN: " + vin);
					vm.setVIN(vin);
				}

				// Try to decode the VIN locally for display in this adapter
				if (!decodedVins.containsKey(vin))
				{
					vinDecoder.decodeVIN(vin, new VINDecoderService.VINDecoderCallback()
					{
						@Override
						public void onSuccess(VehicleData vehicleData)
						{
							decodedVins.put(vin, vehicleData);
							// Refresh the view
							notifyDataSetChanged();
						}

						@Override
						public void onError(String error)
						{
							Log.w(TAG, "VIN decode error: " + error);
						}
					});
				}

				// If we have decoded data, update the view
				View view = super.getView(position, convertView, parent);
				VehicleData vehicleData = decodedVins.get(vin);
				if (vehicleData != null)
				{
					TextView tvValue = view.findViewById(android.R.id.text2);
					TextView tvUnits = view.findViewById(android.R.id.text1);
					updateVINDisplay(tvValue, tvUnits, vin, vehicleData);
				}
				return view;
			}
		}

		// For non-VIN items, use default rendering
		return super.getView(position, convertView, parent);
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
