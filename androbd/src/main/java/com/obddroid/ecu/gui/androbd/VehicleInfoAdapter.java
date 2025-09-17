package com.obddroid.ecu.gui.androbd;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.obddroid.ecu.EcuDataPv;
import com.obddroid.ecu.gui.androbd.api.nhtsa.VINDecoderService;
import com.obddroid.ecu.gui.androbd.api.nhtsa.VehicleData;
import com.obddroid.pvs.PvList;

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

					// Only decode valid VINs (17 characters or partial VINs)
					if (vinString.length() >= 3)  // Minimum for WMI (World Manufacturer Identifier)
					{
						TextView tvValue = convertView.findViewById(R.id.obd_value);
						TextView tvUnits = convertView.findViewById(R.id.obd_units);

						// Check if we already have this VIN decoded in cache
						if (decodedVins.containsKey(vinString))
						{
							VehicleData vehicleData = decodedVins.get(vinString);
							updateVINDisplay(tvValue, tvUnits, vinString, vehicleData);
						}
						else
						{
							// Show VIN while decoding
							if (tvValue != null)
							{
								tvValue.setText(vinString + "\nDecoding...");
								tvValue.setMaxLines(2);
								tvValue.setSingleLine(false);
							}

							// Start async VIN decoding
							final View finalView = convertView;
							final String finalVin = vinString;

							vinDecoder.decodeVIN(vinString, new VINDecoderService.VINDecoderCallback() {
								@Override
								public void onSuccess(VehicleData vehicleData) {
									// Cache the result
									decodedVins.put(finalVin, vehicleData);

									// Update UI on main thread
									finalView.post(() -> {
										TextView value = finalView.findViewById(R.id.obd_value);
										TextView units = finalView.findViewById(R.id.obd_units);
										updateVINDisplay(value, units, finalVin, vehicleData);
									});

									Log.d(TAG, "VIN decoded: " + vehicleData.getDisplayName());
								}

								@Override
								public void onError(String error) {
									Log.e(TAG, "VIN decode error: " + error);

									// Show just the VIN on error
									finalView.post(() -> {
										TextView value = finalView.findViewById(R.id.obd_value);
										if (value != null) {
											value.setText(finalVin);
											value.setMaxLines(1);
										}
									});
								}
							});
						}
					}
					else
					{
						// Invalid VIN length, just display as-is
						TextView tvValue = convertView.findViewById(R.id.obd_value);
						if (tvValue != null)
						{
							tvValue.setText(vinString);
						}
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
