package com.obddroid.ecu.gui.androbd;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This Activity appears as a dialog. It lists any paired devices and
 * devices detected in the area after discovery. When a device is chosen
 * by the user, the MAC address of the device is sent back to the parent
 * Activity in the result Intent.
 */
public class BtDeviceListActivity extends Activity
{
	// Debugging
	private static final String TAG = BtDeviceListActivity.class.getSimpleName();
	private static final Logger log = Logger.getLogger(TAG);
	
	// Return Intent extra
	public static final String EXTRA_DEVICE_ADDRESS = "device_address";

	// Member fields
	private BluetoothAdapter mBtAdapter;
	// Map to store device names and their MAC addresses
	private final Map<String, String> deviceAddressMap = new HashMap<>();
	private ModernDeviceAdapter modernAdapter;
	private LinearLayout emptyState;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		// Set theme
		setTheme(R.style.AppTheme);

		// Hide the title bar for this activity (must be before super.onCreate)
		requestWindowFeature(Window.FEATURE_NO_TITLE);

		super.onCreate(savedInstanceState);

		// Also hide action bar if present
		if (getActionBar() != null) {
			getActionBar().hide();
		}

		// Get preferences for fullscreen mode
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		boolean fullScreenMode = prefs.getBoolean(MainActivity.PREF_FULLSCREEN, false);

		// Apply fullscreen mode if enabled
		if (fullScreenMode) {
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		}

		// Set window background color
		getWindow().getDecorView().setBackgroundColor(Color.WHITE);

