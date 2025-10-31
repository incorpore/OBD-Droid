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
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.preference.PreferenceManager;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.obddroid.R;
import com.obddroid.obd.ElmProt;
import com.obddroid.services.CommService;
import com.obddroid.ui.adapters.DeviceAdapter;
import com.obddroid.utils.PermissionManager;
import com.obddroid.utils.SnackbarHelper;

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
    private DeviceAdapter btAdapter;
    private DeviceAdapter usbAdapter;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    private final List<DeviceAdapter.DeviceInfo> usbEntries = new ArrayList<>();
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
    private ImageButton adapterSettingsButton;

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
            getWindow().setNavigationBarColor(androidx.core.content.ContextCompat.getColor(this, R.color.background_secondary));
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

        btAdapter = new DeviceAdapter(this);
        ListView pairedListView = findViewById(R.id.paired_devices);
        pairedListView.setAdapter(btAdapter);
        pairedListView.setOnItemClickListener(mBtDeviceClickListener);
        pairedListView.setOnItemLongClickListener(mBtDeviceLongClickListener);

        ListView usbListView = findViewById(R.id.usb_devices);
        usbAdapter = new DeviceAdapter(this, usbEntries);
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
                SnackbarHelper.showError(this, "Please enter IP address", SnackbarHelper.Duration.SHORT);
                return;
            }
            if (portStr.isEmpty()) {
                SnackbarHelper.showError(this, "Please enter port", SnackbarHelper.Duration.SHORT);
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
                SnackbarHelper.showError(this, "Invalid port number", SnackbarHelper.Duration.SHORT);
            }
        });
    }

    private void setupHeaderButtons() {
        ImageButton demoModeButton = findViewById(R.id.btn_demo_mode);
        adapterSettingsButton = findViewById(R.id.btn_adapter_settings);
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

        // Settings button click handler is set dynamically in updateTabStyles()

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

        // Update settings button based on current tab
        if (adapterSettingsButton != null) {
            adapterSettingsButton.setOnClickListener(v -> {
                switch (currentTab) {
                    case BLUETOOTH:
                        showBluetoothSettingsDialog();
                        break;
                    case USB:
                        showUsbSettingsDialog();
                        break;
                    case WIFI:
                        showWifiSettingsDialog();
                        break;
                }
            });
        }
    }

    private void showBluetoothSettingsDialog() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean secureConnection = prefs.getBoolean("bt_secure_connection", false);
        int protocolIndex = Integer.parseInt(prefs.getString("protocol", "0"));

        View dialogView = getLayoutInflater().inflate(android.R.layout.select_dialog_multichoice, null);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Bluetooth Settings");

        // Create layout for settings
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        // Permission status and request button
        LinearLayout permissionSection = new LinearLayout(this);
        permissionSection.setOrientation(LinearLayout.HORIZONTAL);
        permissionSection.setPadding(0, 0, 0, 20);

        TextView permissionStatus = new TextView(this);
        boolean hasPermission = PermissionManager.hasBluetoothPermissions(this);
        permissionStatus.setText(hasPermission ? "✓ Bluetooth permission granted" : "⚠ Bluetooth permission needed");
        permissionStatus.setTextSize(12);
        permissionStatus.setTextColor(hasPermission ? Color.parseColor("#4CAF50") : Color.parseColor("#FF9800"));
        permissionStatus.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        permissionSection.addView(permissionStatus);

        if (!hasPermission) {
            Button requestPermButton = new Button(this);
            requestPermButton.setText("Grant");
            requestPermButton.setTextSize(11);
            requestPermButton.setPadding(20, 10, 20, 10);
            requestPermButton.setOnClickListener(v -> {
                PermissionManager.requestBluetoothPermissions(this);
            });
            permissionSection.addView(requestPermButton);
        }
        layout.addView(permissionSection);

        // System Bluetooth settings button
        Button systemBtButton = new Button(this);
        systemBtButton.setText("Open System Bluetooth Settings");
        systemBtButton.setTextSize(12);
        systemBtButton.setPadding(20, 15, 20, 15);
        systemBtButton.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
            startActivity(intent);
        });
        layout.addView(systemBtButton);

        // Divider
        View divider = new View(this);
        divider.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2));
        divider.setBackgroundColor(Color.parseColor("#E0E0E0"));
        LinearLayout.LayoutParams dividerParams = (LinearLayout.LayoutParams) divider.getLayoutParams();
        dividerParams.setMargins(0, 20, 0, 20);
        divider.setLayoutParams(dividerParams);
        layout.addView(divider);

        // Secure connection checkbox
        CheckBox secureCheckbox = new CheckBox(this);
        secureCheckbox.setText("Secure Connection");
        secureCheckbox.setChecked(secureConnection);
        layout.addView(secureCheckbox);

        // Protocol selection
        TextView protocolLabel = new TextView(this);
        protocolLabel.setText("OBD Protocol");
        protocolLabel.setPadding(0, 30, 0, 10);
        protocolLabel.setTextSize(14);
        layout.addView(protocolLabel);

        Spinner protocolSpinner = new Spinner(this);
        ElmProt.PROT[] protocols = ElmProt.PROT.values();
        String[] protocolNames = new String[protocols.length];
        for (int i = 0; i < protocols.length; i++) {
            protocolNames[i] = protocols[i].toString();
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, protocolNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        protocolSpinner.setAdapter(adapter);
        protocolSpinner.setSelection(protocolIndex);
        layout.addView(protocolSpinner);

        builder.setView(layout);
        builder.setPositiveButton("Save", (dialog, which) -> {
            prefs.edit()
                .putBoolean("bt_secure_connection", secureCheckbox.isChecked())
                .putString("protocol", String.valueOf(protocolSpinner.getSelectedItemPosition()))
                .apply();
            SnackbarHelper.showSuccess(this, "Bluetooth settings saved", SnackbarHelper.Duration.SHORT);
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showUsbSettingsDialog() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String baudRate = prefs.getString("comm_baudrate", "38400");
        int protocolIndex = Integer.parseInt(prefs.getString("protocol", "0"));

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("USB Settings");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        // USB permission info
        TextView usbInfo = new TextView(this);
        usbInfo.setText("ℹ USB permissions are requested when connecting to a device");
        usbInfo.setTextSize(11);
        usbInfo.setTextColor(Color.parseColor("#757575"));
        usbInfo.setPadding(0, 0, 0, 20);
        layout.addView(usbInfo);

        // Baud rate selection
        TextView baudLabel = new TextView(this);
        baudLabel.setText("Baud Rate");
        baudLabel.setPadding(0, 0, 0, 10);
        baudLabel.setTextSize(14);
        layout.addView(baudLabel);

        Spinner baudSpinner = new Spinner(this);
        String[] baudRates = {"2400", "9600", "19200", "38400", "57600", "115200", "230400", "460800", "500000", "576000", "921600", "1000000", "1152000", "1500000", "2000000", "2500000", "3000000", "3500000", "4000000"};
        ArrayAdapter<String> baudAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, baudRates);
        baudAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        baudSpinner.setAdapter(baudAdapter);
        for (int i = 0; i < baudRates.length; i++) {
            if (baudRates[i].equals(baudRate)) {
                baudSpinner.setSelection(i);
                break;
            }
        }
        layout.addView(baudSpinner);

        // Protocol selection
        TextView protocolLabel = new TextView(this);
        protocolLabel.setText("OBD Protocol");
        protocolLabel.setPadding(0, 30, 0, 10);
        protocolLabel.setTextSize(14);
        layout.addView(protocolLabel);

        Spinner protocolSpinner = new Spinner(this);
        ElmProt.PROT[] protocols = ElmProt.PROT.values();
        String[] protocolNames = new String[protocols.length];
        for (int i = 0; i < protocols.length; i++) {
            protocolNames[i] = protocols[i].toString();
        }
        ArrayAdapter<String> protocolAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, protocolNames);
        protocolAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        protocolSpinner.setAdapter(protocolAdapter);
        protocolSpinner.setSelection(protocolIndex);
        layout.addView(protocolSpinner);

        builder.setView(layout);
        builder.setPositiveButton("Save", (dialog, which) -> {
            prefs.edit()
                .putString("comm_baudrate", baudSpinner.getSelectedItem().toString())
                .putString("protocol", String.valueOf(protocolSpinner.getSelectedItemPosition()))
                .apply();
            SnackbarHelper.showSuccess(this, "USB settings saved", SnackbarHelper.Duration.SHORT);
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showWifiSettingsDialog() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        int protocolIndex = Integer.parseInt(prefs.getString("protocol", "0"));

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("WiFi/Network Settings");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        // Network permission status
        TextView permissionStatus = new TextView(this);
        permissionStatus.setText("✓ Network permission granted (manifest permission)");
        permissionStatus.setTextSize(12);
        permissionStatus.setTextColor(Color.parseColor("#4CAF50"));
        permissionStatus.setPadding(0, 0, 0, 10);
        layout.addView(permissionStatus);

        // WiFi settings button
        Button wifiSettingsButton = new Button(this);
        wifiSettingsButton.setText("Open WiFi Settings");
        wifiSettingsButton.setTextSize(12);
        wifiSettingsButton.setPadding(20, 15, 20, 15);
        wifiSettingsButton.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_WIFI_SETTINGS);
            startActivity(intent);
        });
        layout.addView(wifiSettingsButton);

        // Divider
        View divider = new View(this);
        divider.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2));
        divider.setBackgroundColor(Color.parseColor("#E0E0E0"));
        LinearLayout.LayoutParams dividerParams = (LinearLayout.LayoutParams) divider.getLayoutParams();
        dividerParams.setMargins(0, 20, 0, 20);
        divider.setLayoutParams(dividerParams);
        layout.addView(divider);

        // Note about IP/Port
        TextView noteView = new TextView(this);
        noteView.setText("IP Address and Port are configured in the Network tab above");
        noteView.setTextSize(12);
        noteView.setTextColor(Color.parseColor("#757575"));
        noteView.setPadding(0, 0, 0, 10);
        layout.addView(noteView);

        // Protocol selection
        TextView protocolLabel = new TextView(this);
        protocolLabel.setText("OBD Protocol");
        protocolLabel.setPadding(0, 10, 0, 10);
        protocolLabel.setTextSize(14);
        layout.addView(protocolLabel);

        Spinner protocolSpinner = new Spinner(this);
        ElmProt.PROT[] protocols = ElmProt.PROT.values();
        String[] protocolNames = new String[protocols.length];
        for (int i = 0; i < protocols.length; i++) {
            protocolNames[i] = protocols[i].toString();
        }
        ArrayAdapter<String> protocolAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, protocolNames);
        protocolAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        protocolSpinner.setAdapter(protocolAdapter);
        protocolSpinner.setSelection(protocolIndex);
        layout.addView(protocolSpinner);

        builder.setView(layout);
        builder.setPositiveButton("Save", (dialog, which) -> {
            prefs.edit()
                .putString("protocol", String.valueOf(protocolSpinner.getSelectedItemPosition()))
                .apply();
            SnackbarHelper.showSuccess(this, "WiFi settings saved", SnackbarHelper.Duration.SHORT);
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
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
            log.info("Refreshing USB device list...");
            List<DeviceAdapter.DeviceInfo> result = new ArrayList<>();

            if (mUsbManager != null) {
                HashMap<String, UsbDevice> deviceList = mUsbManager.getDeviceList();
                log.info("UsbManager reports " + deviceList.size() + " USB devices");

                List<UsbSerialDriver> drivers = UsbSerialProber.getDefaultProber().findAllDrivers(mUsbManager);
                log.info("UsbSerialProber found " + drivers.size() + " compatible drivers");

                Map<String, UsbSerialPort> compatibleDevices = new HashMap<>();
                for (UsbSerialDriver driver : drivers) {
                    List<UsbSerialPort> ports = driver.getPorts();
                    for (UsbSerialPort port : ports) {
                        compatibleDevices.put(port.getDriver().getDevice().getDeviceName(), port);
                    }
                }

                for (UsbDevice device : deviceList.values()) {
                    log.info(String.format("  USB Device: VID=0x%04x PID=0x%04x Name=%s",
                            device.getVendorId(), device.getProductId(), device.getDeviceName()));

                    UsbSerialPort port = compatibleDevices.get(device.getDeviceName());
                    boolean isCompatible = port != null;
                    result.add(new DeviceAdapter.DeviceInfo(device, port, isCompatible));
                }
            }

            mHandler.post(() -> {
                usbEntries.clear();
                usbEntries.addAll(result);
                int compatibleCount = 0;
                for (DeviceAdapter.DeviceInfo info : result) {
                    if (info.isUsbCompatible) compatibleCount++;
                }
                usbDeviceCount.setText(String.format("%d USB device(s) found (%d compatible)",
                        result.size(), compatibleCount));
                usbAdapter.notifyDataSetChanged();

                if (result.size() > 0) {
                    hideEmptyState(AdapterTab.USB);
                } else {
                    showEmptyState(AdapterTab.USB);
                }

                log.info("Done refreshing USB devices, " + usbEntries.size() + " total, " + compatibleCount + " compatible");
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
            DeviceAdapter.DeviceInfo deviceInfo = btAdapter.getItem(position);
            if (deviceInfo == null || deviceInfo.bluetoothDevice == null) return;
            final BluetoothDevice device = deviceInfo.bluetoothDevice;

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
            DeviceAdapter.DeviceInfo deviceInfo = btAdapter.getItem(position);

            if (deviceInfo != null && deviceInfo.bluetoothDevice != null) {
                BluetoothDevice device = deviceInfo.bluetoothDevice;
                String address = device.getAddress();
                String name = device.getName() != null ? device.getName() : "Unknown Device";

                SnackbarHelper.showInfo(UnifiedAdapterSelectionActivity.this,
                        name + "\nMAC: " + address);
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

            DeviceAdapter.DeviceInfo deviceInfo = usbEntries.get(position);

            if (!deviceInfo.isUsbCompatible || deviceInfo.usbPort == null) {
                SnackbarHelper.showWarning(UnifiedAdapterSelectionActivity.this,
                        "This device is not a compatible USB serial adapter", SnackbarHelper.Duration.SHORT);
                return;
            }

            selectedUsbPort = deviceInfo.usbPort;

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
