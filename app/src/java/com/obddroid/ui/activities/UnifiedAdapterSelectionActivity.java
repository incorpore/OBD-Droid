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
import android.graphics.Color;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.preference.PreferenceManager;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.obddroid.R;
import com.obddroid.services.CommService;
import com.obddroid.ui.adapters.ModernDeviceAdapter;
import com.obddroid.utils.PermissionManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public class UnifiedAdapterSelectionActivity extends AppCompatActivity {

    private static final String TAG = UnifiedAdapterSelectionActivity.class.getSimpleName();
    private static final Logger log = Logger.getLogger(TAG);

    public static final String EXTRA_ADAPTER_TYPE = "adapter_type";
    public static final String EXTRA_DEVICE_ADDRESS = "device_address";
    public static final String EXTRA_NETWORK_IP = "network_ip";
    public static final String EXTRA_NETWORK_PORT = "network_port";
    public static final String EXTRA_USB_PORT = "usb_port";

    private static final int MESSAGE_REFRESH = 101;
    private static final long REFRESH_TIMEOUT_MILLIS = 5000;

    public static UsbSerialPort selectedUsbPort = null;

    private enum AdapterTab {
        BLUETOOTH, USB, WIFI
    }

    private AdapterTab currentTab = AdapterTab.BLUETOOTH;

    private BluetoothAdapter mBtAdapter;
    private UsbManager mUsbManager;
    private ModernDeviceAdapter btAdapter;
    private ArrayAdapter<UsbSerialPort> usbAdapter;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    private final List<UsbSerialPort> usbEntries = new ArrayList<>();
    private final Map<String, String> deviceAddressMap = new HashMap<>();

    private View bluetoothContent;
    private View usbContent;
    private View networkContent;
    private LinearLayout emptyStateBluetooth;
    private LinearLayout emptyStateUsb;
    private TextView adapterTypeSubtitle;
    private TextView usbDeviceCount;

    private LinearLayout tabBluetooth;
    private LinearLayout tabUsb;
    private LinearLayout tabWifi;
    private ImageView tabBluetoothIcon;
    private ImageView tabUsbIcon;
    private ImageView tabWifiIcon;
    private TextView tabBluetoothLabel;
    private TextView tabUsbLabel;
    private TextView tabWifiLabel;
    private View tabBluetoothIndicator;
    private View tabUsbIndicator;
    private View tabWifiIndicator;

    private EditText networkIpInput;
    private EditText networkPortInput;
    private Button connectNetworkButton;

    @SuppressLint("HandlerLeak")
    private final Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == MESSAGE_REFRESH) {
                if (currentTab == AdapterTab.USB) {
                    refreshUsbDeviceList();
                }
                mHandler.sendEmptyMessageDelayed(MESSAGE_REFRESH, REFRESH_TIMEOUT_MILLIS);
            } else {
                super.handleMessage(msg);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.AppTheme);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean fullScreenMode = prefs.getBoolean(MainActivity.PREF_FULLSCREEN, false);

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

        getWindow().getDecorView().setBackgroundColor(Color.WHITE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(Color.parseColor("#212121"));
            getWindow().setNavigationBarColor(Color.parseColor("#212121"));
        }

        setResult(Activity.RESULT_CANCELED);
        setContentView(R.layout.adapter_selection_unified);

        initializeViews();
        initializeAdapters();
        setupTabListeners();
        setupHeaderButtons();

        switchToTab(AdapterTab.BLUETOOTH);
    }

    private void initializeViews() {
        bluetoothContent = findViewById(R.id.bluetooth_content);
        usbContent = findViewById(R.id.usb_content);
        networkContent = findViewById(R.id.network_content);
        emptyStateBluetooth = findViewById(R.id.empty_state_bluetooth);
        emptyStateUsb = findViewById(R.id.empty_state_usb);
        adapterTypeSubtitle = findViewById(R.id.adapter_type_subtitle);
        usbDeviceCount = findViewById(R.id.usb_device_count);

        tabBluetooth = findViewById(R.id.tab_bluetooth);
        tabUsb = findViewById(R.id.tab_usb);
        tabWifi = findViewById(R.id.tab_wifi);
        tabBluetoothIcon = findViewById(R.id.tab_bluetooth_icon);
        tabUsbIcon = findViewById(R.id.tab_usb_icon);
        tabWifiIcon = findViewById(R.id.tab_wifi_icon);
        tabBluetoothLabel = findViewById(R.id.tab_bluetooth_label);
        tabUsbLabel = findViewById(R.id.tab_usb_label);
        tabWifiLabel = findViewById(R.id.tab_wifi_label);
        tabBluetoothIndicator = findViewById(R.id.tab_bluetooth_indicator);
        tabUsbIndicator = findViewById(R.id.tab_usb_indicator);
        tabWifiIndicator = findViewById(R.id.tab_wifi_indicator);

        networkIpInput = findViewById(R.id.network_ip_address);
        networkPortInput = findViewById(R.id.network_port);
        connectNetworkButton = findViewById(R.id.btn_connect_network);
    }

    private void initializeAdapters() {
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBtAdapter = bluetoothManager != null ? bluetoothManager.getAdapter() : null;
        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        btAdapter = new ModernDeviceAdapter(this);
        ListView pairedListView = findViewById(R.id.paired_devices);
        pairedListView.setAdapter(btAdapter);
        pairedListView.setOnItemClickListener(mBtDeviceClickListener);
        pairedListView.setOnItemLongClickListener(mBtDeviceLongClickListener);

        ListView usbListView = findViewById(R.id.usb_devices);
        usbAdapter = new ArrayAdapter<UsbSerialPort>(this,
                android.R.layout.simple_list_item_2,
                usbEntries) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View row = convertView;
                if (row == null) {
                    LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    if (inflater != null) {
                        row = inflater.inflate(android.R.layout.simple_list_item_2, parent, false);
                    }
                }

                if (row != null) {
                    UsbSerialPort port = usbEntries.get(position);
                    UsbSerialDriver driver = port.getDriver();
                    UsbDevice device = driver.getDevice();

                    String title = String.format("USB: 0x%04x/0x%04x",
                            device.getVendorId(),
                            device.getProductId());
                    String subtitle = driver.getClass().getSimpleName();

                    TextView text1 = row.findViewById(android.R.id.text1);
                    TextView text2 = row.findViewById(android.R.id.text2);

                    if (text1 != null) text1.setText(title);
                    if (text2 != null) text2.setText(subtitle);
                }

                return row;
            }
        };
        usbListView.setAdapter(usbAdapter);
        usbListView.setOnItemClickListener(mUsbDeviceClickListener);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String savedIp = prefs.getString("DEVICE_ADDRESS", "192.168.0.10");
        int savedPort = prefs.getInt("DEVICE_PORT", 35000);
        networkIpInput.setText(savedIp);
        networkPortInput.setText(String.valueOf(savedPort));
    }

    private void setupTabListeners() {
        tabBluetooth.setOnClickListener(v -> switchToTab(AdapterTab.BLUETOOTH));
        tabUsb.setOnClickListener(v -> switchToTab(AdapterTab.USB));
        tabWifi.setOnClickListener(v -> switchToTab(AdapterTab.WIFI));

        connectNetworkButton.setOnClickListener(v -> {
            String ip = networkIpInput.getText().toString().trim();
            String portStr = networkPortInput.getText().toString().trim();

            if (ip.isEmpty()) {
                Toast.makeText(this, "Please enter IP address", Toast.LENGTH_SHORT).show();
                return;
            }
            if (portStr.isEmpty()) {
                Toast.makeText(this, "Please enter port", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                int port = Integer.parseInt(portStr);

                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
                prefs.edit()
                        .putString("DEVICE_ADDRESS", ip)
                        .putInt("DEVICE_PORT", port)
                        .apply();

                Intent intent = new Intent();
                intent.putExtra(EXTRA_ADAPTER_TYPE, CommService.MEDIUM.NETWORK.name());
                intent.putExtra(EXTRA_NETWORK_IP, ip);
                intent.putExtra(EXTRA_NETWORK_PORT, port);
                setResult(Activity.RESULT_OK, intent);
                finish();
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid port number", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupHeaderButtons() {
        ImageButton demoModeButton = findViewById(R.id.btn_demo_mode);
        ImageButton bluetoothSettingsButton = findViewById(R.id.btn_bluetooth_settings);
        Button openBtSettingsButton = findViewById(R.id.btn_open_bt_settings);

        if (demoModeButton != null) {
            demoModeButton.setOnClickListener(v -> {
                Intent intent = new Intent();
                intent.putExtra(EXTRA_ADAPTER_TYPE, "DEMO");
                intent.putExtra(EXTRA_DEVICE_ADDRESS, "DEMO_MODE");
                setResult(Activity.RESULT_OK, intent);
                finish();
            });
        }

        if (bluetoothSettingsButton != null) {
            bluetoothSettingsButton.setOnClickListener(v -> {
                Intent intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
                startActivity(intent);
            });
        }

        if (openBtSettingsButton != null) {
            openBtSettingsButton.setOnClickListener(v -> {
                Intent intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
                startActivity(intent);
            });
        }
    }

    private void switchToTab(AdapterTab tab) {
        currentTab = tab;

        bluetoothContent.setVisibility(tab == AdapterTab.BLUETOOTH ? View.VISIBLE : View.GONE);
        usbContent.setVisibility(tab == AdapterTab.USB ? View.VISIBLE : View.GONE);
        networkContent.setVisibility(tab == AdapterTab.WIFI ? View.VISIBLE : View.GONE);

        updateTabStyles();

        switch (tab) {
            case BLUETOOTH:
                adapterTypeSubtitle.setText("(Bluetooth Adapters)");
                loadBluetoothDevices();
                break;
            case USB:
                adapterTypeSubtitle.setText("(USB Adapters)");
                loadUsbDevices();
                break;
            case WIFI:
                adapterTypeSubtitle.setText("(WiFi/Network Adapters)");
                break;
        }
    }

    private void updateTabStyles() {
        int activeColor = Color.WHITE;
        int inactiveColor = Color.parseColor("#B0B0B0");

        tabBluetoothIcon.setColorFilter(currentTab == AdapterTab.BLUETOOTH ? activeColor : inactiveColor);
        tabUsbIcon.setColorFilter(currentTab == AdapterTab.USB ? activeColor : inactiveColor);
        tabWifiIcon.setColorFilter(currentTab == AdapterTab.WIFI ? activeColor : inactiveColor);

        tabBluetoothLabel.setTextColor(currentTab == AdapterTab.BLUETOOTH ? activeColor : inactiveColor);
        tabUsbLabel.setTextColor(currentTab == AdapterTab.USB ? activeColor : inactiveColor);
        tabWifiLabel.setTextColor(currentTab == AdapterTab.WIFI ? activeColor : inactiveColor);

        if (currentTab == AdapterTab.BLUETOOTH) {
            tabBluetoothLabel.setTypeface(null, android.graphics.Typeface.BOLD);
            tabUsbLabel.setTypeface(null, android.graphics.Typeface.NORMAL);
            tabWifiLabel.setTypeface(null, android.graphics.Typeface.NORMAL);
        } else if (currentTab == AdapterTab.USB) {
            tabBluetoothLabel.setTypeface(null, android.graphics.Typeface.NORMAL);
            tabUsbLabel.setTypeface(null, android.graphics.Typeface.BOLD);
            tabWifiLabel.setTypeface(null, android.graphics.Typeface.NORMAL);
        } else {
            tabBluetoothLabel.setTypeface(null, android.graphics.Typeface.NORMAL);
            tabUsbLabel.setTypeface(null, android.graphics.Typeface.NORMAL);
            tabWifiLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        }

        tabBluetoothIndicator.setBackgroundColor(currentTab == AdapterTab.BLUETOOTH ? activeColor : Color.TRANSPARENT);
        tabUsbIndicator.setBackgroundColor(currentTab == AdapterTab.USB ? activeColor : Color.TRANSPARENT);
        tabWifiIndicator.setBackgroundColor(currentTab == AdapterTab.WIFI ? activeColor : Color.TRANSPARENT);
    }

    private void loadBluetoothDevices() {
        if (!PermissionManager.hasBluetoothPermissions(this)) {
            PermissionManager.requestBluetoothPermissions(this);
            return;
        }

        if (mBtAdapter == null || !mBtAdapter.isEnabled()) {
            showEmptyState(AdapterTab.BLUETOOTH);
            return;
        }

        Set<BluetoothDevice> pairedDevices = null;
        try {
            if (PermissionManager.hasBluetoothPermissions(this)) {
                @SuppressLint("MissingPermission")
                Set<BluetoothDevice> devices = mBtAdapter.getBondedDevices();
                pairedDevices = devices;
            } else {
                showEmptyState(AdapterTab.BLUETOOTH);
                return;
            }
        } catch (SecurityException e) {
            log.log(Level.WARNING, "SecurityException getting bonded devices", e);
            showEmptyState(AdapterTab.BLUETOOTH);
            return;
        }

        if (pairedDevices.size() > 0) {
            hideEmptyState(AdapterTab.BLUETOOTH);

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            String lastDeviceAddress = prefs.getString("LAST_DEV_ADDRESS", null);
            btAdapter.setLastUsedDeviceAddress(lastDeviceAddress);

            BluetoothDevice lastDevice = null;
            if (lastDeviceAddress != null) {
                for (BluetoothDevice device : pairedDevices) {
                    if (device.getAddress().equals(lastDeviceAddress)) {
                        lastDevice = device;
                        btAdapter.addDevice(device);
                        break;
                    }
                }
            }

            for (BluetoothDevice device : pairedDevices) {
                if (lastDevice != null && device.getAddress().equals(lastDevice.getAddress())) {
                    continue;
                }
                btAdapter.addDevice(device);
            }
        } else {
            showEmptyState(AdapterTab.BLUETOOTH);
        }
    }

    private void loadUsbDevices() {
        mHandler.sendEmptyMessage(MESSAGE_REFRESH);
    }

    private void refreshUsbDeviceList() {
        executorService.execute(() -> {
            log.fine("Refreshing USB device list...");
            List<UsbSerialDriver> drivers = UsbSerialProber.getDefaultProber().findAllDrivers(mUsbManager);
            List<UsbSerialPort> result = new ArrayList<>();

            for (UsbSerialDriver driver : drivers) {
                List<UsbSerialPort> ports = driver.getPorts();
                log.fine(String.format("+ %s: %s port%s", driver, ports.size(), ports.size() == 1 ? "" : "s"));
                result.addAll(ports);
            }

            mHandler.post(() -> {
                usbEntries.clear();
                usbEntries.addAll(result);
                usbDeviceCount.setText(String.format("%d USB device(s) found", result.size()));
                usbAdapter.notifyDataSetChanged();

                if (result.size() > 0) {
                    hideEmptyState(AdapterTab.USB);
                } else {
                    showEmptyState(AdapterTab.USB);
                }

                log.fine("Done refreshing USB devices, " + usbEntries.size() + " entries found.");
            });
        });
    }

    private void showEmptyState(AdapterTab tab) {
        if (tab == AdapterTab.BLUETOOTH) {
            emptyStateBluetooth.setVisibility(View.VISIBLE);
        } else if (tab == AdapterTab.USB) {
            emptyStateUsb.setVisibility(View.VISIBLE);
        }
    }

    private void hideEmptyState(AdapterTab tab) {
        if (tab == AdapterTab.BLUETOOTH) {
            emptyStateBluetooth.setVisibility(View.GONE);
        } else if (tab == AdapterTab.USB) {
            emptyStateUsb.setVisibility(View.GONE);
        }
    }

    private final AdapterView.OnItemClickListener mBtDeviceClickListener = new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView<?> av, View v, int position, long id) {
            final BluetoothDevice device = btAdapter.getItem(position);
            if (device == null) return;

            String deviceName = device.getName();
            if (deviceName == null || deviceName.isEmpty()) {
                deviceName = "Unknown Device";
            }
            final String address = device.getAddress();
            final String originalName = deviceName;

            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(UnifiedAdapterSelectionActivity.this);
            String savedNickname = prefs.getString("device_nickname_" + address, "");
            final String[] currentNickname = {savedNickname};

            View dialogView = getLayoutInflater().inflate(R.layout.dialog_device_details, null);

            final TextView nicknameDisplay = dialogView.findViewById(R.id.device_nickname);
            LinearLayout infoContainer = dialogView.findViewById(R.id.device_info_container);
            TextView statusIndicator = dialogView.findViewById(R.id.status_indicator);
            Button cancelButton = dialogView.findViewById(R.id.btn_cancel);
            Button connectButton = dialogView.findViewById(R.id.btn_connect);

            String displayName = !savedNickname.isEmpty() ? savedNickname : originalName;
            nicknameDisplay.setText(displayName);

            if (!savedNickname.isEmpty()) {
                addInfoRow(infoContainer, "Original Name", originalName);
            }
            addInfoRow(infoContainer, "Device Type", getDeviceType(device));
            addInfoRow(infoContainer, "MAC Address", address);

            if (isLastUsedDevice(address)) {
                statusIndicator.setText("● Recently Connected");
                statusIndicator.setTextColor(Color.parseColor("#4CAF50"));
            } else {
                statusIndicator.setText("● Ready to Connect");
                statusIndicator.setTextColor(Color.parseColor("#FF9800"));
            }

            final AlertDialog dialog = new AlertDialog.Builder(UnifiedAdapterSelectionActivity.this)
                    .setView(dialogView)
                    .create();

            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            }

            nicknameDisplay.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    AlertDialog.Builder inputBuilder = new AlertDialog.Builder(UnifiedAdapterSelectionActivity.this);
                    inputBuilder.setTitle("Edit Nickname");

                    LinearLayout inputContainer = new LinearLayout(UnifiedAdapterSelectionActivity.this);
                    inputContainer.setOrientation(LinearLayout.VERTICAL);
                    inputContainer.setPadding(50, 20, 50, 20);

                    final EditText input = new EditText(UnifiedAdapterSelectionActivity.this);
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

                            if (!newNickname.isEmpty()) {
                                nicknameDisplay.setText(newNickname);
                                prefs.edit().putString("device_nickname_" + address, newNickname).apply();
                            } else {
                                nicknameDisplay.setText(originalName);
                                prefs.edit().remove("device_nickname_" + address).apply();
                            }

                            btAdapter.notifyDataSetChanged();
                        }
                    });

                    inputBuilder.setNegativeButton("Cancel", null);
                    inputBuilder.show();

                    input.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            input.requestFocus();
                        }
                    }, 100);

                    return true;
                }
            });

            if (connectButton != null) {
                connectButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (mBtAdapter != null && PermissionManager.hasBluetoothPermissions(UnifiedAdapterSelectionActivity.this)) {
                            try {
                                @SuppressLint("MissingPermission")
                                boolean cancelled = mBtAdapter.cancelDiscovery();
                                log.log(Level.FINE, "Discovery cancelled: " + cancelled);
                            } catch (SecurityException e) {
                                log.log(Level.WARNING, "Cannot cancel discovery", e);
                            }
                        }

                        Intent intent = new Intent();
                        intent.putExtra(EXTRA_ADAPTER_TYPE, CommService.MEDIUM.BLUETOOTH.name());
                        intent.putExtra(EXTRA_DEVICE_ADDRESS, address);
                        setResult(Activity.RESULT_OK, intent);
                        log.log(Level.FINE, "Sending Bluetooth result...");
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

            dialog.show();
        }
    };

    private final AdapterView.OnItemLongClickListener mBtDeviceLongClickListener = new AdapterView.OnItemLongClickListener() {
        public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
            BluetoothDevice device = btAdapter.getItem(position);

            if (device != null) {
                String address = device.getAddress();
                String name = device.getName() != null ? device.getName() : "Unknown Device";

                Toast.makeText(UnifiedAdapterSelectionActivity.this,
                        name + "\nMAC: " + address,
                        Toast.LENGTH_LONG).show();
            }

            return true;
        }
    };

    private final AdapterView.OnItemClickListener mUsbDeviceClickListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            log.fine("USB device clicked at position " + position);
            if (position >= usbEntries.size()) {
                log.warning("Illegal position.");
                return;
            }

            selectedUsbPort = usbEntries.get(position);

            Intent intent = new Intent();
            intent.putExtra(EXTRA_ADAPTER_TYPE, CommService.MEDIUM.USB.name());
            setResult(Activity.RESULT_OK, intent);
            log.fine("Sending USB result...");
            finish();
        }
    };

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

    private boolean isLastUsedDevice(String address) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String lastDeviceAddress = prefs.getString("LAST_DEV_ADDRESS", null);
        return lastDeviceAddress != null && lastDeviceAddress.equals(address);
    }

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
    protected void onResume() {
        super.onResume();
        if (currentTab == AdapterTab.USB) {
            mHandler.sendEmptyMessage(MESSAGE_REFRESH);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mHandler.removeMessages(MESSAGE_REFRESH);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executorService.shutdown();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PermissionManager.PERMISSION_REQUEST_BLUETOOTH) {
            if (PermissionManager.handlePermissionResult(requestCode, permissions, grantResults)) {
                recreate();
            } else {
                Toast.makeText(this, "Bluetooth permissions are required to scan for devices", Toast.LENGTH_LONG).show();
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