		// Set status bar color to black to match the header
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			getWindow().setStatusBarColor(Color.BLACK);
			// Also set navigation bar to black if supported
			getWindow().setNavigationBarColor(Color.BLACK);
		}

		// Set result CANCELED in case the user backs out
		setResult(Activity.RESULT_CANCELED);
		// Setup the window with modern layout
		setContentView(R.layout.device_list_modern);

		// Get the local Bluetooth adapter
		mBtAdapter = BluetoothAdapter.getDefaultAdapter();

		// Initialize modern adapter
		modernAdapter = new ModernDeviceAdapter(this);

		// Find and set up the ListView for paired devices
		ListView pairedListView = findViewById(R.id.paired_devices);
		pairedListView.setAdapter(modernAdapter);

		// Set up list selection handlers (always set them, not just when devices exist)
		pairedListView.setOnItemClickListener(mDeviceClickListener);
		pairedListView.setOnItemLongClickListener(mDeviceLongClickListener);

		// Get references to UI elements
		emptyState = findViewById(R.id.empty_state);
		ImageButton bluetoothSettingsButton = findViewById(R.id.btn_bluetooth_settings);
		ImageButton demoModeButton = findViewById(R.id.btn_demo_mode);
		Button settingsButton = findViewById(R.id.btn_open_settings);

		// Set up button listeners

		// Bluetooth settings button in header
		if (bluetoothSettingsButton != null) {
			bluetoothSettingsButton.setOnClickListener(v -> {
				Intent intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
				startActivity(intent);
			});
		}

		// Demo mode button
		if (demoModeButton != null) {
			demoModeButton.setOnClickListener(v -> {
				// Return demo mode as the selected "device"
				Intent intent = new Intent();
				intent.putExtra(EXTRA_DEVICE_ADDRESS, "DEMO_MODE");
				setResult(Activity.RESULT_OK, intent);
				finish();
			});
		}

		// Bluetooth settings button in empty state
		if (settingsButton != null) {
			settingsButton.setOnClickListener(v -> {
				Intent intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
				startActivity(intent);
			});
		}

		if(mBtAdapter == null || !mBtAdapter.isEnabled())
		{
			// Show empty state
			showEmptyState();
			return;
		}

		// Get a set of currently paired devices
		Set<BluetoothDevice> pairedDevices = mBtAdapter.getBondedDevices();

		// If there are paired devices, add each one to the ArrayAdapter
		if (pairedDevices.size() > 0)
		{
			// Hide empty state
			hideEmptyState();

			// Get the last connected device address from preferences
			String lastDeviceAddress = prefs.getString(MainActivity.PRESELECT.LAST_DEV_ADDRESS.toString(), null);
			BluetoothDevice lastDevice = null;

			// Set the last used device address on the adapter for visual indication
			modernAdapter.setLastUsedDeviceAddress(lastDeviceAddress);

			// First, find and add the last connected device if it exists
			if (lastDeviceAddress != null) {
				for (BluetoothDevice device : pairedDevices) {
					if (device.getAddress().equals(lastDeviceAddress)) {
						lastDevice = device;
						modernAdapter.addDevice(device);
						break;
					}
				}
			}

			// Then add all other devices
			for (BluetoothDevice device : pairedDevices)
			{
				// Skip if this is the last device (already added)
				if (lastDevice != null && device.getAddress().equals(lastDevice.getAddress())) {
					continue;
				}
				modernAdapter.addDevice(device);
			}
		} else
		{
			// Show empty state
			showEmptyState();
		}
	}
	
	// The on-click listener for all devices in the ListViews
	private final OnItemClickListener mDeviceClickListener = new OnItemClickListener()
	{
		public void onItemClick(AdapterView<?> av, View v, int position, long id)
		{
			// Get the device from the adapter
			final BluetoothDevice device = modernAdapter.getItem(position);

			if (device == null) {
				return;
			}

			// Get device info
			String deviceName = device.getName();
			if (deviceName == null || deviceName.isEmpty()) {
				deviceName = "Unknown Device";
			}
			final String address = device.getAddress();
			final String originalName = deviceName;

			// Get saved nickname if exists
			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(BtDeviceListActivity.this);
			String savedNickname = prefs.getString("device_nickname_" + address, "");
			final String[] currentNickname = {savedNickname}; // Use array to allow modification in inner class

			// Create custom layout for dialog
			LinearLayout mainLayout = new LinearLayout(BtDeviceListActivity.this);
			mainLayout.setOrientation(LinearLayout.VERTICAL);
			mainLayout.setPadding(60, 40, 60, 40);

			// Device Information Section
			LinearLayout infoContainer = new LinearLayout(BtDeviceListActivity.this);
			infoContainer.setOrientation(LinearLayout.VERTICAL);

			// Add nickname/name as first item (editable)
			TextView nicknameLabel = new TextView(BtDeviceListActivity.this);
			nicknameLabel.setText("Nickname");
			nicknameLabel.setTextSize(12);
			nicknameLabel.setTextColor(Color.parseColor("#757575"));
			nicknameLabel.setPadding(0, 0, 0, 5);
			infoContainer.addView(nicknameLabel);

			// Add device name/nickname as editable TextView
			final TextView nameDisplay = new TextView(BtDeviceListActivity.this);
			String displayName = !savedNickname.isEmpty() ? savedNickname : originalName;
			nameDisplay.setText(displayName);
			nameDisplay.setTextSize(16);
			nameDisplay.setTextColor(Color.parseColor("#2196F3")); // Material Blue for editable field
			nameDisplay.setTypeface(null, android.graphics.Typeface.BOLD);
			nameDisplay.setPadding(0, 0, 0, 0);

			// Add hint text below the name
			TextView hintText = new TextView(BtDeviceListActivity.this);
			hintText.setText("(Long press to edit)");
			hintText.setTextSize(10);
			hintText.setTextColor(Color.parseColor("#9E9E9E")); // Light grey
			hintText.setPadding(0, 2, 0, 0);

			infoContainer.addView(nameDisplay);
			infoContainer.addView(hintText);

			// Add each info item with better formatting
			if (!savedNickname.isEmpty()) {
				addInfoRow(infoContainer, "Original Name", originalName);
			}
			addInfoRow(infoContainer, "Device Type", getDeviceType(device));
			addInfoRow(infoContainer, "MAC Address", address);

			// Status with color coding
			TextView statusLabel = new TextView(BtDeviceListActivity.this);
			statusLabel.setText("Status");
			statusLabel.setTextSize(12);
			statusLabel.setTextColor(Color.parseColor("#757575"));
			statusLabel.setPadding(0, 15, 0, 5);
			infoContainer.addView(statusLabel);

			TextView statusValue = new TextView(BtDeviceListActivity.this);
			if (isLastUsedDevice(address)) {
				statusValue.setText("● Recently Connected");
				statusValue.setTextColor(Color.parseColor("#4CAF50")); // Green
			} else {
				statusValue.setText("● Ready to Connect");
				statusValue.setTextColor(Color.parseColor("#FF9800")); // Orange
			}
			statusValue.setTextSize(14);
			statusValue.setTypeface(null, android.graphics.Typeface.BOLD);
			infoContainer.addView(statusValue);

			mainLayout.addView(infoContainer);

			// Create and show dialog with custom view
			AlertDialog.Builder builder = new AlertDialog.Builder(BtDeviceListActivity.this);
			builder.setTitle("Device Details");
			builder.setView(mainLayout);

			// Make name editable on long press
			nameDisplay.setOnLongClickListener(new View.OnLongClickListener() {
				@Override
				public boolean onLongClick(View v) {
					// Show input dialog for nickname
					AlertDialog.Builder inputBuilder = new AlertDialog.Builder(BtDeviceListActivity.this);
					inputBuilder.setTitle("Edit Nickname");

					// Create container with padding for the EditText
					LinearLayout inputContainer = new LinearLayout(BtDeviceListActivity.this);
					inputContainer.setOrientation(LinearLayout.VERTICAL);
					inputContainer.setPadding(50, 20, 50, 20);

					final EditText input = new EditText(BtDeviceListActivity.this);
					input.setText(currentNickname[0]);
					input.setHint("e.g., My Car");
					input.setSingleLine(true);
					input.selectAll();

					inputContainer.addView(input);
					inputBuilder.setView(inputContainer);

					inputBuilder.setPositiveButton("Save", new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							String newNickname = input.getText().toString().trim();
							currentNickname[0] = newNickname;

							// Update display
							if (!newNickname.isEmpty()) {
								nameDisplay.setText(newNickname);
								prefs.edit().putString("device_nickname_" + address, newNickname).apply();
							} else {
								nameDisplay.setText(originalName);
								prefs.edit().remove("device_nickname_" + address).apply();
							}

							// Refresh the adapter
							modernAdapter.notifyDataSetChanged();
						}
					});

					inputBuilder.setNegativeButton("Cancel", null);
					inputBuilder.show();

					// Request focus and show keyboard
					input.postDelayed(new Runnable() {
						@Override
						public void run() {
							input.requestFocus();
						}
					}, 100);

					return true;
				}
			});

			// Add Connect button
			builder.setPositiveButton("Connect", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					// Cancel discovery because it's costly and we're about to connect
					if (mBtAdapter != null) {
						mBtAdapter.cancelDiscovery();
					}

					// Create the result Intent and include the MAC address
					Intent intent = new Intent();
					intent.putExtra(EXTRA_DEVICE_ADDRESS, address);

					// Set result and finish this Activity
					setResult(Activity.RESULT_OK, intent);
					log.log(Level.FINE, "Sending Result...");
					finish();
				}
			});

			// Add Cancel button
			builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					dialog.dismiss();
				}
			});

			// Show the dialog
			AlertDialog dialog = builder.create();
			dialog.show();
		}
	};

	// The long-click listener to show MAC address
	private final OnItemLongClickListener mDeviceLongClickListener = new OnItemLongClickListener()
	{
		public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id)
		{
			// Get the device from the adapter
			BluetoothDevice device = modernAdapter.getItem(position);

			if (device != null) {
				String address = device.getAddress();
				String name = device.getName() != null ? device.getName() : "Unknown Device";

				// Show device details in a toast
				Toast.makeText(BtDeviceListActivity.this,
					name + "\nMAC: " + address,
					Toast.LENGTH_LONG).show();
			}

			return true; // Consume the long click
		}
	};

	// Helper methods for empty state
	private void showEmptyState() {
		if (emptyState != null) {
			emptyState.setVisibility(View.VISIBLE);
		}
	}

	private void hideEmptyState() {
		if (emptyState != null) {
			emptyState.setVisibility(View.GONE);
		}
	}

	// Helper method to get device type
	private String getDeviceType(BluetoothDevice device) {
		String name = device.getName();
		if (name != null) {
			String nameLower = name.toLowerCase();
			if (nameLower.contains("obd") || nameLower.contains("elm327")) {
				return "OBD-II Adapter";
			} else if (nameLower.contains("veepeak")) {
				return "VeePeak Adapter";
			}
		}
		return "Bluetooth Device";
	}

	// Helper method to check if device is the last used one
	private boolean isLastUsedDevice(String address) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		String lastDeviceAddress = prefs.getString(MainActivity.PRESELECT.LAST_DEV_ADDRESS.toString(), null);
		return lastDeviceAddress != null && lastDeviceAddress.equals(address);
	}

	// Helper method to add info rows to the dialog
	private void addInfoRow(LinearLayout container, String label, String value) {
		TextView labelView = new TextView(this);
		labelView.setText(label);
		labelView.setTextSize(12);
		labelView.setTextColor(Color.parseColor("#757575"));
		labelView.setPadding(0, 15, 0, 5);
		container.addView(labelView);

		TextView valueView = new TextView(this);
		valueView.setText(value);
		valueView.setTextSize(14);
		valueView.setTextColor(Color.parseColor("#424242"));
		valueView.setPadding(0, 0, 0, 0);
		container.addView(valueView);
	}
}
