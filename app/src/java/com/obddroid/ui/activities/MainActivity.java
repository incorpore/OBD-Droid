package com.obddroid.ui.activities;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.StrictMode;
import androidx.preference.PreferenceManager;
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.obddroid.core.ecu.EcuDataItem;
import com.obddroid.core.ecu.EcuDataItems;
import com.obddroid.core.ecu.EcuDataPv;
import com.obddroid.core.obd.ElmProt;
import com.obddroid.core.obd.ObdProt;
import com.obddroid.core.pvs.ProcessVar;
import com.obddroid.core.pvs.PvChangeEvent;
import com.obddroid.core.pvs.PvChangeListener;
import com.obddroid.core.pvs.PvList;

import com.obddroid.ui.adapters.FaultCodeAdapter;
import com.obddroid.ui.adapters.ObdItemAdapter;
import com.obddroid.ui.adapters.TestResultAdapter;
import com.obddroid.services.BluetoothCommService;
import com.obddroid.services.CommService;
import com.obddroid.services.NetworkCommService;
import com.obddroid.services.UsbCommService;
import com.obddroid.services.ObdDataService;
import com.obddroid.ui.components.AutoHider;
import com.obddroid.utils.ExportTask;
import com.obddroid.utils.FileHelper;
import com.obddroid.utils.SnackbarHelper;
import com.obddroid.vehicle.VehicleManager;
import com.obddroid.R;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeSet;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Main Activity for AndrOBD app
 */
