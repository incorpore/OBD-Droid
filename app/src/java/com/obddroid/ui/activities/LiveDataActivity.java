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
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.obddroid.R;
import com.obddroid.obd.ObdProt;
import com.obddroid.ecu.EcuDataPv;
import com.obddroid.common.ProcessVariables.ProcessVar;
import com.obddroid.common.ProcessVariables.PvChangeEvent;
import com.obddroid.common.ProcessVariables.PvChangeListener;
import com.obddroid.common.ProcessVariables.TypedPvList;
import com.obddroid.telemetry.GpsTelemetryManager;
import com.obddroid.telemetry.SensorTelemetryManager;
import com.obddroid.services.CommService;
import com.obddroid.ui.adapters.ObdItemAdapter;
import com.obddroid.ui.adapters.ObdRecyclerAdapter;
import com.obddroid.ui.components.VehicleInfoFooter;
import com.obddroid.utils.SnackbarHelper;
import com.obddroid.utils.PermissionManager;

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
        implements PvChangeListener {

    private static final Logger log = Logger.getLogger(LiveDataActivity.class.getName());

    // UI Components
    private RecyclerView recyclerView;
    private ObdRecyclerAdapter recyclerAdapter;
    private VehicleInfoFooter vehicleInfoFooter;
    private View snackbarAnchor;
    private ActionMode actionMode;

    // Status bar components
    private TextView milStatusText;
    private TextView faultCodeCountText;

    // Telemetry managers
    private GpsTelemetryManager gpsTelemetryManager;
    private SensorTelemetryManager sensorTelemetryManager;

    // Update handler
    private Handler updateHandler = new Handler(Looper.getMainLooper());
    private static final long UPDATE_INTERVAL = 500; // Update every 500ms

    private final Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            if (recyclerAdapter != null && !ObdProt.PidPvs.isEmpty()) {
                // Just refresh the displayed values, don't rebuild the entire list
                recyclerAdapter.notifyDataSetChanged();

                // Update status bar
                updateStatusBar();
            }
            updateHandler.postDelayed(this, UPDATE_INTERVAL);
        }
    };

    private void updateStatusBar() {
        try {
            // Get numCodes from CommService.elm which contains both MIL status and fault count
            int numCodes = CommService.elm.getNumCodes();

            // Extract MIL status from bit 7 (0x80)
            boolean milOn = (numCodes & 0x80) != 0;

            // Extract actual fault code count from bits 0-6 (0x7F)
            int faultCount = numCodes & 0x7F;

            runOnUiThread(() -> {
                // Update MIL Status
                if (milStatusText != null) {
                    milStatusText.setText(milOn ? "ON" : "OFF");
                    milStatusText.setTextColor(milOn ? 0xFFF44336 : 0xFF4CAF50); // Red or Green
                }

                // Update Fault Code Count
                if (faultCodeCountText != null) {
                    faultCodeCountText.setText(String.valueOf(faultCount));
                    faultCodeCountText.setTextColor(faultCount > 0 ? 0xFFF44336 : 0xFF4CAF50); // Red or Green
                }
            });
        } catch (Exception e) {
            // Silently handle errors - OBD connection might not be established yet
            log.fine("Could not update status bar: " + e.getMessage());
        }
    }

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
        recyclerView = findViewById(R.id.recycler_view);
        snackbarAnchor = findViewById(R.id.snackbar_anchor);

        // Setup RecyclerView with StaggeredGridLayoutManager (2 columns, vertical)
        StaggeredGridLayoutManager layoutManager =
            new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL);
        recyclerView.setLayoutManager(layoutManager);

        // Create adapter with grid tile layout
        recyclerAdapter = new ObdRecyclerAdapter(this, ObdProt.PidPvs, this::onSelectionChanged);
        recyclerView.setAdapter(recyclerAdapter);

        // Setup status bar
        milStatusText = findViewById(R.id.mil_status);
        faultCodeCountText = findViewById(R.id.fault_code_count);

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
        ObdProt.PidPvs.addPvChangeListener(this, PvChangeEvent.PV_ADDED | PvChangeEvent.PV_MODIFIED);

        // Set OBD service to live data mode
        CommService.elm.setService(ObdProt.OBD_SVC_DATA);
        log.info("Set OBD service to OBD_SVC_DATA (Live Data mode)");

        // Refresh adapter with current PIDs (in case new ones were added while paused)
        if (recyclerAdapter != null && !ObdProt.PidPvs.isEmpty()) {
            recyclerAdapter.updateData(ObdProt.PidPvs);
        }

        // Start updating the adapter
        updateHandler.post(updateRunnable);

        // Auto-start telemetry if enabled in preferences
        restoreTelemetryState();
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Unregister PV change listeners
        ObdProt.PidPvs.removePvChangeListener(this);

        // Stop updates
        updateHandler.removeCallbacks(updateRunnable);
    }

    @Override
    public void pvChanged(PvChangeEvent event) {
        // When NEW PIDs are added (vehicle discovery), rebuild the adapter
        if ((event.getType() & PvChangeEvent.PV_ADDED) != 0) {
            runOnUiThread(() -> {
                if (recyclerAdapter != null) {
                    recyclerAdapter.updateData(ObdProt.PidPvs);
                }
            });
        }
        // For value modifications, the updateRunnable will handle the refresh
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

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PermissionManager.PERMISSION_REQUEST_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                // Permission granted, start GPS telemetry
                log.info("Location permission granted, starting GPS telemetry");
                if (gpsTelemetryManager != null) {
                    gpsTelemetryManager.start();
                    SnackbarHelper.showSuccess(this, "GPS Telemetry enabled");
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
                    prefs.edit().putBoolean("gps_telemetry_enabled", true).apply();

                    // Refresh adapter and menu
                    if (recyclerAdapter != null && !ObdProt.PidPvs.isEmpty()) {
                        recyclerAdapter.updateData(ObdProt.PidPvs);
                    }
                    invalidateOptionsMenu();
                }
            } else {
                // Permission denied
                log.warning("Location permission denied by user");
                SnackbarHelper.showError(this, "GPS Telemetry requires location permission");
            }
        }
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
            // Check if we have location permissions before starting GPS
            if (!PermissionManager.hasLocationPermission(this)) {
                log.info("Location permission not granted, requesting...");
                PermissionManager.requestLocationPermission(this);
                SnackbarHelper.showInfo(this, "Location permission required for GPS Telemetry");
                return;
            }

            gpsTelemetryManager.start();
            SnackbarHelper.showSuccess(this, "GPS Telemetry enabled");
            log.info("GPS Telemetry enabled by user");
            prefs.edit().putBoolean("gps_telemetry_enabled", true).apply();
        }

        // Refresh adapter to show/hide fields (even if PidPvs becomes empty)
        if (recyclerAdapter != null) {
            recyclerAdapter.updateData(ObdProt.PidPvs);
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

        // Refresh adapter to show/hide fields (even if PidPvs becomes empty)
        if (recyclerAdapter != null) {
            recyclerAdapter.updateData(ObdProt.PidPvs);
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
        if (recyclerAdapter != null) {
            recyclerAdapter.updateData(ObdProt.PidPvs);
        }
    }

    // ========== Selection Handler (for RecyclerView) ==========

    private void onSelectionChanged(int selectedCount) {
        if (selectedCount > 0) {
            if (actionMode == null) {
                actionMode = startActionMode(new ActionMode.Callback() {
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
                            mode.finish();
                            return true;
                        } else if (id == R.id.dashboard_selected) {
                            launchDashboardView();
                            mode.finish();
                            return true;
                        } else if (id == R.id.hud_selected) {
                            launchHudView();
                            mode.finish();
                            return true;
                        } else if (id == R.id.filter_selected) {
                            applyFilter();
                            mode.finish();
                            return true;
                        }

                        return false;
                    }

                    @Override
                    public void onDestroyActionMode(ActionMode mode) {
                        actionMode = null;
                        recyclerAdapter.clearSelection();
                    }
                });
            }
            actionMode.setTitle(selectedCount + " selected");
        } else {
            if (actionMode != null) {
                actionMode.finish();
            }
        }
    }

    // ========== Visualization Launchers ==========

    private int[] getSelectedPositions() {
        return recyclerAdapter.getSelectedPositions();
    }

    private void launchChartView() {
        int[] selectedPositions = getSelectedPositions();
        if (selectedPositions.length == 0) {
            SnackbarHelper.showWarning(this, "Please select items to chart");
            return;
        }

        // Note: ChartActivity expects an ObdItemAdapter, so we need to create a compatibility wrapper
        // For now, we'll log a message indicating this needs to be updated
        SnackbarHelper.showInfo(this, "Chart view needs adapter compatibility update");
        log.info("Chart view requested with " + selectedPositions.length + " items");
    }

    private void launchDashboardView() {
        int[] selectedPositions = getSelectedPositions();
        if (selectedPositions.length == 0) {
            SnackbarHelper.showWarning(this, "Please select items for dashboard");
            return;
        }

        SnackbarHelper.showInfo(this, "Dashboard view needs adapter compatibility update");
        log.info("Dashboard view requested with " + selectedPositions.length + " items");
    }

    private void launchHudView() {
        int[] selectedPositions = getSelectedPositions();
        if (selectedPositions.length == 0) {
            SnackbarHelper.showWarning(this, "Please select items for HUD");
            return;
        }

        SnackbarHelper.showInfo(this, "HUD view needs adapter compatibility update");
        log.info("HUD view requested with " + selectedPositions.length + " items");
    }

    private void applyFilter() {
        int[] selectedPositions = getSelectedPositions();
        if (selectedPositions.length == 0) {
            SnackbarHelper.showWarning(this, "Please select items to filter");
            return;
        }

        // Set list to filtered mode showing only selected items
        // TODO: Actually implement filtering by hiding non-selected items
        SnackbarHelper.showInfo(this, "Filter functionality coming soon");
        log.info("Filter requested for " + selectedPositions.length + " items");
    }
}
