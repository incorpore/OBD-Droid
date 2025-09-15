package com.obddroid.ecu.gui.androbd;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
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
		// Set theme based on night mode setting (must be before super.onCreate)
		setTheme(MainActivity.nightMode ? R.style.AppTheme_Dark : R.style.AppTheme);

		// Hide the title bar for this activity (must be before super.onCreate)
		requestWindowFeature(Window.FEATURE_NO_TITLE);

		super.onCreate(savedInstanceState);

		// Also hide action bar if present
		if (getActionBar() != null) {
			getActionBar().hide();
		}

		// Get preferences for fullscreen mode
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		boolean fullScreenMode = prefs.getBoolean(MainActivity.PREF_FULLSCREEN, true);

		// Apply fullscreen mode if enabled
		if (fullScreenMode) {
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		}

		// Set window background color based on night mode
		getWindow().getDecorView().setBackgroundColor(MainActivity.nightMode ? Color.BLACK : Color.WHITE);

		// Set status bar color to match theme
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			// For night mode, use black status bar, for day mode use a dark gray to match the header
			getWindow().setStatusBarColor(MainActivity.nightMode ? Color.BLACK : Color.parseColor("#212121"));
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
		ImageButton scanButton = findViewById(R.id.btn_scan);
		ImageButton bluetoothSettingsButton = findViewById(R.id.btn_bluetooth_settings);
		Button settingsButton = findViewById(R.id.btn_open_settings);

		// Set up button listeners
		if (scanButton != null) {
			scanButton.setOnClickListener(v -> scanForDevices());
		}

		// Bluetooth settings button in header
		if (bluetoothSettingsButton != null) {
			bluetoothSettingsButton.setOnClickListener(v -> {
				Intent intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
				startActivity(intent);
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
			// Cancel discovery because it's costly and we're about to connect
			if (mBtAdapter != null) {
				mBtAdapter.cancelDiscovery();
			}

			// Get the device from the adapter
			BluetoothDevice device = modernAdapter.getItem(position);

			if (device == null) {
				return;
			}

			// Get the MAC address
			String address = device.getAddress();

			// Create the result Intent and include the MAC address
			Intent intent = new Intent();
			intent.putExtra(EXTRA_DEVICE_ADDRESS, address);

			// Set result and finish this Activity
			setResult(Activity.RESULT_OK, intent);
			log.log(Level.FINE, "Sending Result...");
			finish();
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

	// Scan for devices (placeholder - can be implemented later)
	private void scanForDevices() {
		Toast.makeText(this, "Scanning for devices...", Toast.LENGTH_SHORT).show();
		// TODO: Implement device discovery
	}
}
