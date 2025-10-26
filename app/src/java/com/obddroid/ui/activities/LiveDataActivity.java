package com.obddroid.ui.activities;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AbsListView;
import android.widget.ListView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.obddroid.R;
import com.obddroid.core.obd.ObdProt;
import com.obddroid.core.pvs.ProcessVariables.ProcessVar;
import com.obddroid.core.pvs.ProcessVariables.PvChangeEvent;
import com.obddroid.core.pvs.ProcessVariables.PvChangeListener;
import com.obddroid.core.pvs.ProcessVariables.TypedPvList;
import com.obddroid.features.gps.data.GpsTelemetryManager;
import com.obddroid.features.sensors.data.SensorTelemetryManager;
import com.obddroid.services.CommService;
import com.obddroid.ui.adapters.ObdItemAdapter;
import com.obddroid.ui.components.VehicleInfoFooter;
import com.obddroid.utils.SnackbarHelper;

import java.util.logging.Logger;

/**
 * Live Data Activity
 *
 * Displays real-time OBD sensor data from the vehicle, including:
 * - Standard OBD PIDs (engine RPM, speed, temperature, etc.)
 * - GPS telemetry data (location, altitude, speed)
 * - Motion sensor data (acceleration, gyroscope)
 */
public class LiveDataActivity extends AppCompatActivity
        implements PvChangeListener, AbsListView.MultiChoiceModeListener {

    private static final Logger log = Logger.getLogger(LiveDataActivity.class.getName());

    // UI Components
    private ListView listView;
    private ObdItemAdapter adapter;
    private VehicleInfoFooter vehicleInfoFooter;
    private View snackbarAnchor;

    // Telemetry managers
    private GpsTelemetryManager gpsTelemetryManager;
    private SensorTelemetryManager sensorTelemetryManager;

    private TypedPvList<Object, ProcessVar> pidStore;

    // Update handler
    private Handler updateHandler = new Handler(Looper.getMainLooper());
    private static final long UPDATE_INTERVAL = 500; // Update every 500ms

    private final Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            if (adapter != null && pidStore != null && !pidStore.isEmpty()) {
                // Refresh the adapter's data source to pick up new PIDs from vehicle
                adapter.setPvList(pidStore);
                adapter.notifyDataSetChanged();
            }
            updateHandler.postDelayed(this, UPDATE_INTERVAL);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_live_data);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setNavigationBarColor(ContextCompat.getColor(this, R.color.background_secondary));
        }

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Live Data");
        }

        initializeViews();
        initializeTelemetryManagers();
    }

    private void initializeViews() {
        listView = findViewById(android.R.id.list);
        snackbarAnchor = findViewById(R.id.snackbar_anchor);

        // Create adapter with current PID store
        pidStore = getPidStore();
        adapter = new ObdItemAdapter(this, R.layout.obd_item, pidStore);
        listView.setAdapter(adapter);

        // Enable multi-select mode with contextual action bar
        listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
        listView.setMultiChoiceModeListener(this);

        // Setup footer
        vehicleInfoFooter = findViewById(R.id.vehicle_footer);
        if (vehicleInfoFooter != null) {
            vehicleInfoFooter.setOverlayView(findViewById(R.id.footer_overlay));
        }
    }

    private void initializeTelemetryManagers() {
        // Telemetry managers are created on-demand
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Register PV change listeners for OBD data
        if (pidStore != null) {
            pidStore.addPvChangeListener(this, PvChangeEvent.PV_ADDED | PvChangeEvent.PV_MODIFIED);
        }

        // Set OBD service to live data mode
        CommService.elm.setService(ObdProt.OBD_SVC_DATA);
        log.info("Set OBD service to OBD_SVC_DATA (Live Data mode)");

        // Start updating the adapter
        updateHandler.post(updateRunnable);

        // Auto-start telemetry if enabled in preferences
        restoreTelemetryState();
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Unregister PV change listeners
        if (pidStore != null) {
            pidStore.removePvChangeListener(this);
        }

        // Stop updates
        updateHandler.removeCallbacks(updateRunnable);
    }

    @Override
    public void pvChanged(PvChangeEvent event) {
        // PV data changed - adapter will be updated by updateRunnable
        // This ensures we capture OBD data changes in real-time
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_live_data, menu);

        // Update menu items based on current telemetry state
        updateMenuItems(menu);

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        updateMenuItems(menu);
        return super.onPrepareOptionsMenu(menu);
    }

    private void updateMenuItems(Menu menu) {
        if (menu == null) return;

        // Update GPS telemetry menu item
        MenuItem gpsItem = menu.findItem(R.id.action_toggle_gps_telemetry);
        if (gpsItem != null && gpsTelemetryManager != null) {
            boolean isActive = gpsTelemetryManager.isActive();
            gpsItem.setTitle(isActive ? "Disable GPS Telemetry" : "Enable GPS Telemetry");
            gpsItem.setIcon(R.drawable.ic_gps_24);
        }

        // Update motion sensor telemetry menu item
        MenuItem sensorItem = menu.findItem(R.id.action_toggle_motion_telemetry);
        if (sensorItem != null && sensorTelemetryManager != null) {
            boolean isActive = sensorTelemetryManager.isActive();
            sensorItem.setTitle(isActive ? "Disable Motion Telemetry" : "Enable Motion Telemetry");
            sensorItem.setIcon(R.drawable.ic_sensors_24);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == android.R.id.home) {
            onBackPressed();
            return true;
        } else if (id == R.id.action_toggle_gps_telemetry) {
            toggleGpsTelemetry();
            return true;
        } else if (id == R.id.action_toggle_motion_telemetry) {
            toggleMotionTelemetry();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void toggleGpsTelemetry() {
        if (gpsTelemetryManager == null) {
            gpsTelemetryManager = new GpsTelemetryManager(this);
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        if (gpsTelemetryManager.isActive()) {
            gpsTelemetryManager.stop();
            SnackbarHelper.showInfo(this, "GPS Telemetry disabled");
            log.info("GPS Telemetry disabled by user");
            prefs.edit().putBoolean("gps_telemetry_enabled", false).apply();
        } else {
            gpsTelemetryManager.start();
            SnackbarHelper.showSuccess(this, "GPS Telemetry enabled");
            log.info("GPS Telemetry enabled by user");
            prefs.edit().putBoolean("gps_telemetry_enabled", true).apply();
        }

        // Refresh adapter to show new fields
        if (adapter != null && pidStore != null && !pidStore.isEmpty()) {
            adapter.setPvList(pidStore);
            adapter.notifyDataSetChanged();
        }

        // Update menu
        invalidateOptionsMenu();
    }

    private void toggleMotionTelemetry() {
        if (sensorTelemetryManager == null) {
            sensorTelemetryManager = new SensorTelemetryManager(this);
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        if (sensorTelemetryManager.isActive()) {
            sensorTelemetryManager.stop();
            SnackbarHelper.showInfo(this, "Motion Telemetry disabled");
            log.info("Motion Telemetry disabled by user");
            prefs.edit().putBoolean("sensor_telemetry_enabled", false).apply();
        } else {
            sensorTelemetryManager.start();
            SnackbarHelper.showSuccess(this, "Motion Telemetry enabled");
            log.info("Motion Telemetry enabled by user");
            prefs.edit().putBoolean("sensor_telemetry_enabled", true).apply();
        }

        // Refresh adapter to show new fields
        if (adapter != null && pidStore != null && !pidStore.isEmpty()) {
            adapter.setPvList(pidStore);
            adapter.notifyDataSetChanged();
        }

        // Update menu
        invalidateOptionsMenu();
    }

    private void restoreTelemetryState() {
        // Auto-start telemetry if it was enabled before
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        boolean gpsEnabled = prefs.getBoolean("gps_telemetry_enabled", false);
        boolean sensorEnabled = prefs.getBoolean("sensor_telemetry_enabled", false);

        if (gpsEnabled) {
            if (gpsTelemetryManager == null) {
                gpsTelemetryManager = new GpsTelemetryManager(this);
            }
            gpsTelemetryManager.start();
        }

        if (sensorEnabled) {
            if (sensorTelemetryManager == null) {
                sensorTelemetryManager = new SensorTelemetryManager(this);
            }
            sensorTelemetryManager.start();
        }

        // Refresh adapter after auto-start
        if (adapter != null && pidStore != null && !pidStore.isEmpty()) {
            adapter.setPvList(pidStore);
            adapter.notifyDataSetChanged();
        }
    }

    private TypedPvList<Object, ProcessVar> getPidStore() {
        TypedPvList<Object, ProcessVar> store =
            ObdProt.getDataService().getTypedStoreForService(ObdProt.OBD_SVC_DATA);
        return store != null ? store : new TypedPvList<>();
    }

    // ========== MultiChoiceModeListener Implementation ==========

    @Override
    public void onItemCheckedStateChanged(ActionMode mode, int position, long id, boolean checked) {
        // Update action bar title with selection count
        int selectedCount = listView.getCheckedItemCount();
        mode.setTitle(selectedCount + " selected");
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        MenuInflater inflater = mode.getMenuInflater();
        inflater.inflate(R.menu.context_graph, menu);
        return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        return false;
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.chart_selected) {
            launchChartView();
            return true;
        } else if (id == R.id.dashboard_selected) {
            launchDashboardView();
            return true;
        } else if (id == R.id.hud_selected) {
            launchHudView();
            return true;
        } else if (id == R.id.filter_selected) {
            applyFilter();
            return true;
        }

        return false;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
        // Contextual action bar closed
    }

    // ========== Visualization Launchers ==========

    private int[] getSelectedPositions() {
        final SparseBooleanArray checkedItems = listView.getCheckedItemPositions();
        int checkedItemsCount = listView.getCheckedItemCount();

        int[] positions = new int[checkedItemsCount];
        int j = 0;
        for (int i = 0; i < checkedItems.size(); i++) {
            if (checkedItems.valueAt(i)) {
                positions[j++] = checkedItems.keyAt(i);
            }
        }
        return positions;
    }

    private void launchChartView() {
        if (listView.getCheckedItemCount() == 0) {
            SnackbarHelper.showWarning(this, "Please select items to chart");
            return;
        }

        ChartActivity.setAdapter(adapter);
        Intent intent = new Intent(this, ChartActivity.class);
        intent.putExtra(ChartActivity.POSITIONS, getSelectedPositions());
        startActivity(intent);

        log.info("Launched Chart view with " + listView.getCheckedItemCount() + " items");
    }

    private void launchDashboardView() {
        if (listView.getCheckedItemCount() == 0) {
            SnackbarHelper.showWarning(this, "Please select items for dashboard");
            return;
        }

        DashBoardActivity.setAdapter(adapter);
        Intent intent = new Intent(this, DashBoardActivity.class);
        intent.putExtra(DashBoardActivity.POSITIONS, getSelectedPositions());
        intent.putExtra(DashBoardActivity.RES_ID, R.layout.dashboard);
        startActivity(intent);

        log.info("Launched Dashboard view with " + listView.getCheckedItemCount() + " items");
    }

    private void launchHudView() {
        if (listView.getCheckedItemCount() == 0) {
            SnackbarHelper.showWarning(this, "Please select items for HUD");
            return;
        }

        DashBoardActivity.setAdapter(adapter);
        Intent intent = new Intent(this, DashBoardActivity.class);
        intent.putExtra(DashBoardActivity.POSITIONS, getSelectedPositions());
        intent.putExtra(DashBoardActivity.RES_ID, R.layout.head_up);
        startActivity(intent);

        log.info("Launched HUD view with " + listView.getCheckedItemCount() + " items");
    }

    private void applyFilter() {
        if (listView.getCheckedItemCount() == 0) {
            SnackbarHelper.showWarning(this, "Please select items to filter");
            return;
        }

        // Set list to filtered mode showing only selected items
        // TODO: Actually implement filtering by hiding non-selected items
        SnackbarHelper.showInfo(this, "Filter functionality coming soon");
        log.info("Filter requested for " + listView.getCheckedItemCount() + " items");
    }
}