public class MainActivity extends AppCompatActivity
        implements PvChangeListener,
        AdapterView.OnItemLongClickListener,
        AdapterView.OnItemClickListener,
        PropertyChangeListener,
        SharedPreferences.OnSharedPreferenceChangeListener,
        AbsListView.MultiChoiceModeListener
{
    /**
     * Key names for preferences
     */
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";
    public static final String PREF_AUTOHIDE = "autohide_toolbar";
    public static final String PREF_FULLSCREEN = "full_screen";
    public static final String PREF_AUTOHIDE_DELAY = "autohide_delay";
    /**
     * Message types sent from the BluetoothChatService Handler
     */
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_FILE_READ = 2;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;
    public static final int MESSAGE_UPDATE_VIEW = 7;
    public static final int MESSAGE_TOOLBAR_VISIBLE = 12;
    private static final String DEVICE_ADDRESS = "device_address";
    private static final String DEVICE_PORT = "device_port";
    private static final String MEASURE_SYSTEM = "measure_system";
    private static final String ELM_ADAPTIVE_TIMING = "adaptive_timing_mode";
    private static final String ELM_RESET_ON_NRC = "elm_reset_on_nrc";
    private static final String PREF_USE_LAST = "USE_LAST_SETTINGS";
    private static final String PREF_OVERLAY = "toolbar_overlay";
    private static final String PREF_DATA_DISABLE_MAX = "data_disable_max";
    private static final int MESSAGE_FILE_WRITTEN = 3;
    private static final int MESSAGE_DATA_ITEMS_CHANGED = 6;
    private static final int MESSAGE_OBD_STATE_CHANGED = 8;
    private static final int MESSAGE_OBD_NUMCODES = 9;
    private static final int MESSAGE_OBD_ECUS = 10;
    private static final int MESSAGE_OBD_NRC = 11;
    private static final String TAG = "AndrOBD";
    /**
     * internal Intent request codes
     */
    private static final int REQUEST_CONNECT_DEVICE_SECURE = 1;
    private static final int REQUEST_CONNECT_DEVICE_INSECURE = 2;
    private static final int REQUEST_ENABLE_BT = 3;
    private static final int REQUEST_SELECT_FILE = 4;
    private static final int REQUEST_SETTINGS = 5;
    private static final int REQUEST_CONNECT_DEVICE_USB = 6;
    private static final int REQUEST_GRAPH_DISPLAY_DONE = 7;
    private static final int REQUEST_CONNECT_UNIFIED = 8;
    /**
     * app exit parameters
     */
    private static final int EXIT_TIMEOUT = 2500;
    /**
     * reconnect cooldown time in milliseconds
     */
    private static final int RECONNECT_COOLDOWN_MS = 15000;
    /**
     * time between display updates to represent data changes
     */
    private static final int DISPLAY_UPDATE_TIME = 250;
    private static final String LOG_MASTER = "log_master";
    private static final String ELM_CUSTOM_INIT_CMDS = "elm_custom_init_cmds";
    /**
     * Logging
     */
    private static final Logger rootLogger = Logger.getLogger("");
    private static final Logger log = Logger.getLogger(TAG);
    /**
     * Timer for display updates
     */
    private static Timer updateTimer;
    /**
     * empty string set as default parameter
     */
    private static final Set<String> emptyStringSet = new HashSet<>();
    /**
     * app preferences ...
     */
    static SharedPreferences prefs;
    /**
     * dialog builder - removed static to prevent state persistence issues
     */
    /**
     * Local Bluetooth adapter
     */
    private static BluetoothAdapter mBluetoothAdapter = null;
    /**
     * Name of the connected BT device
     */
    private static String mConnectedDeviceName = null;
    /**
     * menu object
     */
    private static Menu menu;
    /**
     * Data list adapters
     */
    private static ObdItemAdapter mPidAdapter;
    private static TestResultAdapter mTidAdapter;
    private static FaultCodeAdapter mDfcAdapter;
    private static ObdItemAdapter currDataAdapter;
    /**
     * initial state of bluetooth adapter
     */
    private static boolean initialBtStateEnabled = false;
    /**
     * last time of back key pressed
     */
    private static long lastBackPressTime = 0;
    /**
     * toast for showing exit message
     */

    /**
     * Flag to temporarily ignore NRCs
     * This flag ist used to temporarily allow negative OBD responses without issuing an error message.
     * i.e. un-supported mode 0x0A for DFC reading
     */
    private static boolean ignoreNrcs = false;

    /**
     * handler for freeze frame selection
     */
    private final AdapterView.OnItemSelectedListener ff_selected = new AdapterView.OnItemSelectedListener()
    {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id)
        {
            CommService.elm.setFreezeFrame_Id(position);
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent)
        {

        }
    };
    /**
     * Member object for the BT comm services
     */
    private CommService mCommService = null;
    /**
     * timestamp of last reconnect attempt to prevent spam
     */
    private long lastReconnectTime = 0;
    /**
     * file helper
     */
    private FileHelper fileHelper;
    /**
     * the local list view
     */
    private View mListView;
    /**
     * ListView for list functionality
     */
    private ListView listView;

    /**
     * Get the list view (compatibility method)
     */
    private ListView getListView() {
        if (listView == null) {
            listView = findViewById(android.R.id.list);
        }
        return listView;
    }
    /**
     * current data view mode
     */
    private DATA_VIEW_MODE dataViewMode = DATA_VIEW_MODE.LIST;
    /**
     * AutoHider for the toolbar
     */
    private AutoHider toolbarAutoHider;
    /**
     * log file handler
     */
    private FileHandler logFileHandler;
    /**
     * current OBD service
     */
    private int obdService = ElmProt.OBD_SVC_NONE;
    /**
     * current operating mode
     */
    private MODE mode = MODE.OFFLINE;
    /**
     * current ECU connection state
     */
    private ElmProt.STAT ecuConnectionState = ElmProt.STAT.UNDEFINED;
    /**
     * Track if ECU has been selected by user
     */
    private boolean ecuUserSelected = false;

    // === Connection Cycle Detection for Unsupported Modes ===
    private ElmProt.STAT lastConnectionState = ElmProt.STAT.UNDEFINED;
    private int connectionCycleCount = 0;
    private int serviceWhenCycleStarted = ObdProt.OBD_SVC_NONE;
    private long lastCycleTimestamp = 0;
    private AlertDialog unsupportedModeDialog = null;
    private ElmProt.STAT ecuStateBeforeUnsupportedMode = ElmProt.STAT.UNDEFINED; // Save good state before cycles
    private static final int MAX_CYCLES_BEFORE_ALERT = 3;
    private static final long CYCLE_RESET_TIMEOUT_MS = 5000; // Reset cycle count if no cycles for 5 seconds

    // === Auto-Reconnect Tracking ===
    private boolean hasAttemptedAutoReconnect = false;

    // === Vehicle Info Footer ===
    private com.obddroid.ui.components.VehicleInfoFooter vehicleInfoFooter;

    /**
     * Handle message requests
     */
    @SuppressLint("HandlerLeak")
    private transient final Handler mHandler = new Handler(Looper.getMainLooper())
    {
        @Override
        public void handleMessage(Message msg)
        {
            try
            {
                PropertyChangeEvent evt;

                // log trace message for received handler notification event
                log.log(Level.FINEST, String.format("Handler notification: %s", msg.toString()));

                switch (msg.what)
                {
                    case MESSAGE_STATE_CHANGE:
                        // log trace message for received handler notification event
                        log.log(Level.FINEST, String.format("State change: %s", msg.toString()));
                        switch ((CommService.STATE) msg.obj)
                        {
                            case CONNECTED:
                                onConnect();
                                break;

                            case CONNECTING:
                                setStatus(R.string.title_connecting);
                                break;

                            default:
                                onDisconnect();
                                break;
                        }
                        break;

                    case MESSAGE_FILE_WRITTEN:
                        break;

                    // data has been read - finish up
                    case MESSAGE_FILE_READ:
                        // set listeners for data structure changes
                        setDataListeners();
                        // set adapters data source to loaded list instances
                        mPidAdapter.setPvList(ObdProt.PidPvs);
                        mTidAdapter.setPvList(ObdProt.TidPvs);
                        mDfcAdapter.setPvList(ObdProt.tCodes);
                        // set OBD data mode to the one selected by input file
                        setObdService(CommService.elm.getService(), getString(R.string.saved_data));
                        // Check if last data selection shall be restored
                        if (obdService == ObdProt.OBD_SVC_DATA)
                        {
                            checkToRestoreLastDataSelection();
                            // Don't restore view mode after connection - stay on main page
                            // checkToRestoreLastViewMode();
                        }
                        break;

                    case MESSAGE_DEVICE_NAME:
                        // save the connected device's name
                        mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);

                        // Save device name to preferences for reconnect card
                        if (mConnectedDeviceName != null) {
                            prefs.edit()
                                .putString("LAST_ADAPTER_NAME", mConnectedDeviceName)
                                .apply();
                            log.info("Saved device name for reconnect card: " + mConnectedDeviceName);

                            // Update the reconnect card subtitle immediately
                            updateReconnectCardSubtitle();
                        }

                        SnackbarHelper.showSuccess(MainActivity.this,
                                getString(R.string.connected_to) + mConnectedDeviceName);
                        break;

                    case MESSAGE_TOAST:
                        SnackbarHelper.showInfo(MainActivity.this,
                                msg.getData().getString(TOAST));
                        break;

                    case MESSAGE_DATA_ITEMS_CHANGED:
                        PvChangeEvent event = (PvChangeEvent) msg.obj;
                        switch (event.getType())
                        {
                            case PvChangeEvent.PV_ADDED:
                                if (currDataAdapter != null) {
                                    currDataAdapter.setPvList(currDataAdapter.pvs);
                                }
                                try
                                {
                                    // Debug: Log event source
                                    log.info("PV_ADDED event - source: " + event.getSource().getClass().getName() +
                                            ", VidPvs: " + ObdProt.VidPvs.getClass().getName() +
                                            ", match: " + (event.getSource() == ObdProt.VidPvs));

                                    if (event.getSource() == ObdProt.PidPvs)
                                    {
                                        // Check if last data selection shall be restored
                                        checkToRestoreLastDataSelection();
                                        // Don't restore view mode after connection - stay on main page
                                        // checkToRestoreLastViewMode();
                                    }
                                    else if (event.getSource() == ObdProt.VidPvs)
                                    {
                                        log.info("VidPvs match - calling checkForVinAndNotify");
                                        // Check if this is a VIN and notify VehicleManager
                                        VinDataHelper.checkForVinAndNotify(event);
                                    }
                                    else if (event.getSource() == ObdProt.TidPvs)
                                    {
                                        log.info("TidPvs match - Test Control data received");
                                        // Test Control data received - adapter will update automatically
                                    }
                                } catch (Exception e)
                                {
                                    log.log(Level.FINER, "Error adding PV", e);
                                }
                                break;

                            case PvChangeEvent.PV_MODIFIED:
                                // Debug: Log event source
                                log.info("PV_MODIFIED event - source: " + event.getSource().getClass().getName() +
                                        ", VidPvs: " + ObdProt.VidPvs.getClass().getName() +
                                        ", match: " + (event.getSource() == ObdProt.VidPvs));

                                // Also check for VIN updates (when existing VIN PV gets updated with actual value)
                                if (event.getSource() == ObdProt.VidPvs)
                                {
                                    log.info("VidPvs match - calling checkForVinAndNotify");
                                    VinDataHelper.checkForVinAndNotify(event);
                                }
                                else if (event.getSource() == ObdProt.TidPvs)
                                {
                                    log.info("TidPvs modified - Test Control data updated");
                                    // Test Control data updated - adapter will update automatically
                                }
                                break;

                            case PvChangeEvent.PV_CLEARED:
                                if (currDataAdapter != null) {
                                    currDataAdapter.clear();
                                }
                                break;
                        }
                        break;

                    case MESSAGE_UPDATE_VIEW:
                        if (listView != null) {
                            listView.invalidateViews();
                        }
                        break;

                    // handle state change in OBD protocol
                    case MESSAGE_OBD_STATE_CHANGED:
                        evt = (PropertyChangeEvent) msg.obj;
                        ElmProt.STAT state = (ElmProt.STAT) evt.getNewValue();

                        // Check if we should skip status updates for fault codes mode
                        boolean skipStatusUpdate = false;
                        if (CommService.elm != null &&
                            (CommService.elm.getService() == ObdProt.OBD_SVC_READ_CODES ||
                             CommService.elm.getService() == ObdProt.OBD_SVC_PENDINGCODES ||
                             CommService.elm.getService() == ObdProt.OBD_SVC_PERMACODES) &&
                            (state == ElmProt.STAT.NODATA || state == ElmProt.STAT.CONNECTING)) {
                            // Skip status update for these states in fault codes mode
                            // as "NO DATA" is normal response when there are no fault codes
                            skipStatusUpdate = true;
                        }

                        ecuConnectionState = state; // Track ECU connection state

                        // Update VehicleManager with ECU connection state
                        VehicleManager.getInstance().setECUConnectionState(state);

                        // === Detect connection cycles for unsupported modes ===
                        detectConnectionCycles(state);

                        /* Show ELM status only in ONLINE mode */
                        if (getMode() != MODE.DEMO && !skipStatusUpdate)
                        {
                            // Don't overwrite "ECU selected" status when state changes to CONNECTED
                            if (!(ecuUserSelected && state == ElmProt.STAT.CONNECTED)) {
                                // Special handling for ECU_SELECTED state
                                if (ecuUserSelected && state == ElmProt.STAT.ECU_DETECTED) {
                                    // Keep showing ECU selected when we're in ECU_DETECTED but user has selected
                                    setStatus(getResources().getStringArray(R.array.elmcomm_states)[ElmProt.STAT.ECU_SELECTED.ordinal()]);
                                } else {
                                    setStatus(getResources().getStringArray(R.array.elmcomm_states)[state.ordinal()]);
                                }
                            }
                        }

                        // Enable individual OBD services only when ECU is detected
                        updateServiceMenuItems(state == ElmProt.STAT.ECU_DETECTED ||
                                               state == ElmProt.STAT.CONNECTED);

                        // Don't auto-switch here - wait for ECU selection to complete

                        // Don't automatically restore last service - stay on main screen
                        break;

                    // handle change in number of fault codes
                    case MESSAGE_OBD_NUMCODES:
                        evt = (PropertyChangeEvent) msg.obj;
                        setNumCodes((Integer) evt.getNewValue());
                        break;

                    // handle ECU detection event
                    case MESSAGE_OBD_ECUS:
                        evt = (PropertyChangeEvent) msg.obj;
                        @SuppressWarnings("unchecked") // PropertyChangeEvent.getNewValue() returns Set<Integer> for ECU addresses
                        Set<Integer> ecuAddresses = (Set<Integer>) evt.getNewValue();

                        // Log detected ECUs for diagnostics
                        if (ecuAddresses != null && !ecuAddresses.isEmpty()) {
                            StringBuilder ecuList = new StringBuilder("Detected ECUs: ");
                            for (Integer addr : ecuAddresses) {
                                ecuList.append(String.format("0x%X ", addr));
                            }
                            log.info(ecuList.toString());

                            // Auto-proceed without ECU selection dialog
                            // CAN bus protocol naturally routes queries to correct ECUs based on PID
                            // No need to filter or manually select - let the bus handle it
                            ecuUserSelected = true;
                            setStatus(getResources().getStringArray(R.array.elmcomm_states)[ElmProt.STAT.ECU_SELECTED.ordinal()]);
                            VehicleManager.getInstance().setECUSelected(true);
                            VinDataHelper.triggerVinRetrieval();
                        }
                        break;

                    // handle negative result code from OBD protocol
                    case MESSAGE_OBD_NRC:
                        // show error dialog ...
                        if(! ignoreNrcs)
                        {
                            evt = (PropertyChangeEvent) msg.obj;
                            ObdProt.NRC nrc = (ObdProt.NRC) evt.getOldValue();
                            String nrcMsg = (String) evt.getNewValue();

                            // Special handling for "Feature not available" errors (0x12)
                            if (nrc.code == 0x12) {
                                // For Mode 9 (Vehicle Info) - complete service failure
                                if (CommService.elm.getService() == ObdProt.OBD_SVC_VEH_INFO) {
                                    // Mode 9 not supported - notify VehicleManager only if not already attempted
                                    VehicleManager vm = VehicleManager.getInstance();
                                    if (!vm.hasVINRetrievalFailed()) {
                                        vm.setVIN(null);
                                        // Auto-switch to live data since Mode 9 isn't supported
                                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                            if (CommService.elm != null && CommService.elm.getService() == ObdProt.OBD_SVC_VEH_INFO) {
                                                setObdService(ObdProt.OBD_SVC_DATA, "Live Data");
                                            }
                                        }, 500);
                                    }
                                    // Don't show error snackbar - VehicleInfoFooter handles display
                                    return;
                                }
                                // For Mode 8 (Test Control) - not supported by most vehicles
                                if (CommService.elm.getService() == ObdProt.OBD_SVC_CTRL_MODE) {
                                    // Show helpful message and switch back to dashboard
                                    SnackbarHelper.showInfo(MainActivity.this,
                                        "Test Control (Mode 8) not supported by this vehicle. This is normal for most consumer vehicles.");
                                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                        if (CommService.elm != null && CommService.elm.getService() == ObdProt.OBD_SVC_CTRL_MODE) {
                                            setObdService(ObdProt.OBD_SVC_NONE, null);
                                        }
                                    }, 500);
                                    return;
                                }
                                // For Mode 1 (Live Data) - individual PID not supported is normal
                                // Don't show error for individual unsupported PIDs
                                if (CommService.elm.getService() == ObdProt.OBD_SVC_DATA) {
                                    // Silently ignore - some PIDs aren't supported by all vehicles
                                    return;
                                }
                            }
                            switch (nrc.disp)
                            {
                                case ERROR:
                                    SnackbarHelper.showError(MainActivity.this, nrcMsg);
                                    break;
                                // Display warning (with confirmation)
                                case WARN:
                                    SnackbarHelper.showWarning(MainActivity.this, nrcMsg);
                                    break;
                                // Display notification (no confirmation)
                                case NOTIFY:
                                    SnackbarHelper.showInfo(MainActivity.this, nrcMsg);
                                    break;

                                case HIDE:
                                default:
                                    // intentionally ignore
                            }
                        }
                        break;

                    // set toolbar visibility
                    case MESSAGE_TOOLBAR_VISIBLE:
                        ActionBar ab = getSupportActionBar();
                        if (ab != null && (Boolean) msg.obj)
                        {
                            ab.show();
                        }
                        break;
                }
            } catch (Exception ex)
            {
                log.log(Level.SEVERE, "Error in mHandler", ex);
            }
        }
    };

    /**
     * Set fixed PIDs for protocol to specified list of PIDs
     *
     * @param pidNumbers List of PIDs
     */
    public static void setFixedPids(Set<Integer> pidNumbers)
    {
        int[] pids = new int[pidNumbers.size()];
        int i = 0;
        for (Integer pidNum : pidNumbers)
        {
            pids[i++] = pidNum;
        }
        Arrays.sort(pids);
        // set protocol fixed PIDs
        ObdProt.setFixedPid(pids);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        // instantiate superclass
        super.onCreate(savedInstanceState);

        // Initialize VehicleManager with context
        VehicleManager.getInstance(this);

        // Initialize DTC Database for comprehensive code lookup (28K+ codes)
        com.obddroid.core.ecu.DTCDatabaseCodeList dtcDatabase =
            new com.obddroid.core.ecu.DTCDatabaseCodeList(this);

        // Set as singleton instance for ObdCodeList
        com.obddroid.core.ecu.ObdCodeList.setDatabaseInstance(dtcDatabase);

        // Also set in EcuConversions for fault code lookups
        com.obddroid.core.ecu.EcuConversions.codeList = dtcDatabase;

        // Set status bar and navigation bar colors to match our theme right away
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(Color.parseColor("#212121"));
            getWindow().setNavigationBarColor(Color.parseColor("#212121"));
        }

        // get additional permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
        {
            // Storage Permissions
            final int REQUEST_EXTERNAL_STORAGE = 1;
            final String[] PERMISSIONS_STORAGE = {
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            };
            requestPermissions(PERMISSIONS_STORAGE, REQUEST_EXTERNAL_STORAGE);
            // Workaround for FileUriExposedException in Android >= M
            StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
            StrictMode.setVmPolicy(builder.build());
        }

        // Removed global dlgBuilder initialization - creating fresh instances for each dialog

        // get preferences
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        // register for later changes
        prefs.registerOnSharedPreferenceChangeListener(this);

        // Overlay feature has to be set before window content is set
        if (prefs.getBoolean(PREF_AUTOHIDE, false)
                && prefs.getBoolean(PREF_OVERLAY, false))
        {
            getWindow().requestFeature(Window.FEATURE_ACTION_BAR_OVERLAY);
        }

        // Set up all data adapters
        mPidAdapter = new ObdItemAdapter(this, R.layout.obd_item, ObdProt.PidPvs);
        mTidAdapter = new TestResultAdapter(this, R.layout.obd_item, ObdProt.TidPvs);
        mDfcAdapter = new FaultCodeAdapter(this, R.layout.obd_item, ObdProt.tCodes);
        currDataAdapter = mPidAdapter;

        // get list view
        mListView = getWindow().getLayoutInflater().inflate(R.layout.obd_list, null);

        // update all settings from preferences
        onSharedPreferenceChanged(prefs, null);

        // set up logging system
        setupLoggers();

        // Log program startup
        log.info(String.format("%s %s starting",
                getString(R.string.app_name),
                getString(R.string.app_version)));

        // create file helper instance
        fileHelper = new FileHelper(this);
        // set listeners for data structure changes
        setDataListeners();
        // automate elm status display
        CommService.elm.addPropertyChangeListener(this);

        // Initialize action bar
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null)
        {
            actionBar.show();
            // Enable home button to navigate back to dashboard with app logo
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setHomeAsUpIndicator(R.drawable.ic_app_logo);
        }
        // start automatic toolbar hider
        setAutoHider(prefs.getBoolean(PREF_AUTOHIDE, false));

        // set content view
        setContentView(R.layout.startup_layout);

        // Wire up footer overlay
        setupFooterOverlay();

        // Set up dashboard card click listeners immediately after setting content view
        setupDashboardCards();
        log.info("Dashboard cards set up in onCreate()");

        // override comm medium with USB connect intent
        if ("android.hardware.usb.action.USB_DEVICE_ATTACHED".equals(getIntent().getAction()))
        {
            CommService.medium = CommService.MEDIUM.USB;
        }

        switch (CommService.medium)
        {
            case BLUETOOTH:
                // Get local Bluetooth adapter
                BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
                mBluetoothAdapter = bluetoothManager != null ? bluetoothManager.getAdapter() : null;
                log.fine("Adapter: " + mBluetoothAdapter);
                // If BT is not on, request that it be enabled.
                if (getMode() != MODE.DEMO && mBluetoothAdapter != null)
                {
                    // remember initial bluetooth state
                    initialBtStateEnabled = mBluetoothAdapter.isEnabled();
                    if (!initialBtStateEnabled)
                    {
                        // request to enable bluetooth
                        Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                        launchActivityForResult(enableIntent, REQUEST_ENABLE_BT);
                    }
                    else
                    {
                        // Don't auto-connect on startup - leave "connect" action to the user
                        // App should always start on the main screen
                    }
                }
                break;

            case USB:
            case NETWORK:
                // Don't auto-connect on startup - start in offline mode
                // User should manually connect via the menu
                setMode(MODE.OFFLINE);
                break;
        }

        // Setup modern back button handling
        setupBackPressedCallback();
    }

    /**
     * Handler for application start event
     */
    @Override
    public void onStart()
    {
        super.onStart();
        // If the adapter is null, then Bluetooth is not supported
        if (CommService.medium == CommService.MEDIUM.BLUETOOTH && mBluetoothAdapter == null)
        {
            // start ELM protocol demo loop
            setMode(MODE.DEMO);
        }
    }

    @Override protected void onPause()
    {
        super.onPause();

        // stop data display update timer
        updateTimer.cancel();
    }

    @Override protected void onResume()
    {
        super.onResume();

        // Synchronize UI with actual connection state
        // This prevents "Connecting..." from persisting after navigation
        updateConnectionStatusUI();

        // Update reconnect card with last connected adapter info
        updateReconnectCardSubtitle();

        // Auto-reconnect on startup if enabled (only on first resume)
        attemptAutoReconnectIfEnabled();

        // set up data display update timer
        updateTimer = new Timer();
        final TimerTask updateTask = new TimerTask()
        {
            @Override
            public void run()
            {
                /* forward message to update the view */
                Message msg = mHandler.obtainMessage(MainActivity.MESSAGE_UPDATE_VIEW);
                mHandler.sendMessage(msg);
            }
        };
        updateTimer.schedule(updateTask, 0, DISPLAY_UPDATE_TIME);
    }

    /**
     * Synchronize UI connection status with actual CommService state
     * Called on onResume() to prevent stuck "Connecting..." state
     */
    private void updateConnectionStatusUI()
    {
        // Check actual CommService state and update UI accordingly
        if (mCommService != null)
        {
            CommService.STATE currentState = mCommService.getState();

            switch (currentState)
            {
                case CONNECTED:
                    // Service is connected, ensure UI reflects this
                    if (mode != MODE.ONLINE)
                    {
                        onConnect();
                    }
                    break;

                case CONNECTING:
                    // Service is still connecting, show connecting status
                    setStatus(R.string.title_connecting);
                    break;

                case OFFLINE:
                case NONE:
                default:
                    // Service is offline, ensure UI reflects this
                    if (mode != MODE.OFFLINE && mode != MODE.DEMO && mode != MODE.FILE)
                    {
                        onDisconnect();
                    }
                    break;
            }
        }
        else
        {
            // No CommService means offline
            if (mode != MODE.OFFLINE && mode != MODE.DEMO && mode != MODE.FILE)
            {
                setStatus(getString(R.string.status_connect_device));
            }
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see android.app.Activity#onDestroy()
     */
    @Override
    protected void onDestroy()
    {
        // Stop toolbar hider thread
        setAutoHider(false);

        try
        {
            // Reduce ELM power consumption by setting it to sleep
            CommService.elm.goToSleep();
            // wait until message is out ...
            Thread.sleep(100, 0);
        } catch (InterruptedException e)
        {
            // do nothing
            log.log(Level.FINER, e.getLocalizedMessage());
        }

        /* don't listen to ELM data changes any more */
        removeDataListeners();
        // don't listen to ELM property changes any more
        CommService.elm.removePropertyChangeListener(this);

        // stop demo service if it was started
        setMode(MODE.OFFLINE);

        // stop communication service
        if (mCommService != null)
        {
            mCommService.stop();
        }

        // if bluetooth adapter was switched OFF before ...
        if (mBluetoothAdapter != null && !initialBtStateEnabled)
        {
            // ... turn it OFF again (only supported on Android 12 and below)
            // Note: Android 13+ removed the ability for apps to disable Bluetooth
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                // Suppress deprecation - required for backward compatibility with API < 33
                @SuppressWarnings("deprecation")
                boolean disabled = mBluetoothAdapter.disable();
            }
        }

        log.info(String.format("%s %s finished",
                getString(R.string.app_name),
                getString(R.string.app_version)));

        /* remove log file handler, if available (file access was granted) */
        if (logFileHandler != null) logFileHandler.close();
        Logger.getLogger("").removeHandler(logFileHandler);

        super.onDestroy();
    }

    @Override
    public void setContentView(int layoutResID)
    {
        setContentView(getLayoutInflater().inflate(layoutResID, null));
    }

    @Override
    public void setContentView(View view)
    {
        super.setContentView(view);
        listView = findViewById(android.R.id.list);
        if (listView != null) {
            listView.setOnTouchListener(toolbarAutoHider);
        }
    }

    /**
     * handle pressing of the BACK-KEY using modern OnBackPressedCallback
     */
    private void setupBackPressedCallback()
    {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // Check if vehicle info footer is expanded - collapse it first
                if (vehicleInfoFooter != null && vehicleInfoFooter.isExpanded()) {
                    vehicleInfoFooter.collapse();
                    return; // Consume the back press
                }

                if (CommService.elm.getService() != ObdProt.OBD_SVC_NONE)
                {
                    if (dataViewMode != DATA_VIEW_MODE.LIST)
                    {
                        setDataViewMode(DATA_VIEW_MODE.LIST);
                        checkToRestoreLastDataSelection();
                    } else
                    {
                        setObdService(ObdProt.OBD_SVC_NONE, null);
                    }
                } else
                {
                    if (lastBackPressTime < System.currentTimeMillis() - EXIT_TIMEOUT)
                    {
                        SnackbarHelper.showInfo(MainActivity.this, getString(R.string.back_again_to_exit));
                        lastBackPressTime = System.currentTimeMillis();
                    } else
                    {
                        setEnabled(false);
                        getOnBackPressedDispatcher().onBackPressed();
                    }
                }
            }
        });
    }

    /**
     * Handler for options menu creation event
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        MainActivity.menu = menu;
        // update menu item status for current conversion
        setConversionSystem(EcuDataItem.cnvSystem);
        return true;
    }

    /**
     * Handler for Options menu selection
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId())
        {
            case android.R.id.home:
                // Home button clicked - return to dashboard
                setObdService(ObdProt.OBD_SVC_NONE, getString(R.string.app_name));
                return true;

            case R.id.secure_connect_scan:
                setMode(MODE.ONLINE);
                return true;


            case R.id.disconnect:
                // Show styled confirmation dialog before disconnecting
                showDisconnectConfirmDialog();
                return true;

            case R.id.settings:
                // Launch the Settings Activity
                Intent settingsIntent = new Intent(this, SettingsActivity.class);
                launchActivityForResult(settingsIntent, REQUEST_SETTINGS);
                return true;




            case R.id.service_home:
                // Always return to dashboard/home screen
                setObdService(ObdProt.OBD_SVC_NONE, getString(R.string.app_name));
                return true;

            case R.id.service_none:
                setObdService(ObdProt.OBD_SVC_NONE, item.getTitle());
                return true;

            case R.id.service_data:
                if (ecuConnectionState == ElmProt.STAT.ECU_DETECTED ||
                    ecuConnectionState == ElmProt.STAT.CONNECTED) {
                    setObdService(ObdProt.OBD_SVC_DATA, item.getTitle());
                } else {
                    SnackbarHelper.showWarning(this, "Please wait for ECU connection to complete");
                }
                return true;

            case R.id.service_testcontrol:
                if (ecuConnectionState == ElmProt.STAT.ECU_DETECTED ||
                    ecuConnectionState == ElmProt.STAT.CONNECTED) {
                    setObdService(ObdProt.OBD_SVC_CTRL_MODE, item.getTitle());
                } else {
                    SnackbarHelper.showWarning(this, "Please wait for ECU connection to complete");
                }
                return true;

            case R.id.service_codes:
                if (ecuConnectionState == ElmProt.STAT.ECU_DETECTED ||
                    ecuConnectionState == ElmProt.STAT.CONNECTED) {
                    setObdService(ObdProt.OBD_SVC_READ_CODES, item.getTitle());
                } else {
                    SnackbarHelper.showWarning(this, "Please wait for ECU connection to complete");
                }
                return true;

        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onItemCheckedStateChanged(ActionMode mode, int position, long id, boolean checked)
    {
        // Intentionally do nothing
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu)
    {
        MenuInflater inflater = mode.getMenuInflater();
        inflater.inflate(R.menu.context_graph, menu);
        return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu)
    {
        return false;
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item)
    {
        switch (item.getItemId())
        {
            case R.id.chart_selected:
                setDataViewMode(DATA_VIEW_MODE.CHART);
                return true;

            case R.id.hud_selected:
                setDataViewMode(DATA_VIEW_MODE.HEADUP);
                return true;

            case R.id.dashboard_selected:
                setDataViewMode(DATA_VIEW_MODE.DASHBOARD);
                return true;

            case R.id.filter_selected:
                setDataViewMode(DATA_VIEW_MODE.FILTERED);
                return true;
        }
        return false;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode)
    {

    }

    /**
     * Handler for result messages from other activities
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        boolean secureConnection = false;

        switch (requestCode)
        {
            // device is connected
            case REQUEST_CONNECT_DEVICE_SECURE:
                secureConnection = true;
                // no break here ...
            case REQUEST_CONNECT_DEVICE_INSECURE:
                // When BtDeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK)
                {
                    // Get the device MAC address
                    String address = Objects.requireNonNull(data.getExtras()).getString(
                            BtDeviceListActivity.EXTRA_DEVICE_ADDRESS);

                    // Check if demo mode was selected
                    if ("DEMO_MODE".equals(address)) {
                        // Start demo mode
                        setMode(MODE.DEMO);
                    } else {
                        // Save the device address for display purposes only (not for auto-restore)
                        prefs.edit().putString("LAST_DEV_ADDRESS", address).apply();
                        connectBtDevice(address, secureConnection);
                    }
                } else
                {
                    setMode(MODE.OFFLINE);
                }
                break;

            // USB device selected
            case REQUEST_CONNECT_DEVICE_USB:
                // DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK)
                {
                    mCommService = new UsbCommService(this, mHandler);
                    mCommService.connect(UsbDeviceListActivity.selectedPort, true);
                } else
                {
                    setMode(MODE.OFFLINE);
                }
                break;

            // Unified adapter selection
            case REQUEST_CONNECT_UNIFIED:
                if (resultCode == Activity.RESULT_OK && data != null)
                {
                    String adapterType = data.getStringExtra(UnifiedAdapterSelectionActivity.EXTRA_ADAPTER_TYPE);

                    // Handle demo mode
                    if ("DEMO".equals(adapterType)) {
                        setMode(MODE.DEMO);
                        break;
                    }

                    // Handle adapter types
                    if (adapterType != null) {
                        try {
                            CommService.MEDIUM medium = CommService.MEDIUM.valueOf(adapterType);

                            switch (medium) {
                                case BLUETOOTH:
                                    String btAddress = data.getStringExtra(UnifiedAdapterSelectionActivity.EXTRA_DEVICE_ADDRESS);
                                    if (btAddress != null) {
                                        // Save the device address and adapter type
                                        log.info("Saving Bluetooth adapter info - Address: " + btAddress);
                                        prefs.edit()
                                            .putString("LAST_DEV_ADDRESS", btAddress)
                                            .putString("LAST_ADAPTER_TYPE", "BLUETOOTH")
                                            .apply();
                                        log.info("Bluetooth adapter info saved successfully");
                                        // Connect to Bluetooth device
                                        connectBtDevice(btAddress, prefs.getBoolean("bt_secure_connection", false));
                                    } else {
                                        setMode(MODE.OFFLINE);
                                    }
                                    break;

                                case USB:
                                    if (UnifiedAdapterSelectionActivity.selectedUsbPort != null) {
                                        // Save adapter type for USB
                                        log.info("Saving USB adapter type");
                                        prefs.edit()
                                            .putString("LAST_ADAPTER_TYPE", "USB")
                                            .apply();
                                        log.info("USB adapter type saved successfully");
                                        mCommService = new UsbCommService(this, mHandler);
                                        mCommService.connect(UnifiedAdapterSelectionActivity.selectedUsbPort, true);
                                    } else {
                                        setMode(MODE.OFFLINE);
                                    }
                                    break;

                                case NETWORK:
                                    String networkIp = data.getStringExtra(UnifiedAdapterSelectionActivity.EXTRA_NETWORK_IP);
                                    int networkPort = data.getIntExtra(UnifiedAdapterSelectionActivity.EXTRA_NETWORK_PORT, 35000);
                                    if (networkIp != null) {
                                        // Save network info and adapter type
                                        log.info("Saving Network adapter info - IP: " + networkIp + ", Port: " + networkPort);
                                        prefs.edit()
                                            .putString("LAST_ADAPTER_TYPE", "NETWORK")
                                            .putString("DEVICE_ADDRESS", networkIp)
                                            .putInt("DEVICE_PORT", networkPort)
                                            .apply();
                                        log.info("Network adapter info saved successfully");
                                        connectNetworkDevice(networkIp, networkPort);
                                    } else {
                                        setMode(MODE.OFFLINE);
                                    }
                                    break;
                            }
                        } catch (IllegalArgumentException e) {
                            log.warning("Invalid adapter type: " + adapterType);
                            setMode(MODE.OFFLINE);
                        }
                    } else {
                        setMode(MODE.OFFLINE);
                    }
                } else
                {
                    setMode(MODE.OFFLINE);
                }
                break;

            // bluetooth enabled
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK)
                {
                    // Start online mode
                    setMode(MODE.ONLINE);
                } else
                {
                    // Start demo service Thread
                    setMode(MODE.DEMO);
                }
                break;

            // file selected
            case REQUEST_SELECT_FILE:
                if (resultCode == RESULT_OK)
                {
                    // Get the Uri of the selected file
                    Uri uri = data.getData();
                    log.info("Load content: " + uri);
                    // load data ...
                    fileHelper.loadDataThreaded(uri, mHandler);
                    updateServiceMenuItems(true);
                }
                break;

            // settings finished
            case REQUEST_SETTINGS:
                // change handling done by callbacks
                break;

            // graphical data view finished
            case REQUEST_GRAPH_DISPLAY_DONE:
                // let context know that we are in list mode again ...
                dataViewMode = DATA_VIEW_MODE.LIST;
                break;
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key)
    {
        // Always keep main display on for vehicle diagnostics
        if (key == null)
        {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        // FULL SCREEN operation based on preference settings
        if (key == null || PREF_FULLSCREEN.equals(key))
        {
            ActionBar actionBar = getSupportActionBar();
            WindowInsetsControllerCompat windowInsetsController = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());

            if (prefs.getBoolean(PREF_FULLSCREEN, false))
            {
                // Ultra-dark mode: hide status bar and make everything black
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    getWindow().setNavigationBarColor(Color.BLACK);
                }

                // Hide status and navigation bars using modern API
                if (windowInsetsController != null) {
                    windowInsetsController.hide(WindowInsetsCompat.Type.systemBars());
                    windowInsetsController.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                }

                // Make the action bar black too
                if (actionBar != null) {
                    actionBar.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.BLACK));
                }
            }
            else
            {
                // Show the status bar and restore dark grey theme
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    getWindow().setNavigationBarColor(Color.parseColor("#212121"));
                }

                // Show status and navigation bars using modern API
                if (windowInsetsController != null) {
                    windowInsetsController.show(WindowInsetsCompat.Type.systemBars());
                }

                // Restore the action bar color
                if (actionBar != null) {
                    actionBar.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.parseColor("#212121")));
                }
            }
        }

        // Always set default colors in regular mode
        if (!prefs.getBoolean(PREF_FULLSCREEN, false)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                getWindow().setStatusBarColor(Color.parseColor("#212121"));
                getWindow().setNavigationBarColor(Color.parseColor("#212121"));
            }
        }


        // set default comm medium
        if (key == null || SettingsActivity.KEY_COMM_MEDIUM.equals(key))
        {
            CommService.medium =
                    CommService.MEDIUM.values()[
                            getPrefsInt(SettingsActivity.KEY_COMM_MEDIUM, 0)];
        }

        // enable/disable ELM adaptive timing
        if (key == null || ELM_ADAPTIVE_TIMING.equals(key))
        {
            CommService.elm.mAdaptiveTiming.setMode(
                    ElmProt.AdaptTimingMode.valueOf(
                            prefs.getString(ELM_ADAPTIVE_TIMING,
                                    ElmProt.AdaptTimingMode.OFF.toString())));
        }

        // set protocol flag to initiate immediate reset on NRC reception
        if (key == null || ELM_RESET_ON_NRC.equals(key))
        {
            CommService.elm.setResetOnNrc(prefs.getBoolean(ELM_RESET_ON_NRC, false));
        }

        // set custom ELM init commands
        if (key == null || ELM_CUSTOM_INIT_CMDS.equals(key))
        {
            String value = prefs.getString(ELM_CUSTOM_INIT_CMDS, null);
            if (value != null && value.length() > 0)
            {
                CommService.elm.setCustomInitCommands(value.split("\n"));
            }
        }

        // ELM timeout
        if (key == null || SettingsActivity.ELM_MIN_TIMEOUT.equals(key))
        {
            CommService.elm.mAdaptiveTiming.setElmTimeoutMin(
                    getPrefsInt(SettingsActivity.ELM_MIN_TIMEOUT,
                            CommService.elm.mAdaptiveTiming.getElmTimeoutMin()));
        }

        // ... preferred protocol
        if (key == null || SettingsActivity.KEY_PROT_SELECT.equals(key))
        {
            ElmProt.setPreferredProtocol(getPrefsInt(SettingsActivity.KEY_PROT_SELECT, 0));
        }

        // set disabled ELM commands
        if (key == null || SettingsActivity.ELM_CMD_DISABLE.equals(key))
        {
            ElmProt.disableCommands(prefs.getStringSet(SettingsActivity.ELM_CMD_DISABLE, null));
        }

        // ... measurement system
        if (key == null || MEASURE_SYSTEM.equals(key))
        {
            setConversionSystem(getPrefsInt(MEASURE_SYSTEM, EcuDataItem.SYSTEM_METRIC));
        }


        // log levels
        if (key == null || LOG_MASTER.equals(key))
        {
            setLogLevels();
        }

        // AutoHide ToolBar
        if (key == null || PREF_AUTOHIDE.equals(key) || PREF_AUTOHIDE_DELAY.equals(key))
        {
            setAutoHider(prefs.getBoolean(PREF_AUTOHIDE, false));
        }

        // Max. data disabling debounce counter
        if (key == null || PREF_DATA_DISABLE_MAX.equals(key))
        {
            EcuDataItem.MAX_ERROR_COUNT = getPrefsInt(PREF_DATA_DISABLE_MAX, 3);
        }

        // Customized PID display color preference
        if (key != null)
        {
            // specific key -> update single
            updatePidColor(key);
            updatePidDisplayRange(key);
            updatePidUpdatePeriod(key);
        }
        else
        {
            // loop through all keys
            for (String currKey : prefs.getAll().keySet())
            {
                // update by key
                updatePidColor(currKey);
                updatePidDisplayRange(currKey);
                updatePidUpdatePeriod(currKey);
            }
        }
    }

    /**
     * Update PID PV display color from preference
     * @param key Preference key
     */
    private void updatePidColor(String key)
    {
        int pos = key.indexOf("/".concat(EcuDataPv.FID_COLOR));
        if(pos >= 0)
        {
            String mnemonic = key.substring(0, pos);
            EcuDataItem itm = EcuDataItems.byMnemonic.get(mnemonic);
            // Default BLACK is to detect key removal
            Integer color = prefs.getInt(key, Color.BLACK);
            if(Color.BLACK != color)
            {
                itm.pv.put(EcuDataPv.FID_COLOR, color);
                log.info(String.format("PID pref %s=#%08x", key, color));
            }
        }
    }

    /**
     * Update PID PV display color from preference
     * @param key Preference key
     */
    private void updatePidDisplayRange(String key)
    {
        final String[] rangeFields = new String[]
        {
            EcuDataPv.FID_MIN,
            EcuDataPv.FID_MAX
        };
        // Loop through <MIN/MAX>> fields
        for (String field : rangeFields)
        {
            // If preference key matches PID/<MIN/MAX>
            int pos = key.indexOf("/".concat(field));
            if (pos >= 0)
            {
                // Default MAX_VALUE is to detect key removal
                Number value = prefs.getFloat(key, Float.MAX_VALUE);
                if (Float.MAX_VALUE != value.floatValue())
                {
                    // Find corresponding data item
                    String mnemonic = key.substring(0, pos);
                    EcuDataItem itm = EcuDataItems.byMnemonic.get(mnemonic);
                    // update display range limit in data item
                    itm.pv.put(field, value);

                    log.info(String.format("PID pref %s=%f", key, value));
                }
            }
        }
    }

    /**
     * Update customized PID display update period from preference
     * @param key Preference key
     */
    private void updatePidUpdatePeriod(String key)
    {
            // If preference key matches PID/<MIN/MAX>
            int pos = key.indexOf("/".concat(EcuDataPv.FID_UPDT_PERIOD));
            if (pos >= 0)
            {
                // Default MAX_VALUE is to detect key removal
                long value = prefs.getLong(key, 0);
                if (0 != value)
                {
                    // Find corresponding data item
                    String mnemonic = key.substring(0, pos);
                    EcuDataItem itm = EcuDataItems.byMnemonic.get(mnemonic);
                    // update display range limit in data item
                    itm.updatePeriod_ms = value;

                    log.info(String.format("PID pref %s=%f", key, value));
                }
            }
    }

    /**
     * Handle long licks on OBD data list items
     */
    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id)
    {
        Intent intent;
        EcuDataPv pv;

        switch (CommService.elm.getService())
        {
            /* if we are in OBD data mode:
             * ->Long click on an item starts the single item dashboard activity
             */
            case ObdProt.OBD_SVC_DATA:
                pv = (EcuDataPv) currDataAdapter.getItem(position);
                /* only numeric values may be shown as graph/dashboard */
                if (pv.get(EcuDataPv.FID_VALUE) instanceof Number)
                {
                    DashBoardActivity.setAdapter(currDataAdapter);
                    intent = new Intent(this, DashBoardActivity.class);
                    intent.putExtra(DashBoardActivity.POSITIONS, new int[]{position});
                    startActivity(intent);
                }
                break;

            /* If we are in DFC mode of any kind
             * -> Long click now also shows the options modal (same as tap)
             */
            case ObdProt.OBD_SVC_READ_CODES:
            case ObdProt.OBD_SVC_PERMACODES:
            case ObdProt.OBD_SVC_PENDINGCODES:
                // Show the same modal as regular tap for consistency
                FaultCodeUiHelper.showFaultCodeOptionsModal(this, currDataAdapter, position, ecuConnectionState);
                break;

            case ObdProt.OBD_SVC_CTRL_MODE:
                pv = (EcuDataPv) currDataAdapter.getItem(position);
                // Confirm & perform OBD test control ...
                confirmObdTestControl(pv.get(EcuDataPv.FID_DESCRIPT).toString(),
                        ObdProt.OBD_SVC_CTRL_MODE,
                        pv.getAsInt(EcuDataPv.FID_PID));
                break;
        }
        return true;
    }

    /**
     * Handle clicks on OBD data list items
     */
    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id)
    {
        switch (CommService.elm.getService())
        {
            // If we are in DFC mode, show fault code options modal
            case ObdProt.OBD_SVC_READ_CODES:
            case ObdProt.OBD_SVC_PERMACODES:
            case ObdProt.OBD_SVC_PENDINGCODES:
                FaultCodeUiHelper.showFaultCodeOptionsModal(this, currDataAdapter, position, ecuConnectionState);
                break;
        }
    }

    /**
     * Handler for PV change events This handler just forwards the PV change
     * events to the android handler, since all adapter / GUI actions have to be
     * performed from the main handler
     *
     * @param event PvChangeEvent which is reported
     */
    @Override
    public synchronized void pvChanged(PvChangeEvent event)
    {
        // forward PV change to the UI Activity
        Message msg = mHandler.obtainMessage(MainActivity.MESSAGE_DATA_ITEMS_CHANGED);
        if (!event.isChildEvent())
        {
            msg.obj = event;
            mHandler.sendMessage(msg);
        }
    }


    /**
     * Check if last data selection shall be restored
     * <p>
     * If previously selected items shall be re-selected, then re-select them
     */
    private void checkToRestoreLastDataSelection()
    {
        // Restoration disabled - do nothing
    }

    /**
     * Check if last view mode shall be restored
     * <p>
     * If last view mode shall be restored by user settings,
     * then restore the last selected view mode
     */
    private void checkToRestoreLastViewMode()
    {
        // Restoration disabled - do nothing
    }

    /**
     * convert result of Arrays.toString(int[]) back into int[]
     *
     * @param input String of array
     * @return int[] of String value
     */
    private int[] toIntArray(String input)
    {
        int[] result = {};
        int numValidEntries = 0;
        try
        {
            String beforeSplit = input.replaceAll("\\[|]|\\s", "");
            String[] split = beforeSplit.split(",");
            int[] ints = new int[split.length];
            for (String s : split)
            {
                if (s.length() > 0)
                {
                    ints[numValidEntries++] = Integer.parseInt(s);
                }
            }
            result = Arrays.copyOf(ints, numValidEntries);
        } catch (Exception ex)
        {
            log.severe(ex.toString());
        }

        return result;
    }

    /**
     * OnClick handler - Browse URL from content description
     *
     * @param view view source of click event
     */
    public void browseClickedUrl(View view)
    {
        String url = view.getContentDescription().toString();
        startActivity(new Intent(Intent.ACTION_VIEW).setData(Uri.parse(url)));
    }

    /**
     * Unhide action bar
     */
    private void unHideActionBar()
    {
        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null)
        {
            runOnUiThread(new Runnable()
            {
                @Override
                public void run()
                {
                    actionBar.show();
                }
            });
        }
    }


    private void setNumCodes(int newNumCodes)
    {
        // Extract MIL status and code count
        boolean milOn = (newNumCodes & 0x80) != 0;
        int numCodes = newNumCodes & 0x7F; // Get actual code count (lower 7 bits)

        // Only update the status card if we're on the fault codes screen
        if (obdService == ObdProt.OBD_SVC_READ_CODES ||
            obdService == ObdProt.OBD_SVC_PENDINGCODES ||
            obdService == ObdProt.OBD_SVC_PERMACODES) {

            // Update status card if it exists
            ImageView statusIcon = (ImageView) findViewById(R.id.mil_status_icon);
            TextView statusText = (TextView) findViewById(R.id.mil_status_text);
            TextView statusSubtitle = (TextView) findViewById(R.id.mil_status_subtitle);
            View clearCodesBtn = findViewById(R.id.clear_codes_button);

            if (statusIcon != null && statusText != null && statusSubtitle != null) {
                if (milOn || numCodes > 0) {
                    // MIL is ON - show warning status
                    statusIcon.setColorFilter(Color.parseColor("#FFC107"));
                    statusText.setText(numCodes + " Fault Code" + (numCodes != 1 ? "s" : "") + " Detected");
                    statusSubtitle.setText("Check engine light is ON");
                    statusSubtitle.setTextColor(Color.parseColor("#F57C00"));

                    // Show clear codes button when there are codes
                    if (clearCodesBtn != null) {
                        clearCodesBtn.setVisibility(View.VISIBLE);
                    }
                } else {
                    // MIL is OFF - show normal status
                    statusIcon.setColorFilter(Color.parseColor("#4CAF50"));
                    statusText.setText("No Fault Codes");
                    statusSubtitle.setText("Engine running normally");
                    statusSubtitle.setTextColor(Color.parseColor("#757575"));

                    // Hide clear codes button when no codes
                    if (clearCodesBtn != null) {
                        clearCodesBtn.setVisibility(View.GONE);
                    }
                }
            }
        }

        // Freeze frames are now accessed through fault code modal - no menu item needed
    }

    /**
     * Set enabled state for a specified menu item
     * * this includes shading disabled items to visualize state
     *
     * @param id      ID of menu item
     * @param enabled flag if to be enabled/disabled
     */
    private void setMenuItemEnable(int id, boolean enabled)
    {
        if (menu != null)
        {
            MenuItem item = menu.findItem(id);
            if (item != null)
            {
                item.setEnabled(enabled);

                // if menu item has icon ...
                Drawable icon = item.getIcon();
                if (icon != null)
                {
                    // set it's shading
                    icon.setAlpha(enabled ? 255 : 127);
                }
            }
        }
    }

    /**
     * Set enabled state for a specified menu item
     * * this includes shading disabled items to visualize state
     *
     * @param id      ID of menu item
     * @param enabled flag if to be visible/invisible
     */
    private void setMenuItemVisible(int id, boolean enabled)
    {
        if (menu != null)
        {
            MenuItem item = menu.findItem(id);
            if (item != null)
            {
                item.setVisible(enabled);
            }
        }
    }

    /**
     * start/stop the autmatic toolbar hider
     */
    private void setAutoHider(boolean active)
    {
        // disable existing hider
        if (toolbarAutoHider != null)
        {
            // cancel auto hider
            toolbarAutoHider.cancel();
            // forget about it
            toolbarAutoHider = null;
        }

        // if new hider shall be activated
        if (active)
        {
            int timeout = getPrefsInt(MainActivity.PREF_AUTOHIDE_DELAY, 15);
            toolbarAutoHider = new AutoHider(this,
                    mHandler,
                    timeout * 1000);
            // start with update resolution of 1 second
            toolbarAutoHider.start(1000);
        }
    }

    /**
     * Get preference int value
     *
     * @param key          preference key name
     * @param defaultValue numeric default value
     * @return preference int value
     */
    @SuppressLint("DefaultLocale")
    private int getPrefsInt(String key, int defaultValue)
    {
        int result = defaultValue;

        try
        {
            result = Integer.valueOf(prefs.getString(key, String.valueOf(defaultValue)));
        } catch (Exception ex)
        {
            // log error message
            log.severe(String.format("Preference '%s'(%d): %s", key, result, ex.toString()));
        }

        return result;
    }

    /**
     * set listeners for data structure changes
     */
    private void setDataListeners()
    {
        // add pv change listeners to trigger model updates
        ObdProt.PidPvs.addPvChangeListener(this,
                PvChangeEvent.PV_ADDED
                        | PvChangeEvent.PV_CLEARED
        );
        ObdProt.VidPvs.addPvChangeListener(this,
                PvChangeEvent.PV_ADDED
                        | PvChangeEvent.PV_MODIFIED  // Also listen for updates to existing VINs
                        | PvChangeEvent.PV_CLEARED
        );
        ObdProt.TidPvs.addPvChangeListener(this,
                PvChangeEvent.PV_ADDED
                        | PvChangeEvent.PV_MODIFIED
                        | PvChangeEvent.PV_CLEARED
        );
        ObdProt.tCodes.addPvChangeListener(this,
                PvChangeEvent.PV_ADDED
                        | PvChangeEvent.PV_CLEARED
        );
    }

    /**
     * set listeners for data structure changes
     */
    private void removeDataListeners()
    {
        // remove pv change listeners
        ObdProt.PidPvs.removePvChangeListener(this);
        ObdProt.VidPvs.removePvChangeListener(this);
        ObdProt.TidPvs.removePvChangeListener(this);
        ObdProt.tCodes.removePvChangeListener(this);
    }

    /**
     * get current operating mode
     */
    private MODE getMode()
    {
        return mode;
    }

    /**
     * set new operating mode
     *
     * @param mode new mode
     */
    private void setMode(MODE mode)
    {
        // if this is a mode change, or file reload ...
        if (mode != this.mode || mode == MODE.FILE)
        {
            if (mode != MODE.DEMO)
            {
                stopDemoService();
            }

            // Disable data updates in FILE mode
            ObdItemAdapter.allowDataUpdates = (mode != MODE.FILE);

            switch (mode)
            {
                case OFFLINE:
                    // update menu item states
                    setMenuItemVisible(R.id.disconnect, false);
                    setMenuItemVisible(R.id.secure_connect_scan, true);
                    updateServiceMenuItems(false);
                    break;

                case ONLINE:
                    // Launch unified adapter selection activity
                    Intent adapterIntent = new Intent(this, UnifiedAdapterSelectionActivity.class);
                    launchActivityForResult(adapterIntent, REQUEST_CONNECT_UNIFIED);
                    break;

                case DEMO:
                    startDemoService();
                    break;

                case FILE:
                    setStatus(R.string.saved_data);
                    selectFileToLoad();
                    break;

            }
            // remember previous mode
            // set new mode
            this.mode = mode;
            // Set appropriate status message based on mode
            switch (mode) {
                case OFFLINE:
                    setStatus(getString(R.string.status_connect_device));
                    break;
                case ONLINE:
                    setStatus(getString(R.string.status_online));
                    break;
                case DEMO:
                    setStatus(getString(R.string.status_demo_mode));
                    break;
                case FILE:
                    setStatus(getString(R.string.status_viewing_saved));
                    break;
                default:
                    setStatus(mode.toString());
            }
        }
    }

    /**
     * set mesaurement conversion system to metric/imperial
     *
     * @param cnvId ID for metric/imperial conversion
     */
    private void setConversionSystem(int cnvId)
    {
        log.info("Conversion: " + getResources().getStringArray(R.array.measure_options)[cnvId]);
        if (EcuDataItem.cnvSystem != cnvId)
        {
            // set coversion system
            EcuDataItem.cnvSystem = cnvId;
        }
    }

    /**
     * Set up loggers
     */
    private void setupLoggers()
    {
        // set file handler for log file output
        String logFileName = FileHelper.getPath(this).concat(File.separator).concat("log");
        try
        {
            // ensure log directory is available
            //noinspection ResultOfMethodCallIgnored
            new File(logFileName).mkdirs();
            // Create new log file handler (max. 250 MB, 5 files rotated, non appending)
            logFileHandler = new FileHandler(logFileName.concat("/AndrOBD.log.%g.txt"),
                    250 * 1024 * 1024,
                    5,
                    false);
            // Set log message formatter
            logFileHandler.setFormatter(new SimpleFormatter()
            {
                final String format = "%1$tF\t%1$tT.%1$tL\t%4$s\t%3$s\t%5$s%n";

                @SuppressLint("DefaultLocale")
                @Override
                public synchronized String format(LogRecord lr)
                {
                    return String.format(format,
                            new Date(lr.getMillis()),
                            lr.getSourceClassName(),
                            lr.getLoggerName(),
                            lr.getLevel().getName(),
                            lr.getMessage()
                    );
                }
            });
            // add file logging ...
            rootLogger.addHandler(logFileHandler);
            // set
            setLogLevels();
        } catch (IOException e)
        {
            // try to log error (at least with system logging)
            log.log(Level.SEVERE, logFileName, e);
        }
    }

    /**
     * Set logging levels from shared preferences
     */
    private void setLogLevels()
    {
        // get level from preferences
        Level level;
        try
        {
            level = Level.parse(prefs.getString(LOG_MASTER, "INFO"));
        } catch (Exception e)
        {
            level = Level.INFO;
        }

        // set logger main level
        MainActivity.rootLogger.setLevel(level);
    }

    /**
     * Stop demo mode Thread
     */
    private void stopDemoService()
    {
        if (getMode() == MODE.DEMO)
        {
            ElmProt.runDemo = false;
            // Clear vehicle data when stopping demo
            VehicleManager.getInstance().clearVehicle();
            SnackbarHelper.showInfo(this, getString(R.string.demo_stopped));
        }
    }

    /**
     * Start demo mode Thread
     */
    private void startDemoService()
    {
        if (getMode() != MODE.DEMO)
        {
            // Reset ECU selection state
            ecuUserSelected = false;

            setStatus(getString(R.string.demo));
            // No snackbar - consistent with real device connection

            // Show disconnect button (green) since we're "connected" to demo
            setMenuItemVisible(R.id.secure_connect_scan, false);
            setMenuItemVisible(R.id.disconnect, true);

            updateServiceMenuItems(true);
            /* The Thread object for processing the demo mode loop */
            Thread demoThread = new Thread(CommService.elm);
            demoThread.start();

            // Ensure we stay on the main list view after starting demo mode
            setDataViewMode(DATA_VIEW_MODE.LIST);
        }
    }

    /**
     * Enable/disable individual service menu items based on connection state
     * Settings is always enabled in the toolbar
     * @param enable true to enable service items, false to disable
     */
    private void updateServiceMenuItems(boolean enable) {
        // Settings icon is now directly in the toolbar and always enabled
        // This method is kept for compatibility but no longer manages menu items
    }

    /**
     * set status message in status bar
     *
     * @param resId Resource ID of the text to be displayed
     */
    private void setStatus(int resId)
    {
        setStatus(getString(resId));
    }

    /**
     * set status message in status bar
     *
     * @param subTitle status text to be set
     */
    private void setStatus(CharSequence subTitle)
    {
        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null)
        {
            actionBar.setSubtitle(subTitle);
        }
    }

    /**
     * Select file to be loaded
     */
    private void selectFileToLoad()
    {
        File file = new File(FileHelper.getPath(this));
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        Uri uri = FileProvider.getUriForFile(MainActivity.this, getPackageName()+".provider", file);
        String type = "*/*";
        intent.setDataAndType(uri, type);
        launchActivityForResult(intent, REQUEST_SELECT_FILE);
    }


    /**
     * Initiate a connect to the selected bluetooth device
     *
     * @param address bluetooth device address
     * @param secure  flag to indicate if the connection shall be secure, or not
     */
    private void connectBtDevice(String address, boolean secure)
    {
        // Get the BluetoothDevice object
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        // Attempt to connect to the device
        mCommService = new BluetoothCommService(this, mHandler);
        mCommService.connect(device, secure);
    }

    /**
     * Initiate a connect to the selected network device
     *
     * @param address IP device address
     * @param port    IP port to connect to
     */
    private void connectNetworkDevice(String address, int port)
    {
        // Attempt to connect to the device
        mCommService = new NetworkCommService(this, mHandler);
        ((NetworkCommService) mCommService).connect(address, port);
    }

    /**
     * Update the subtitle text of the reconnect adapter card based on last connected adapter
     */
    private void updateReconnectCardSubtitle()
    {
        TextView subtitle = findViewById(R.id.reconnect_adapter_subtitle);
        if (subtitle == null) {
            log.fine("Reconnect card subtitle not found - layout may not be set yet");
            return;
        }

        String lastAdapterType = prefs.getString("LAST_ADAPTER_TYPE", null);
        String lastAdapterName = prefs.getString("LAST_ADAPTER_NAME", null);
        log.info("updateReconnectCardSubtitle - Last adapter type: " + lastAdapterType + ", name: " + lastAdapterName);

        if (lastAdapterType == null) {
            subtitle.setText("No adapter connected yet");
            log.info("No last adapter type found");
            return;
        }

        String subtitleText = "";
        switch (lastAdapterType) {
            case "BLUETOOTH":
                String btAddress = prefs.getString("LAST_DEV_ADDRESS", null);
                if (btAddress != null) {
                    // Check for device nickname first
                    String nickname = prefs.getString("device_nickname_" + btAddress, "");
                    if (!nickname.isEmpty()) {
                        subtitleText = "Reconnect to " + nickname;
                    } else if (lastAdapterName != null && !lastAdapterName.isEmpty()) {
                        subtitleText = "Reconnect to " + lastAdapterName;
                    } else {
                        subtitleText = "Reconnect to Bluetooth device";
                    }
                } else {
                    subtitleText = "No adapter connected yet";
                }
                break;
            case "NETWORK":
                String networkIp = prefs.getString("DEVICE_ADDRESS", null);
                int networkPort = prefs.getInt("DEVICE_PORT", 35000);
                if (networkIp != null) {
                    if (lastAdapterName != null && !lastAdapterName.isEmpty()) {
                        subtitleText = "Reconnect to " + lastAdapterName + " (" + networkIp + ":" + networkPort + ")";
                    } else {
                        subtitleText = "Reconnect to " + networkIp + ":" + networkPort;
                    }
                } else {
                    subtitleText = "No adapter connected yet";
                }
                break;
            case "USB":
                if (lastAdapterName != null && !lastAdapterName.isEmpty()) {
                    subtitleText = "Reconnect to " + lastAdapterName;
                } else {
                    subtitleText = "Reconnect to USB adapter";
                }
                break;
            default:
                subtitleText = "No adapter connected yet";
                break;
        }

        subtitle.setText(subtitleText);
    }

    /**
     * Reconnect to the last used adapter based on saved preferences
     */
    private void reconnectToLastAdapter()
    {
        log.info("reconnectToLastAdapter() called");

        View reconnectCard = findViewById(R.id.card_reconnect_adapter);

        // Check cooldown to prevent spam
        long currentTime = System.currentTimeMillis();
        long timeSinceLastReconnect = currentTime - lastReconnectTime;

        if (timeSinceLastReconnect < RECONNECT_COOLDOWN_MS) {
            long remainingSeconds = (RECONNECT_COOLDOWN_MS - timeSinceLastReconnect) / 1000 + 1;
            log.info("Reconnect cooldown active - " + remainingSeconds + " seconds remaining");
            SnackbarHelper.showInfo(this, "Please wait " + remainingSeconds + " second(s) before reconnecting again");
            return;
        }

        // Update last reconnect time
        lastReconnectTime = currentTime;

        // Disable the reconnect card for 10 seconds
        if (reconnectCard != null) {
            reconnectCard.setEnabled(false);
            reconnectCard.setAlpha(0.5f);
            log.info("Reconnect card disabled for " + (RECONNECT_COOLDOWN_MS / 1000) + " seconds");

            // Re-enable after cooldown
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                reconnectCard.setEnabled(true);
                reconnectCard.setAlpha(1.0f);
                log.info("Reconnect card re-enabled");
            }, RECONNECT_COOLDOWN_MS);
        }

        String lastAdapterType = prefs.getString("LAST_ADAPTER_TYPE", null);
        log.info("Attempting to reconnect - Last adapter type: " + lastAdapterType);

        if (lastAdapterType == null) {
            log.warning("No last adapter type found in SharedPreferences");
            SnackbarHelper.showWarning(this, "No previous adapter connection found. Please select an adapter.");
            return;
        }

        // Stop any existing communication service before reconnecting
        if (mCommService != null) {
            log.info("Stopping existing communication service before reconnect");
            mCommService.stop();
            mCommService = null;
        }

        try {
            switch (lastAdapterType) {
                case "BLUETOOTH":
                    String btAddress = prefs.getString("LAST_DEV_ADDRESS", null);
                    if (btAddress != null) {
                        connectBtDevice(btAddress, prefs.getBoolean("bt_secure_connection", false));
                        SnackbarHelper.showInfo(this, "Reconnecting to Bluetooth adapter...");
                    } else {
                        SnackbarHelper.showWarning(this, "No Bluetooth device address found. Please select an adapter.");
                    }
                    break;

                case "NETWORK":
                    String networkIp = prefs.getString("DEVICE_ADDRESS", null);
                    int networkPort = prefs.getInt("DEVICE_PORT", 35000);
                    if (networkIp != null) {
                        connectNetworkDevice(networkIp, networkPort);
                        SnackbarHelper.showInfo(this, "Reconnecting to network adapter...");
                    } else {
                        SnackbarHelper.showWarning(this, "No network address found. Please select an adapter.");
                    }
                    break;

                case "USB":
                    SnackbarHelper.showWarning(this, "USB reconnection requires manual device selection. Please use 'Select Adapter'.");
                    break;

                default:
                    SnackbarHelper.showWarning(this, "Unknown adapter type. Please select an adapter.");
                    break;
            }
        } catch (Exception e) {
            log.log(Level.WARNING, "Error reconnecting to adapter", e);
            SnackbarHelper.showError(this, "Failed to reconnect. Please select an adapter manually.");
        }
    }

    /**
     * Attempt auto-reconnect on startup if the setting is enabled
     * Only runs once per app session (first onResume)
     */
    private void attemptAutoReconnectIfEnabled() {
        // Only attempt once per session
        if (hasAttemptedAutoReconnect) {
            return;
        }

        // Check if auto-reconnect is enabled
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean autoReconnectEnabled = prefs.getBoolean("auto_reconnect_on_startup", false);

        if (!autoReconnectEnabled) {
            log.info("Auto-reconnect disabled");
            hasAttemptedAutoReconnect = true;
            return;
        }

        // Check if we have a last adapter to reconnect to
        String lastAdapterType = prefs.getString("LAST_ADAPTER_TYPE", null);
        if (lastAdapterType == null) {
            log.info("Auto-reconnect: No last adapter found");
            hasAttemptedAutoReconnect = true;
            return;
        }

        // Check if we're already connected
        if (mCommService != null && mCommService.getState() == CommService.STATE.CONNECTED) {
            log.info("Auto-reconnect: Already connected");
            hasAttemptedAutoReconnect = true;
            return;
        }

        // Check if we're in offline mode
        if (getMode() != MODE.OFFLINE) {
            log.info("Auto-reconnect: Not in offline mode");
            hasAttemptedAutoReconnect = true;
            return;
        }

        // All checks passed - attempt reconnect after a short delay
        // Delay ensures UI is fully initialized
        log.info("Auto-reconnect: Attempting to reconnect to " + lastAdapterType);
        hasAttemptedAutoReconnect = true;

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            reconnectToLastAdapter();
        }, 1000); // 1 second delay to ensure UI is ready
    }

    /**
     * Show dialog for auto-reconnect settings
     */
    private void showAutoReconnectSettingsDialog() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean currentAutoReconnect = prefs.getBoolean("auto_reconnect_on_startup", false);

        // Inflate custom layout
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_auto_reconnect_settings, null);

        // Get references to views
        android.widget.CheckBox checkbox = dialogView.findViewById(R.id.checkbox_auto_reconnect);
        android.widget.LinearLayout lastAdapterInfo = dialogView.findViewById(R.id.last_adapter_info);
        TextView lastAdapterName = dialogView.findViewById(R.id.last_adapter_name);
        Button cancelButton = dialogView.findViewById(R.id.btn_cancel);
        Button saveButton = dialogView.findViewById(R.id.btn_save);

        // Set current value
        checkbox.setChecked(currentAutoReconnect);

        // Show last adapter info if available
        String lastAdapterType = prefs.getString("LAST_ADAPTER_TYPE", null);
        if (lastAdapterType != null) {
            lastAdapterInfo.setVisibility(View.VISIBLE);
            String displayName = "";

            switch (lastAdapterType) {
                case "BLUETOOTH":
                    String btAddress = prefs.getString("LAST_DEV_ADDRESS", null);
                    if (btAddress != null) {
                        // Check for device nickname first (most specific)
                        String nickname = prefs.getString("device_nickname_" + btAddress, "");
                        if (!nickname.isEmpty()) {
                            displayName = nickname + " (Bluetooth)";
                        } else {
                            // Fall back to saved adapter name
                            String savedName = prefs.getString("LAST_ADAPTER_NAME", null);
                            if (savedName != null && !savedName.isEmpty()) {
                                displayName = savedName + " (Bluetooth)";
                            } else {
                                displayName = "Bluetooth Device";
                            }
                        }
                    } else {
                        displayName = "Bluetooth Device";
                    }
                    break;
                case "NETWORK":
                    String ip = prefs.getString("DEVICE_ADDRESS", "Unknown");
                    int port = prefs.getInt("DEVICE_PORT", 35000);
                    displayName = ip + ":" + port + " (Network)";
                    break;
                case "USB":
                    displayName = "USB Adapter";
                    break;
                default:
                    displayName = "Unknown Adapter";
                    break;
            }

            lastAdapterName.setText(displayName);
        }

        // Create dialog
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        // Remove default background to show our rounded corners
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        // Set up button listeners
        cancelButton.setOnClickListener(v -> dialog.dismiss());

        saveButton.setOnClickListener(v -> {
            boolean newValue = checkbox.isChecked();
            prefs.edit().putBoolean("auto_reconnect_on_startup", newValue).apply();

            String message = newValue ?
                "Auto-reconnect enabled. The app will reconnect on startup." :
                "Auto-reconnect disabled. You'll need to manually reconnect.";
            SnackbarHelper.showInfo(MainActivity.this, message);

            log.info("Auto-reconnect setting changed to: " + newValue);
            dialog.dismiss();
        });

        dialog.show();
    }

    /**
     * Detect connection cycles (Connecting -> No Data -> Connecting)
     * Shows a dialog after repeated cycles to help user understand unsupported mode
     */
    private void detectConnectionCycles(ElmProt.STAT newState) {
        // Only track cycles for specific modes that commonly fail
        if (CommService.elm == null) {
            log.info("detectConnectionCycles: CommService.elm is null");
            return;
        }

        int currentService = CommService.elm.getService();
        log.info(String.format("detectConnectionCycles: service=%s (%d), newState=%s, lastState=%s",
            ObdProt.getServiceName(currentService), currentService, newState, lastConnectionState));

        // Only detect cycles for Mode 8 (Test Control) and Mode 6 (Monitor Test)
        // These are commonly unsupported
        if (currentService != ObdProt.OBD_SVC_CTRL_MODE &&
            currentService != ObdProt.OBD_SVC_MON_RESULT) {
            log.info("detectConnectionCycles: Not Mode 8 or 6, resetting");
            resetCycleDetection();
            return;
        }
        log.info("detectConnectionCycles: Tracking cycles for this service");

        // Check for timeout - reset if no cycles for a while
        long currentTime = System.currentTimeMillis();
        if (lastCycleTimestamp > 0 && (currentTime - lastCycleTimestamp) > CYCLE_RESET_TIMEOUT_MS) {
            log.info("Cycle detection timeout - resetting");
            resetCycleDetection();
        }

        // Detect the cycle: CONNECTING -> NODATA (or vice versa)
        boolean isCycle = false;
        if ((lastConnectionState == ElmProt.STAT.CONNECTING && newState == ElmProt.STAT.NODATA) ||
            (lastConnectionState == ElmProt.STAT.NODATA && newState == ElmProt.STAT.CONNECTING)) {
            isCycle = true;
            log.info("detectConnectionCycles: *** CYCLE DETECTED ***");
        } else {
            log.info("detectConnectionCycles: No cycle detected in this transition");
        }

        if (isCycle) {
            // Track the service where cycles started
            if (serviceWhenCycleStarted == ObdProt.OBD_SVC_NONE) {
                serviceWhenCycleStarted = currentService;
                // Only save as backup if we don't already have a saved state
                // (The state should have been saved when clicking Test Control card)
                if (ecuStateBeforeUnsupportedMode == ElmProt.STAT.UNDEFINED) {
                    // Save the good ECU state before we start cycling into NODATA
                    // Check if we were in a good connected state before this cycle
                    if (lastConnectionState == ElmProt.STAT.CONNECTED ||
                        lastConnectionState == ElmProt.STAT.ECU_DETECTED ||
                        lastConnectionState == ElmProt.STAT.ECU_SELECTED) {
                        ecuStateBeforeUnsupportedMode = lastConnectionState;
                        log.info("Backup: Saved ECU state from lastConnectionState: " + ecuStateBeforeUnsupportedMode);
                    } else if (ecuConnectionState == ElmProt.STAT.CONNECTED ||
                               ecuConnectionState == ElmProt.STAT.ECU_DETECTED ||
                               ecuConnectionState == ElmProt.STAT.ECU_SELECTED) {
                        // If lastConnectionState wasn't good, use current ecuConnectionState
                        ecuStateBeforeUnsupportedMode = ecuConnectionState;
                        log.info("Backup: Saved ECU state from ecuConnectionState: " + ecuStateBeforeUnsupportedMode);
                    }
                } else {
                    log.info("ECU state already saved: " + ecuStateBeforeUnsupportedMode);
                }
            }

            // Only count if it's the same service
            if (serviceWhenCycleStarted == currentService) {
                connectionCycleCount++;
                lastCycleTimestamp = currentTime;
                log.info(String.format("Connection cycle detected for service %s: count=%d",
                    ObdProt.getServiceName(currentService), connectionCycleCount));

                // Show dialog after threshold
                if (connectionCycleCount >= MAX_CYCLES_BEFORE_ALERT && unsupportedModeDialog == null) {
                    showUnsupportedModeDialog(currentService);
                }
            }
        }

        lastConnectionState = newState;
    }

    /**
     * Reset cycle detection tracking
     */
    private void resetCycleDetection() {
        connectionCycleCount = 0;
        serviceWhenCycleStarted = ObdProt.OBD_SVC_NONE;
        lastCycleTimestamp = 0;
        lastConnectionState = ElmProt.STAT.UNDEFINED;
        ecuStateBeforeUnsupportedMode = ElmProt.STAT.UNDEFINED;
    }

    /**
     * Show dialog explaining that the mode is not supported
     */
    private void showUnsupportedModeDialog(int obdService) {
        if (unsupportedModeDialog != null && unsupportedModeDialog.isShowing()) {
            return; // Already showing
        }

        String serviceName = ObdProt.getServiceName(obdService);
        String message;

        switch (obdService) {
            case ObdProt.OBD_SVC_CTRL_MODE:
                message = "Test Control (Mode 8) is not supported by this vehicle.\n\n" +
                         "This mode is used for specialized diagnostic tests like evaporative system leak tests " +
                         "and is rarely supported by consumer vehicles or OBD emulators.\n\n" +
                         "This is completely normal.";
                break;

            case ObdProt.OBD_SVC_MON_RESULT:
                message = "Monitor Test Results (Mode 6) is not supported by this vehicle.\n\n" +
                         "This mode provides detailed emissions monitoring test results and is not supported " +
                         "by all vehicles.\n\n" +
                         "This is normal for many vehicles.";
                break;

            default:
                message = String.format("%s is not responding.\n\n" +
                         "This feature may not be supported by your vehicle.", serviceName);
                break;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Mode Not Supported")
               .setMessage(message)
               .setCancelable(false)
               .setNegativeButton("Keep Trying", (dialog, which) -> {
                   // Reset cycle detection to allow user to keep trying
                   resetCycleDetection();
                   dialog.dismiss();
                   unsupportedModeDialog = null;
               })
               .setPositiveButton("Go Back", (dialog, which) -> {
                   // Return to dashboard
                   log.info("Go Back button clicked - ecuConnectionState: " + ecuConnectionState);

                   resetCycleDetection();
                   dialog.dismiss();
                   unsupportedModeDialog = null;

                   // Always trigger ECU reconnection after unsupported mode
                   if (CommService.elm != null) {
                       log.info("Triggering ECU reconnection after unsupported mode");
                       // Reset the ELM adapter to trigger fresh ECU detection
                       // This is the same flow as when you first connect to the adapter
                       CommService.elm.reset();
                   } else {
                       log.warning("Cannot reset - CommService.elm is null");
                   }

                   // Return to dashboard
                   setObdService(ObdProt.OBD_SVC_NONE, null);
               });

        unsupportedModeDialog = builder.create();
        unsupportedModeDialog.show();

        log.info(String.format("Showed unsupported mode dialog for %s after %d cycles",
            serviceName, connectionCycleCount));
    }

    /**
     * Activate desired OBD service
     *
     * @param newObdService OBD service ID to be activated
     */
    private void setObdService(int newObdService, CharSequence menuTitle)
    {
        // remember this as current OBD service
        obdService = newObdService;
        ignoreNrcs = false;

        // Reset cycle detection when switching services
        resetCycleDetection();

        // set list view
        setContentView(mListView);
        listView = findViewById(android.R.id.list);
        if (listView != null) {
            listView.setOnItemLongClickListener(this);
            listView.setOnItemClickListener(this);
            listView.setMultiChoiceModeListener(this);
            // Use CHOICE_MODE_NONE for fault codes to prevent greyed-out selection state
            if (newObdService == ObdProt.OBD_SVC_READ_CODES ||
                newObdService == ObdProt.OBD_SVC_PERMACODES ||
                newObdService == ObdProt.OBD_SVC_PENDINGCODES) {
                listView.setChoiceMode(ListView.CHOICE_MODE_NONE);
            } else {
                listView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
            }
        }

        // Hide clear codes button by default (will be shown for fault codes)
        View clearCodesBtn = findViewById(R.id.clear_codes_button);
        if (clearCodesBtn != null) {
            clearCodesBtn.setVisibility(View.GONE);
        }

        // Hide MIL status card by default (only show for fault codes pages)
        View milStatusCard = findViewById(R.id.mil_status_card);
        if (milStatusCard != null) {
            milStatusCard.setVisibility(View.GONE);
        }

        // Set action bar title if provided
        ActionBar ab = getSupportActionBar();
        if (ab != null)
        {
            ab.show();
            if (menuTitle != null)
            {
                ab.setTitle(menuTitle.toString());
            }
            else if (newObdService == ElmProt.OBD_SVC_NONE)
            {
                ab.setTitle(getString(R.string.app_name));
            }
        }
        // set protocol service
        CommService.elm.setService(newObdService, (getMode() != MODE.FILE && getMode() != MODE.OFFLINE));
        // show / hide freeze frame selector */
        Spinner ff_selector = findViewById(R.id.ff_selector);
        ff_selector.setOnItemSelectedListener(ff_selected);
        ff_selector.setAdapter(mDfcAdapter);
        ff_selector.setVisibility(
                newObdService == ObdProt.OBD_SVC_FREEZEFRAME ? View.VISIBLE : View.GONE);
        // set corresponding list adapter
        switch (newObdService)
        {
            case ObdProt.OBD_SVC_DATA:
                if (listView != null) {
                    listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
                }
                // no break here
            case ObdProt.OBD_SVC_FREEZEFRAME:
                currDataAdapter = mPidAdapter;
                break;

            case ObdProt.OBD_SVC_PENDINGCODES:
            case ObdProt.OBD_SVC_PERMACODES:
            case ObdProt.OBD_SVC_READ_CODES:
                // NOT all DFC modes are supported by all vehicles, disable NRC handling for this request
                ignoreNrcs = true;
                currDataAdapter = mDfcAdapter;

                // Update status to show proper ECU state for fault codes view
                // Preserve "ECU Selected" if user manually selected an ECU
                if (ecuUserSelected) {
                    // User manually selected ECU - always show "ECU Selected"
                    setStatus(getResources().getStringArray(R.array.elmcomm_states)[ElmProt.STAT.ECU_SELECTED.ordinal()]);
                } else if (ecuConnectionState == ElmProt.STAT.CONNECTED ||
                           ecuConnectionState == ElmProt.STAT.ECU_DETECTED) {
                    // Auto-detected ECU - show actual state
                    setStatus(getResources().getStringArray(R.array.elmcomm_states)[ecuConnectionState.ordinal()]);
                }

                // Setup clear codes button within the MIL status card
                View clearBtn = findViewById(R.id.clear_codes_button);
                if (clearBtn != null) {
                    // Button visibility will be controlled by updateMilStatusCard based on fault code count
                    clearBtn.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            clearObdFaultCodes();
                        }
                    });
                }

                // Show MIL status card only for fault codes screens
                View statusCard = findViewById(R.id.mil_status_card);
                if (statusCard != null) {
                    statusCard.setVisibility(View.VISIBLE);
                }
                break;

            case ObdProt.OBD_SVC_CTRL_MODE:
                currDataAdapter = mTidAdapter;
                break;

            case ObdProt.OBD_SVC_NONE:
                setContentView(R.layout.startup_layout);
                // Set to null since we're on the startup screen
                currDataAdapter = null;

                // Wire up footer overlay
                setupFooterOverlay();

                // Set up dashboard card click listeners
                setupDashboardCards();

                // Update status to show proper ECU state when returning to dashboard
                // This ensures status is refreshed from "No Data" or other service-specific states
                if (ecuUserSelected) {
                    // User manually selected ECU - always show "ECU Selected"
                    setStatus(getResources().getStringArray(R.array.elmcomm_states)[ElmProt.STAT.ECU_SELECTED.ordinal()]);
                } else if (ecuConnectionState == ElmProt.STAT.CONNECTED ||
                           ecuConnectionState == ElmProt.STAT.ECU_DETECTED) {
                    // Auto-detected ECU - show actual state
                    setStatus(getResources().getStringArray(R.array.elmcomm_states)[ecuConnectionState.ordinal()]);
                }
                // If disconnected/offline, the status will be handled by the mode-specific logic below
                break;
        }

        // un-filter display
        setFiltered(false);

        if (listView != null) {
            listView.setAdapter(currDataAdapter);
        }

        // remember this as last selected service
    }

    /**
     * Set up click listeners for dashboard cards
     */
    private void setupDashboardCards() {
        // Find and set up Live Data card
        View liveDataCard = findViewById(R.id.card_live_data);
        if (liveDataCard != null) {
            log.info("Live Data card found and setting up click listener");
            addCardPressAnimation(liveDataCard);
            liveDataCard.setOnClickListener(v -> {
                log.info("Live Data card clicked!");
                if (ecuConnectionState == ElmProt.STAT.ECU_DETECTED ||
                    ecuConnectionState == ElmProt.STAT.CONNECTED) {
                    setObdService(ObdProt.OBD_SVC_DATA, "Live Data");
                } else {
                    SnackbarHelper.showWarning(this, "Please connect to vehicle first");
                }
            });
        } else {
            log.warning("Live Data card NOT found!");
        }

        // Find and set up Test Control card
        View testControlCard = findViewById(R.id.card_test_control);
        if (testControlCard != null) {
            log.info("Test Control card found and setting up click listener");
            addCardPressAnimation(testControlCard);
            testControlCard.setOnClickListener(v -> {
                log.info("Test Control card clicked!");
                if (ecuConnectionState == ElmProt.STAT.ECU_DETECTED ||
                    ecuConnectionState == ElmProt.STAT.CONNECTED ||
                    ecuConnectionState == ElmProt.STAT.ECU_SELECTED) {
                    // Save current good ECU state before entering potentially unsupported mode
                    ecuStateBeforeUnsupportedMode = ecuConnectionState;
                    log.info("Saved ECU state before entering Test Control: " + ecuStateBeforeUnsupportedMode);
                    setObdService(ObdProt.OBD_SVC_CTRL_MODE, "Test Control");
                } else {
                    SnackbarHelper.showWarning(this, "Please connect to vehicle first");
                }
            });
        } else {
            log.warning("Test Control card NOT found!");
        }

        // Find and set up Fault Codes card
        View faultCodesCard = findViewById(R.id.card_fault_codes);
        if (faultCodesCard != null) {
            addCardPressAnimation(faultCodesCard);
            faultCodesCard.setOnClickListener(v -> {
                if (ecuConnectionState == ElmProt.STAT.ECU_DETECTED ||
                    ecuConnectionState == ElmProt.STAT.CONNECTED) {
                    setObdService(ObdProt.OBD_SVC_READ_CODES, "Fault Codes");
                } else {
                    SnackbarHelper.showWarning(this, "Please connect to vehicle first");
                }
            });
        }

        // Find and set up Reconnect to Last Adapter card
        View reconnectCard = findViewById(R.id.card_reconnect_adapter);
        if (reconnectCard != null) {
            addCardPressAnimation(reconnectCard);
            // Check if we're still in cooldown period
            long currentTime = System.currentTimeMillis();
            long timeSinceLastReconnect = currentTime - lastReconnectTime;

            if (timeSinceLastReconnect < RECONNECT_COOLDOWN_MS && lastReconnectTime > 0) {
                // Still in cooldown - keep it disabled and schedule re-enable
                reconnectCard.setEnabled(false);
                reconnectCard.setAlpha(0.5f);
                long remainingCooldown = RECONNECT_COOLDOWN_MS - timeSinceLastReconnect;
                log.fine("Reconnect card still in cooldown - " + remainingCooldown + "ms remaining");

                // Schedule re-enable for when cooldown expires
                View finalReconnectCard = reconnectCard;
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    finalReconnectCard.setEnabled(true);
                    finalReconnectCard.setAlpha(1.0f);
                    log.info("Reconnect card re-enabled after cooldown");
                }, remainingCooldown);
            } else {
                // Not in cooldown - enable it
                reconnectCard.setEnabled(true);
                reconnectCard.setAlpha(1.0f);
            }

            reconnectCard.setOnClickListener(v -> {
                reconnectToLastAdapter();
            });

            // Add long-press to open auto-reconnect settings
            reconnectCard.setOnLongClickListener(v -> {
                showAutoReconnectSettingsDialog();
                return true; // Consume the long-click event
            });
        }

        // Update the reconnect card subtitle
        updateReconnectCardSubtitle();
    }

    /**
     * Wire up footer overlay to close footer when clicking outside
     */
    private void setupFooterOverlay() {
        vehicleInfoFooter = findViewById(R.id.vehicle_footer);
        View overlay = findViewById(R.id.footer_overlay);

        if (vehicleInfoFooter != null && overlay != null) {
            vehicleInfoFooter.setOverlayView(overlay);
            log.info("Footer overlay wired up successfully");
        } else {
            log.warning("Could not find footer or overlay view");
        }
    }

    /**
     * Add tactile press animation to a card view
     */
    @SuppressLint("ClickableViewAccessibility")
    private void addCardPressAnimation(View card) {
        card.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    // Scale down slightly when pressed
                    v.animate()
                        .scaleX(0.97f)
                        .scaleY(0.97f)
                        .setDuration(100)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator())
                        .start();
                    break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    // Scale back to normal when released
                    v.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator())
                        .start();
                    break;
            }
            return false; // Let the click listener handle the click
        });
    }

    /**
     * Filter display items to just the selected ones
     */
    private void setFiltered(boolean filtered)
    {
        if (filtered)
        {
            if (currDataAdapter == null) {
                log.warning("currDataAdapter is null, skipping filter");
                return;
            }
            TreeSet<Integer> selPids = new TreeSet<>();
            int[] selectedPositions = getSelectedPositions();
            for (int pos : selectedPositions)
            {
                EcuDataPv pv = (EcuDataPv) currDataAdapter.getItem(pos);
                selPids.add(pv != null ? pv.getAsInt(EcuDataPv.FID_PID) : 0);
            }
            currDataAdapter.filterPositions(selectedPositions);

            if (currDataAdapter == mPidAdapter)
                setFixedPids(selPids);
        } else
        {
            if (currDataAdapter == mPidAdapter)
                ObdProt.resetFixedPid();

            /* Return to original PV list */
            if (currDataAdapter == mPidAdapter)
            {
                currDataAdapter.setPvList(ObdProt.PidPvs);
            } else if (currDataAdapter == mDfcAdapter)
                currDataAdapter.setPvList(ObdProt.tCodes);

        }
    }

    /**
     * get the Position in model of the selected items
     *
     * @return Array of selected item positions
     */
    private int[] getSelectedPositions()
    {
        int[] selectedPositions;
        // SparseBoolArray - what a garbage data type to return ...
        final SparseBooleanArray checkedItems = listView != null ? listView.getCheckedItemPositions() : new SparseBooleanArray();
        // get number of items
        int checkedItemsCount = listView != null ? listView.getCheckedItemCount() : 0;
        // dimension array
        selectedPositions = new int[checkedItemsCount];
        if (checkedItemsCount > 0)
        {
            int j = 0;
            // loop through findings
            for (int i = 0; i < checkedItems.size(); i++)
            {
                // Item position in adapter
                if (checkedItems.valueAt(i))
                {
                    selectedPositions[j++] = checkedItems.keyAt(i);
                }
            }
            // trim to really detected value (workaround for invalid length reported)
            selectedPositions = Arrays.copyOf(selectedPositions, j);
        }
        String strPreselect = Arrays.toString(selectedPositions);
        log.fine("Preselection: '" + strPreselect + "'");
        return selectedPositions;
    }

    /**
     * Set selection status on specified list item positions
     *
     * @param positions list of positions to be set
     * @return flag if selections could be applied
     */
    private boolean selectDataItems(int[] positions)
    {
        int count;
        int max;
        boolean positionsValid;

        Arrays.sort(positions);
        max = positions.length > 0 ? positions[positions.length - 1] : 0;
        count = currDataAdapter != null ? currDataAdapter.getCount() : 0;
        positionsValid = (max < count);
        // if all positions are valid for current list ...
        if (positionsValid)
        {
            // set list items as selected
            for (int i : positions)
            {
                getListView().setItemChecked(i, true);
            }
        }

        // return validity of positions
        return positionsValid;
    }

    /**
     * Handle bluetooth connection established ...
     */
    @SuppressLint("StringFormatInvalid")
    private void onConnect()
    {
        stopDemoService();

        // Reset ECU selection state for new connection
        ecuUserSelected = false;

        mode = MODE.ONLINE;
        // handle further initialisations
        setMenuItemVisible(R.id.secure_connect_scan, false);
        setMenuItemVisible(R.id.disconnect, true);

        updateServiceMenuItems(true);
        // display connection status
        setStatus(getString(R.string.title_connected_to, mConnectedDeviceName));
        // send RESET to Elm adapter
        CommService.elm.reset();

        // Stay on main screen after connection (don't auto-select service)
        setObdService(ObdProt.OBD_SVC_NONE, null);
        // Ensure we stay on the main list view after connection
        setDataViewMode(DATA_VIEW_MODE.LIST);
    }

    /**
     * Handle bluetooth connection lost ...
     */
    private void onDisconnect()
    {
        // Clear vehicle data on disconnect
        VehicleManager.getInstance().clearVehicle();

        // Stop communication service to ensure clean disconnect
        if (mCommService != null) {
            mCommService.stop();
            log.info("Stopped communication service on disconnect");
        }

        // handle further initialisations
        setMode(MODE.OFFLINE);
        // Reset ECU connection state
        ecuConnectionState = ElmProt.STAT.UNDEFINED;
        ecuUserSelected = false;
        // Return to main screen
        setObdService(ObdProt.OBD_SVC_NONE, null);
    }

    /**
     * Property change listener to ELM-Protocol
     *
     * @param evt the property change event to be handled
     */
    public void propertyChange(PropertyChangeEvent evt)
    {
        /* handle protocol status changes */
        if (ElmProt.PROP_STATUS.equals(evt.getPropertyName()))
        {
            // forward property change to the UI Activity
            Message msg = mHandler.obtainMessage(MESSAGE_OBD_STATE_CHANGED);
            msg.obj = evt;
            mHandler.sendMessage(msg);
        } else
        {
            if (ElmProt.PROP_NUM_CODES.equals(evt.getPropertyName()))
            {
                // forward property change to the UI Activity
                Message msg = mHandler.obtainMessage(MESSAGE_OBD_NUMCODES);
                msg.obj = evt;
                mHandler.sendMessage(msg);
            } else
            {
                if (ElmProt.PROP_ECU_ADDRESS.equals(evt.getPropertyName()))
                {
                    // forward property change to the UI Activity
                    Message msg = mHandler.obtainMessage(MESSAGE_OBD_ECUS);
                    msg.obj = evt;
                    mHandler.sendMessage(msg);
                } else
                {
                    if (ObdProt.PROP_NRC.equals(evt.getPropertyName()))
                    {
                        // forward property change to the UI Activity
                        Message msg = mHandler.obtainMessage(MESSAGE_OBD_NRC);
                        msg.obj = evt;
                        mHandler.sendMessage(msg);
                    }
                }
            }
        }
    }

    /**
     * clear OBD fault codes after a warning
     * confirmation dialog is shown and the operation is confirmed
     */
    private void clearObdFaultCodes()
    {
        // Create custom dialog view
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_clear_codes, null);

        // Create the dialog
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        // Set up button click handlers
        Button cancelButton = dialogView.findViewById(R.id.btn_cancel);
        Button confirmButton = dialogView.findViewById(R.id.btn_confirm);

        if (cancelButton != null) {
            cancelButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                }
            });
        }

        if (confirmButton != null) {
            confirmButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // Check if elm is available
                    if (CommService.elm == null) {
                        SnackbarHelper.showError(MainActivity.this, "OBD adapter not connected");
                        dialog.dismiss();
                        return;
                    }

                    // Save the current service to restore later
                    final int previousService = CommService.elm.getService();

                    // Show feedback that clear codes is in progress
                    SnackbarHelper.showInfo(MainActivity.this, "Clearing fault codes...");

                    // set service CLEAR_CODES to clear the codes
                    CommService.elm.setService(ObdProt.OBD_SVC_CLEAR_CODES);

                    // Wait for clear codes operation to complete, then re-read
                    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            // Check if still connected
                            if (CommService.elm == null) {
                                SnackbarHelper.showError(MainActivity.this, "Connection lost during clear operation");
                                return;
                            }

                            // Show feedback that we're re-reading codes
                            SnackbarHelper.showInfo(MainActivity.this, "Re-reading fault codes...");

                            // Clear the current codes display first
                            runOnUiThread(() -> {
                                ObdProt.tCodes.clear();
                                if (mDfcAdapter != null) {
                                    mDfcAdapter.notifyDataSetChanged();
                                }
                            });

                            // set service READ_CODES to re-read the codes
                            CommService.elm.setService(ObdProt.OBD_SVC_READ_CODES);

                            // Update status immediately after switching to READ_CODES
                            // Always preserve "ECU Selected" if user manually selected
                            if (ecuUserSelected) {
                                setStatus(getResources().getStringArray(R.array.elmcomm_states)[ElmProt.STAT.ECU_SELECTED.ordinal()]);
                            } else if (ecuConnectionState == ElmProt.STAT.CONNECTED ||
                                       ecuConnectionState == ElmProt.STAT.ECU_DETECTED) {
                                setStatus(getResources().getStringArray(R.array.elmcomm_states)[ecuConnectionState.ordinal()]);
                            }

                            // After another delay, check if codes were cleared successfully and restore service
                            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                if (ObdProt.tCodes.size() <= 1) {
                                    SnackbarHelper.showSuccess(MainActivity.this, "Fault codes cleared successfully");
                                } else {
                                    SnackbarHelper.showWarning(MainActivity.this, "Codes cleared. Found " + (ObdProt.tCodes.size() - 1) + " code(s) still present");
                                }

                                // Restore the previous service after a short delay
                                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                    if (CommService.elm != null && previousService != ObdProt.OBD_SVC_CLEAR_CODES) {
                                        // Return to the previous service (usually OBD_SVC_DATA for live data)
                                        CommService.elm.setService(previousService);

                                        // Update status to show proper state after clearing codes
                                        // Always preserve "ECU Selected" if user manually selected
                                        if (ecuUserSelected) {
                                            setStatus(getResources().getStringArray(R.array.elmcomm_states)[ElmProt.STAT.ECU_SELECTED.ordinal()]);
                                        } else if (ecuConnectionState == ElmProt.STAT.CONNECTED ||
                                                   ecuConnectionState == ElmProt.STAT.ECU_DETECTED) {
                                            setStatus(getResources().getStringArray(R.array.elmcomm_states)[ecuConnectionState.ordinal()]);
                                        }
                                    }
                                }, 500);
                            }, 2000);
                        }
                    }, 1500); // Wait 1.5 seconds for clear codes to complete

                    dialog.dismiss();
                }
            });
        }

        dialog.show();
    }

    /**
     * Show styled disconnect confirmation dialog
     */
    private void showDisconnectConfirmDialog()
    {
        // Create custom dialog view
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_disconnect_confirm, null);

        // Create the dialog
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        // Set up button click handlers
        Button cancelButton = dialogView.findViewById(R.id.btn_cancel);
        Button confirmButton = dialogView.findViewById(R.id.btn_confirm);

        if (cancelButton != null) {
            cancelButton.setOnClickListener(v -> dialog.dismiss());
        }

        if (confirmButton != null) {
            confirmButton.setOnClickListener(v -> {
                // stop communication service
                if (mCommService != null)
                {
                    mCommService.stop();
                }
                setMode(MODE.OFFLINE);
                // Reset ECU connection state
                ecuConnectionState = ElmProt.STAT.UNDEFINED;
                ecuUserSelected = false;
                // Clear vehicle data
                VehicleManager.getInstance().clearVehicle();
                // Return to main screen
                setObdService(ObdProt.OBD_SVC_NONE, null);
                dialog.dismiss();
            });
        }

        dialog.show();
    }

    /**
     * confirm OBD test control
     * confirmation dialog is shown and the operation is confirmed
     */
    private void confirmObdTestControl(String testControlName, int service, int tid)
    {
        new AlertDialog.Builder(this)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle(testControlName)
                .setMessage(R.string.obd_test_confirm)
                .setPositiveButton(R.string.yes,
                        new DialogInterface.OnClickListener()
                        {
                            @Override
                            public void onClick(DialogInterface dialog, int which)
                            {
                                runObdTestControl(testControlName, service, tid);
                            }
                        })
                .setNegativeButton(R.string.no, null)
                .show();
    }

    /**
     * perform OBD test control
     * confirmation dialog is shown and the operation is confirmed
     */
    private void runObdTestControl(String testControlName, int service, int tid)
    {
        // start desired test TID
        char emptyBuffer[] = {};
        CommService.elm.writeTelegram(emptyBuffer, service, tid);

        // Show test progress message
        new AlertDialog.Builder(this)
                .setIcon(android.R.drawable.ic_dialog_info)
                .setTitle(testControlName)
                .setMessage(R.string.obd_test_progress)
                .setPositiveButton(android.R.string.ok,
                        new DialogInterface.OnClickListener()
                        {
                            @Override
                            public void onClick(DialogInterface dialog, int which)
                            {
                            }
                        })
                .show();
    }

    /**
     * Set new data view mode
     *
     * @param dataViewMode new data view mode
     */
    private void setDataViewMode(DATA_VIEW_MODE dataViewMode)
    {
        // if this is a real change ...
        if (dataViewMode != this.dataViewMode)
        {
            log.info(String.format("Set view mode: %s -> %s", this.dataViewMode, dataViewMode));

            switch (dataViewMode)
            {
                case LIST:
                    setFiltered(false);
                    ListView lv = getListView();
                    if (lv != null) {
                        lv.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
                    }
                    this.dataViewMode = dataViewMode;
                    break;

                case FILTERED:
                    ListView lv2 = getListView();
                    if (lv2 != null && lv2.getCheckedItemCount() > 0)
                    {
                        setFiltered(true);
                        lv2.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
                        this.dataViewMode = dataViewMode;
                    }
                    break;

                case HEADUP:
                case DASHBOARD:
                    ListView lv3 = getListView();
                    if (lv3 != null && lv3.getCheckedItemCount() > 0)
                    {
                        DashBoardActivity.setAdapter(currDataAdapter);
                        Intent intent = new Intent(this, DashBoardActivity.class);
                        intent.putExtra(DashBoardActivity.POSITIONS, getSelectedPositions());
                        intent.putExtra(DashBoardActivity.RES_ID,
                                dataViewMode == DATA_VIEW_MODE.DASHBOARD
                                        ? R.layout.dashboard
                                        : R.layout.head_up);
                        launchActivityForResult(intent, REQUEST_GRAPH_DISPLAY_DONE);
                        this.dataViewMode = dataViewMode;
                    }
                    break;

                case CHART:
                    ListView lv4 = getListView();
                    if (lv4 != null && lv4.getCheckedItemCount() > 0)
                    {
                        ChartActivity.setAdapter(currDataAdapter);
                        Intent intent = new Intent(this, ChartActivity.class);
                        intent.putExtra(ChartActivity.POSITIONS, getSelectedPositions());
                        launchActivityForResult(intent, REQUEST_GRAPH_DISPLAY_DONE);
                        this.dataViewMode = dataViewMode;
                    }
                    break;
            }

            // remember this as the last data view mode (if not regular list)
        }
    }

    /**
     * operating modes
     */
    public enum MODE
    {
        OFFLINE,//< OFFLINE mode
        ONLINE,    //< ONLINE mode
        DEMO,    //< DEMO mode
        FILE,   //< FILE mode
    }

    /**
     * data view modes
     */
    public enum DATA_VIEW_MODE
    {
        LIST,       //< data list (un-filtered)
        FILTERED,   //< data list (filtered)
        DASHBOARD,  //< dashboard
        HEADUP,     //< Head up display
        CHART,        //< Chart display
    }

    /**
     * Wrapper for deprecated startActivityForResult - suppresses deprecation warning
     * TODO: Migrate to Activity Result API in future refactor
     */
    @SuppressWarnings("deprecation")
    private void launchActivityForResult(Intent intent, int requestCode) {
        startActivityForResult(intent, requestCode);
    }

}