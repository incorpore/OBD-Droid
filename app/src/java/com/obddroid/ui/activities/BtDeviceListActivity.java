package com.obddroid.ui.activities;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import androidx.preference.PreferenceManager;
import android.provider.Settings;
import android.view.MenuItem;
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

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.obddroid.ui.adapters.ModernDeviceAdapter;
import com.obddroid.R;
import com.obddroid.utils.PermissionManager;
import com.obddroid.utils.SnackbarHelper;

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
public class BtDeviceListActivity extends AppCompatActivity
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
		if (getSupportActionBar() != null) {
			getSupportActionBar().hide();
		}

		// Get preferences for fullscreen mode
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		boolean fullScreenMode = prefs.getBoolean(MainActivity.PREF_FULLSCREEN, false);

		// Apply fullscreen mode if enabled using modern WindowInsetsController
		if (fullScreenMode) {
			WindowInsetsControllerCompat windowInsetsController = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
			if (windowInsetsController != null) {
				windowInsetsController.hide(WindowInsetsCompat.Type.systemBars());
				windowInsetsController.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
			}
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
				getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
			}
		}

		// Set window background color
		getWindow().getDecorView().setBackgroundColor(Color.WHITE);

		// Set status bar color to match the header
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			getWindow().setStatusBarColor(Color.parseColor("#212121"));
			// Also set navigation bar to match if supported
			getWindow().setNavigationBarColor(Color.parseColor("#212121"));
		}

		// Set result CANCELED in case the user backs out
		setResult(Activity.RESULT_CANCELED);
		// Setup the window with modern layout
		setContentView(R.layout.device_list_modern);

		// Get the local Bluetooth adapter
		BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
		mBtAdapter = bluetoothManager != null ? bluetoothManager.getAdapter() : null;

		// Check if we have Bluetooth permissions for Android 12+
		if (!PermissionManager.hasBluetoothPermissions(this)) {
			// Request permissions
			PermissionManager.requestBluetoothPermissions(this);
		}

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
		Set<BluetoothDevice> pairedDevices = null;
		try {
			// Android 12+ requires BLUETOOTH_CONNECT permission
			if (PermissionManager.hasBluetoothPermissions(this)) {
				@SuppressLint("MissingPermission")
				Set<BluetoothDevice> devices = mBtAdapter.getBondedDevices();
				pairedDevices = devices;
			} else {
				log.log(Level.WARNING, "No Bluetooth permissions to get bonded devices");
				showEmptyState();
				return;
			}
		} catch (SecurityException e) {
			log.log(Level.WARNING, "SecurityException getting bonded devices - missing BLUETOOTH_CONNECT", e);
			showEmptyState();
			return;
		}

		// If there are paired devices, add each one to the ArrayAdapter
		if (pairedDevices.size() > 0)
		{
			// Hide empty state
			hideEmptyState();

			// Get the last connected device address from preferences
			String lastDeviceAddress = prefs.getString("LAST_DEV_ADDRESS", null);
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
			final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(BtDeviceListActivity.this);
			String savedNickname = prefs.getString("device_nickname_" + address, "");
			final String[] currentNickname = {savedNickname}; // Use array to allow modification in inner class

			// Inflate custom layout for dialog
			View dialogView = getLayoutInflater().inflate(R.layout.dialog_device_details, null);

			// Get references to views
			final TextView nicknameDisplay = dialogView.findViewById(R.id.device_nickname);
			LinearLayout infoContainer = dialogView.findViewById(R.id.device_info_container);
			TextView statusIndicator = dialogView.findViewById(R.id.status_indicator);
			Button cancelButton = dialogView.findViewById(R.id.btn_cancel);
			Button connectButton = dialogView.findViewById(R.id.btn_connect);

			// Set nickname/name
			String displayName = !savedNickname.isEmpty() ? savedNickname : originalName;
			nicknameDisplay.setText(displayName);

			// Add device info rows
			if (!savedNickname.isEmpty()) {
				addInfoRow(infoContainer, "Original Name", originalName);
			}
			addInfoRow(infoContainer, "Device Type", getDeviceType(device));
			addInfoRow(infoContainer, "MAC Address", address);

			// Set status
			if (isLastUsedDevice(address)) {
				statusIndicator.setText("● Recently Connected");
				statusIndicator.setTextColor(Color.parseColor("#4CAF50")); // Green
			} else {
				statusIndicator.setText("● Ready to Connect");
				statusIndicator.setTextColor(Color.parseColor("#FF9800")); // Orange
			}

			// Create and configure dialog
			final AlertDialog dialog = new AlertDialog.Builder(BtDeviceListActivity.this)
					.setView(dialogView)
					.create();

			// Remove default background to show our rounded corners
			if (dialog.getWindow() != null) {
				dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
			}

			// Make name editable on long press
			nicknameDisplay.setOnLongClickListener(new View.OnLongClickListener() {
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
								nicknameDisplay.setText(newNickname);
								prefs.edit().putString("device_nickname_" + address, newNickname).apply();
							} else {
								nicknameDisplay.setText(originalName);
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

			// Set up button click handlers
			if (connectButton != null) {
				connectButton.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						// Cancel discovery because it's costly and we're about to connect
						if (mBtAdapter != null && PermissionManager.hasBluetoothPermissions(BtDeviceListActivity.this)) {
							try {
								@SuppressLint("MissingPermission")
								boolean cancelled = mBtAdapter.cancelDiscovery();
								log.log(Level.FINE, "Discovery cancelled: " + cancelled);
							} catch (SecurityException e) {
								log.log(Level.WARNING, "Cannot cancel discovery - missing BLUETOOTH_SCAN permission", e);
							}
						}

						// Create the result Intent and include the MAC address
						Intent intent = new Intent();
						intent.putExtra(EXTRA_DEVICE_ADDRESS, address);

						// Set result and finish this Activity
						setResult(Activity.RESULT_OK, intent);
						log.log(Level.FINE, "Sending Result...");
						dialog.dismiss();
						finish();
					}
				});
			}

			if (cancelButton != null) {
				cancelButton.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						dialog.dismiss();
					}
				});
			}

			// Show the dialog
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

				// Show device details in a snackbar
				SnackbarHelper.showInfo(BtDeviceListActivity.this,
					name + "\nMAC: " + address);
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
		String lastDeviceAddress = prefs.getString("LAST_DEV_ADDRESS", null);
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

	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);

		if (requestCode == PermissionManager.PERMISSION_REQUEST_BLUETOOTH) {
			if (PermissionManager.handlePermissionResult(requestCode, permissions, grantResults)) {
				// Permissions granted, refresh the device list
				recreate();
			} else {
				// Permissions denied
				SnackbarHelper.showWarning(this, "Bluetooth permissions are required to scan for devices");
				if (PermissionManager.shouldShowBluetoothRationale(this)) {
					PermissionManager.showBluetoothRationale(this);
				} else if (PermissionManager.isBluetoothPermissionPermanentlyDenied(this)) {
					PermissionManager.showSettingsDialog(this,
						"Bluetooth permission is required to connect to OBD adapters");
				}
			}
		}
	}
}
