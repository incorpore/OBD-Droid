package com.obddroid.ui.activities;

import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.obddroid.R;
import com.obddroid.core.ecu.EcuDataPv;
import com.obddroid.core.obd.ObdProt;
import com.obddroid.core.pvs.PvChangeEvent;
import com.obddroid.core.pvs.PvChangeListener;
import com.obddroid.ui.components.FuelEconomyChart;
import com.obddroid.ui.components.FuelFlowGauge;
import com.obddroid.ui.components.VehicleInfoFooter;
import com.obddroid.utils.TripComputer;
import com.obddroid.utils.TripManager;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileWriter;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;
import java.util.logging.Logger;

/**
 * Fuel Economy Activity
 * Displays detailed fuel economy metrics including:
 * - Instantaneous and average MPG with historical bar chart
 * - Fuel level percentage
 * - Estimated range
 * - Current fuel flow rate
 */
public class FuelEconomyActivity extends AppCompatActivity implements PvChangeListener {

    private static final Logger log = Logger.getLogger(FuelEconomyActivity.class.getName());

    // UI Components
    private TextView instantMpgValue;
    private TextView averageMpgValue;
    private TextView fuelLevelValue;
    private TextView rangeValue;
    private TextView fuelFlowValue;
    private TextView throttlePositionValue;
    private TextView timeToEmptyValue;
    private FuelEconomyChart fuelEconomyChart;
    private FuelFlowGauge fuelFlowGauge;
    private VehicleInfoFooter vehicleInfoFooter;

    // Data tracking
    private Queue<Float> recentMpgData = new LinkedList<>();     // Last 5 values (0-5 min)
    private Queue<Float> mediumMpgData = new LinkedList<>();     // Last 6 values (0-30 min)
    private float longTermMpgAverage = 0f;                        // Overall average (0-3 hours)
    private int dataPointCount = 0;

    private Handler updateHandler;
    private static final long UPDATE_INTERVAL = 1000; // Update every second

    // Trip manager
    private TripManager tripManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fuel_economy);

        log.info("=== FuelEconomyActivity onCreate() ===");

        // Set status bar and navigation bar colors to match footer (dark grey #212121)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(Color.parseColor("#212121"));
            getWindow().setNavigationBarColor(Color.parseColor("#212121"));
        }

        // Set up action bar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Fuel Economy");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // Initialize views
        instantMpgValue = findViewById(R.id.instant_mpg_value);
        averageMpgValue = findViewById(R.id.average_mpg_value);
        fuelLevelValue = findViewById(R.id.fuel_level_value);
        rangeValue = findViewById(R.id.range_value);
        fuelFlowValue = findViewById(R.id.fuel_flow_value);
        throttlePositionValue = findViewById(R.id.throttle_position_value);
        timeToEmptyValue = findViewById(R.id.time_to_empty_value);
        fuelEconomyChart = findViewById(R.id.fuel_economy_chart);
        fuelFlowGauge = findViewById(R.id.fuel_flow_gauge);

        log.info("Views initialized");

        // Initialize with demo data
        initializeDemoData();

        // Set up handler for periodic updates (more frequent for testing)
        updateHandler = new Handler(Looper.getMainLooper());

        // Register for OBD data updates
        log.info("Registering PV change listener...");
        ObdProt.PidPvs.addPvChangeListener(this,
                PvChangeEvent.PV_ADDED | PvChangeEvent.PV_MODIFIED);
        log.info("PV change listener registered. PidPvs size: " + ObdProt.PidPvs.size());

        // Wire up footer overlay
        setupFooterOverlay();

        // Initialize trip manager
        tripManager = new TripManager(this);
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
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        log.info("=== FuelEconomyActivity onResume() ===");

        // Auto-request Live Data service if not already active
        if (ObdProt.PidPvs.size() == 0) {
            log.info("PidPvs is empty - requesting Live Data service");
            try {
                // Request live data service to start collecting OBD data
                com.obddroid.services.CommService.elm.setService(ObdProt.OBD_SVC_DATA, true);
            } catch (Exception e) {
                log.warning("Failed to request Live Data service: " + e.getMessage());
            }
        }

        startPeriodicUpdates();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopPeriodicUpdates();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ObdProt.PidPvs.removePvChangeListener(this);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Add trip computer icon to action bar
        MenuItem tripComputerItem = menu.add(0, R.id.menu_trip_computer, 0, "Trip Computer");
        tripComputerItem.setIcon(android.R.drawable.ic_menu_mylocation); // Speedometer-like icon
        tripComputerItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_trip_computer) {
            showTripComputerDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showTripComputerDialog() {
        log.info("Trip computer icon clicked!");

        // Inflate the new dialog layout
        LayoutInflater inflater = LayoutInflater.from(this);
        View dialogView = inflater.inflate(R.layout.dialog_trip_manager, null);

        // Create the dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();

        // Get views
        LinearLayout tabsContainer = dialogView.findViewById(R.id.trip_tabs_container);
        TextView tripNameHeader = dialogView.findViewById(R.id.trip_name_header);
        View activeIndicator = dialogView.findViewById(R.id.active_trip_indicator);
        TextView distance = dialogView.findViewById(R.id.trip_distance);
        TextView duration = dialogView.findViewById(R.id.trip_duration);
        TextView avgSpeed = dialogView.findViewById(R.id.trip_avg_speed);
        TextView avgMpg = dialogView.findViewById(R.id.trip_avg_mpg);
        TextView fuelUsed = dialogView.findViewById(R.id.trip_fuel_used);
        TextView cost = dialogView.findViewById(R.id.trip_cost);
        Button resetButton = dialogView.findViewById(R.id.reset_trip_button);
        TextView addTripButton = dialogView.findViewById(R.id.btn_add_trip);
        TextView exportButton = dialogView.findViewById(R.id.btn_export);
        ImageView optionsButton = dialogView.findViewById(R.id.btn_trip_options);

        // Current selected trip
        final String[] selectedTripId = {tripManager.getActiveTrip().getId()};

        // Function to update trip display
        final Runnable[] updateDisplay = new Runnable[1];
        final Runnable[] refreshTabs = new Runnable[1];

        updateDisplay[0] = () -> {
            TripComputer trip = tripManager.getTripById(selectedTripId[0]);
            if (trip == null) return;

            tripNameHeader.setText(trip.getName());
            distance.setText(String.format("%.1f", trip.getDistanceMiles()));
            duration.setText(trip.getFormattedDuration());
            avgSpeed.setText(String.format("%.0f", trip.getAverageSpeedMph()));
            avgMpg.setText(String.format("%.1f", trip.getAverageMPG()));
            fuelUsed.setText(String.format("%.2f", trip.getFuelUsedGallons()));
            cost.setText(String.format("%.2f", trip.getTripCost()));

            // Show active indicator if this is the active trip
            boolean isActive = selectedTripId[0].equals(tripManager.getActiveTrip().getId());
            activeIndicator.setVisibility(isActive ? View.VISIBLE : View.GONE);
        };

        refreshTabs[0] = () -> {
            tabsContainer.removeAllViews();
            for (TripComputer trip : tripManager.getAllTrips()) {
                View tabView = inflater.inflate(R.layout.item_trip_tab, tabsContainer, false);
                TextView tabName = tabView.findViewById(R.id.tab_trip_name);
                View tabIndicator = tabView.findViewById(R.id.tab_indicator);

                tabName.setText(trip.getName());

                // Highlight selected trip
                boolean isSelected = trip.getId().equals(selectedTripId[0]);
                tabName.setTextColor(Color.parseColor(isSelected ? "#00ACC1" : "#888888"));
                tabIndicator.setBackgroundColor(Color.parseColor(isSelected ? "#00ACC1" : "#00000000"));

                tabView.setOnClickListener(v -> {
                    selectedTripId[0] = trip.getId();
                    refreshTabs[0].run();
                    updateDisplay[0].run();
                });

                tabsContainer.addView(tabView);
            }
        };

        // Add trip button
        addTripButton.setOnClickListener(v -> showAddTripDialog(dialog, refreshTabs[0], updateDisplay[0]));

        // Export button
        exportButton.setOnClickListener(v -> showExportDialog());

        // Options menu (rename/delete/set active)
        optionsButton.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(this, v);
            popup.getMenu().add(0, 1, 0, "Rename Trip");
            popup.getMenu().add(0, 2, 0, "Delete Trip");
            popup.getMenu().add(0, 3, 0, "Set as Active Trip");

            popup.setOnMenuItemClickListener(item -> {
                switch (item.getItemId()) {
                    case 1: // Rename
                        showRenameTripDialog(selectedTripId[0], refreshTabs[0], updateDisplay[0]);
                        return true;
                    case 2: // Delete
                        showDeleteTripDialog(selectedTripId[0], dialog, refreshTabs[0], updateDisplay[0]);
                        return true;
                    case 3: // Set active
                        tripManager.setActiveTrip(selectedTripId[0]);
                        updateDisplay[0].run();
                        Toast.makeText(this, "Active trip updated", Toast.LENGTH_SHORT).show();
                        return true;
                }
                return false;
            });
            popup.show();
        });

        // Reset button
        resetButton.setOnClickListener(v -> {
            TripComputer trip = tripManager.getTripById(selectedTripId[0]);
            if (trip == null) return;

            new AlertDialog.Builder(this)
                .setTitle("Reset " + trip.getName() + "?")
                .setMessage("This will reset all data for this trip to zero.")
                .setPositiveButton("Reset", (d, which) -> {
                    tripManager.resetTrip(selectedTripId[0]);
                    updateDisplay[0].run();
                })
                .setNegativeButton("Cancel", null)
                .show();
        });

        // Initial setup
        refreshTabs[0].run();
        updateDisplay[0].run();

        dialog.show();
    }

    private void showAddTripDialog(AlertDialog parentDialog, Runnable refreshTabs, Runnable updateDisplay) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Create New Trip");

        // Create input layout
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 20, 50, 20);

        EditText input = new EditText(this);
        input.setHint("Trip name");
        layout.addView(input);

        // Add suggestions
        TextView suggestionsLabel = new TextView(this);
        suggestionsLabel.setText("\nSuggestions:");
        suggestionsLabel.setTextSize(12);
        suggestionsLabel.setTextColor(Color.GRAY);
        layout.addView(suggestionsLabel);

        LinearLayout suggestionsContainer = new LinearLayout(this);
        suggestionsContainer.setOrientation(LinearLayout.HORIZONTAL);
        suggestionsContainer.setPadding(0, 10, 0, 0);

        for (String suggestion : new String[]{"Work", "City", "Highway", "Weekend"}) {
            Button suggestionBtn = new Button(this);
            suggestionBtn.setText(suggestion);
            suggestionBtn.setTextSize(12);
            suggestionBtn.setPadding(20, 10, 20, 10);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(0, 0, 10, 0);
            suggestionBtn.setLayoutParams(params);
            suggestionBtn.setOnClickListener(v -> input.setText(suggestion));
            suggestionsContainer.addView(suggestionBtn);
        }
        layout.addView(suggestionsContainer);

        builder.setView(layout);
        builder.setPositiveButton("Create", (d, which) -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) {
                name = "Trip " + (tripManager.getTripCount() + 1);
            }
            tripManager.createTrip(name);
            refreshTabs.run();
            Toast.makeText(this, "Trip created: " + name, Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showRenameTripDialog(String tripId, Runnable refreshTabs, Runnable updateDisplay) {
        TripComputer trip = tripManager.getTripById(tripId);
        if (trip == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Rename Trip");

        EditText input = new EditText(this);
        input.setText(trip.getName());
        input.setSelection(trip.getName().length());
        input.setPadding(50, 20, 50, 20);
        builder.setView(input);

        builder.setPositiveButton("Rename", (d, which) -> {
            String newName = input.getText().toString().trim();
            if (!newName.isEmpty()) {
                tripManager.renameTrip(tripId, newName);
                refreshTabs.run();
                updateDisplay.run();
                Toast.makeText(this, "Trip renamed", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showDeleteTripDialog(String tripId, AlertDialog parentDialog, Runnable refreshTabs, Runnable updateDisplay) {
        TripComputer trip = tripManager.getTripById(tripId);
        if (trip == null) return;

        new AlertDialog.Builder(this)
            .setTitle("Delete " + trip.getName() + "?")
            .setMessage("This action cannot be undone.")
            .setPositiveButton("Delete", (d, which) -> {
                if (tripManager.deleteTrip(tripId)) {
                    refreshTabs.run();
                    updateDisplay.run();
                    Toast.makeText(this, "Trip deleted", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Cannot delete the last trip", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showExportDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Export Trips");
        builder.setMessage("Choose export format:");

        builder.setPositiveButton("CSV", (d, which) -> exportToCSV());
        builder.setNeutralButton("JSON", (d, which) -> exportToJSON());
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void exportToCSV() {
        try {
            String csv = tripManager.exportToCSV();
            File file = new File(getCacheDir(), "trips_export.csv");
            FileWriter writer = new FileWriter(file);
            writer.write(csv);
            writer.close();

            shareFile(file, "text/csv");
        } catch (Exception e) {
            log.warning("Failed to export CSV: " + e.getMessage());
            Toast.makeText(this, "Export failed", Toast.LENGTH_SHORT).show();
        }
    }

    private void exportToJSON() {
        try {
            String json = tripManager.exportToJSON();
            File file = new File(getCacheDir(), "trips_export.json");
            FileWriter writer = new FileWriter(file);
            writer.write(json);
            writer.close();

            shareFile(file, "application/json");
        } catch (Exception e) {
            log.warning("Failed to export JSON: " + e.getMessage());
            Toast.makeText(this, "Export failed", Toast.LENGTH_SHORT).show();
        }
    }

    private void shareFile(File file, String mimeType) {
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", file);
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType(mimeType);
        shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(shareIntent, "Export Trips"));
    }

    private void initializeDemoData() {
        // Initialize with sample data for demonstration
        float[] recentSample = {20f, 23f, 12f, 18f, 45f};
        float[] mediumSample = {15f, 35f, 30f, 25f, 20f, 25f};
        float[] longSample = {22f};

        for (float value : recentSample) {
            recentMpgData.add(value);
        }
        for (float value : mediumSample) {
            mediumMpgData.add(value);
        }
        longTermMpgAverage = longSample[0];

        updateChart();
    }

    private void startPeriodicUpdates() {
        updateHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                updateDisplayedValues();
                updateHandler.postDelayed(this, UPDATE_INTERVAL);
            }
        }, UPDATE_INTERVAL);
    }

    private void stopPeriodicUpdates() {
        updateHandler.removeCallbacksAndMessages(null);
    }

    @Override
    public void pvChanged(PvChangeEvent event) {
        runOnUiThread(() -> {
            try {
                log.info("pvChanged() received! Event type: " + event.getType());
                Object value = event.getValue();
                if (value instanceof EcuDataPv) {
                    EcuDataPv pv = (EcuDataPv) value;
                    log.info("Processing PV with PID: " + pv.get(EcuDataPv.FID_PID));
                    updateFromOBDData(pv);
                }
            } catch (Exception e) {
                log.warning("Error processing PV change: " + e.getMessage());
            }
        });
    }

    private void updateFromOBDData(EcuDataPv pv) {
        Object pidObj = pv.get(EcuDataPv.FID_PID);
        if (pidObj == null) return;

        int pid = (Integer) pidObj;
        Object valueObj = pv.get(EcuDataPv.FID_VALUE);
        if (valueObj == null) return;

        String valueStr = valueObj.toString();

        try {
            switch (pid) {
                case 0x2F: // Fuel tank level (%)
                    float fuelLevel = Float.parseFloat(valueStr);
                    fuelLevelValue.setText(String.format("%.1f", fuelLevel));
                    log.info("Fuel level updated: " + fuelLevel + "%");
                    break;

                case 0x0D: // Vehicle speed (km/h)
                case 0x0C: // Engine RPM
                case 0x04: // Engine load
                    // Trigger MPG calculation when any of these update
                    updateMPGCalculation();
                    break;
            }
        } catch (NumberFormatException e) {
            log.warning("Invalid number format for PID " + Integer.toHexString(pid) + ": " + valueStr);
        }
    }

    private void updateMPGCalculation() {
        // Get current values from OBD data
        float speed = getCurrentSpeed();      // mph
        float rpm = getCurrentRPM();          // revolutions per minute
        float engineLoad = getCurrentEngineLoad(); // percent

        // Skip calculation if vehicle is stopped
        if (speed < 1.0f) {
            return;
        }

        // Approximate fuel consumption using engine load and RPM
        // This is an estimation formula based on typical gasoline engines
        // Formula: fuel_rate (gal/h) ≈ (displacement * RPM * load) / (efficiency_constant)
        // We use a simplified approximation for 2.0-3.0L engines
        float estimatedFuelRateGalH = calculateEstimatedFuelRate(rpm, engineLoad, speed);

        // Update fuel flow display
        fuelFlowValue.setText(String.format("%.1f", estimatedFuelRateGalH));
        fuelFlowGauge.setValue(estimatedFuelRateGalH);

        // Calculate instant MPG
        if (estimatedFuelRateGalH > 0.01f) {
            float instantMpg = speed / estimatedFuelRateGalH;

            // Cap unrealistic values (sometimes calculations go wild)
            instantMpg = Math.min(Math.max(instantMpg, 5f), 99f);

            // Update instant MPG display
            instantMpgValue.setText(String.format("%.1f", instantMpg));

            // Add to recent data queue (keep last 5 values for 0-5 min period)
            recentMpgData.add(instantMpg);
            if (recentMpgData.size() > 5) {
                recentMpgData.poll();
            }

            // Every 5 data points, add average to medium term queue
            dataPointCount++;
            if (dataPointCount % 5 == 0) {
                float recentAvg = calculateAverage(new ArrayList<>(recentMpgData));
                mediumMpgData.add(recentAvg);
                if (mediumMpgData.size() > 6) {
                    mediumMpgData.poll();
                }
            }

            // Calculate overall average
            ArrayList<Float> allData = new ArrayList<>(mediumMpgData);
            float avgMpg = calculateAverage(allData);
            if (avgMpg > 0) {
                longTermMpgAverage = avgMpg;
                // Update average MPG display
                averageMpgValue.setText(String.format("%.1f", avgMpg));
            }

            // Update chart
            updateChart();

            // Calculate and update range
            updateRange(estimatedFuelRateGalH, avgMpg > 0 ? avgMpg : instantMpg);
        }
    }

    /**
     * Estimate fuel consumption rate based on RPM, engine load, and speed
     * This is an approximation for vehicles without direct fuel rate PID support
     */
    private float calculateEstimatedFuelRate(float rpm, float loadPercent, float speedMph) {
        // Typical 2.5L engine approximation
        // Base fuel consumption increases with RPM and load
        float baseRate = (rpm / 3000f) * (loadPercent / 100f);

        // Adjust for speed (highway efficiency vs city)
        float speedFactor = 1.0f;
        if (speedMph > 55f) {
            // Highway speeds - slightly less efficient due to wind resistance
            speedFactor = 1.0f + ((speedMph - 55f) / 100f);
        } else if (speedMph < 25f && speedMph > 5f) {
            // City speeds - less efficient
            speedFactor = 1.2f;
        }

        // Typical sedan uses 0.3-3.5 gal/h depending on conditions
        float estimatedGalH = baseRate * speedFactor * 2.5f;

        // Clamp to reasonable range
        return Math.min(Math.max(estimatedGalH, 0.2f), 4.0f);
    }

    private void updateRange(float fuelRate, float avgMpg) {
        try {
            // Get fuel level percentage
            String fuelLevelStr = fuelLevelValue.getText().toString();
            float fuelLevelPct = Float.parseFloat(fuelLevelStr);

            // Assume 15 gallon tank (typical sedan)
            float tankCapacity = 15.0f;
            float remainingFuel = (fuelLevelPct / 100f) * tankCapacity;

            // Calculate range
            float range = remainingFuel * avgMpg;
            rangeValue.setText(String.format("%.0f", range));
        } catch (Exception e) {
            log.warning("Error calculating range: " + e.getMessage());
        }
    }

    private float calculateAverage(ArrayList<Float> data) {
        if (data.isEmpty()) return 0f;

        float sum = 0f;
        for (float value : data) {
            sum += value;
        }
        return sum / data.size();
    }

    private void updateChart() {
        float[] recentArray = listToArray(new ArrayList<>(recentMpgData));
        float[] mediumArray = listToArray(new ArrayList<>(mediumMpgData));
        float[] longArray = {longTermMpgAverage};

        fuelEconomyChart.setData(recentArray, mediumArray, longArray);
    }

    private float[] listToArray(ArrayList<Float> list) {
        float[] array = new float[list.size()];
        for (int i = 0; i < list.size(); i++) {
            array[i] = list.get(i);
        }
        return array;
    }

    private void updateDisplayedValues() {
        // This method is called periodically - actively pull OBD data as backup to PV listener
        try {
            log.info("=== updateDisplayedValues() called - PidPvs size: " + ObdProt.PidPvs.size());

            // Debug: Log all PIDs in the collection
            try {
                java.util.Set<Object> keys = ObdProt.PidPvs.keySet();
                log.info("PidPvs keys: " + keys.toString());
            } catch (Exception e) {
                log.warning("Could not get PidPvs keys: " + e.getMessage());
            }

            // Force MPG calculation update from current OBD values
            updateMPGCalculation();

            // Update throttle position
            float throttle = getThrottlePosition();
            throttlePositionValue.setText(String.format("%.0f", throttle));

            // Update time to empty
            updateTimeToEmpty();

            // Update active trip
            float currentSpeed = getCurrentSpeed();
            String fuelFlowStr = fuelFlowValue.getText().toString();
            try {
                float fuelFlow = Float.parseFloat(fuelFlowStr);
                tripManager.updateActiveTrip(currentSpeed, fuelFlow);
            } catch (Exception e) {
                // Ignore parse errors
            }

            // Update fuel level if available
            // Key format is "PID.SENSOR.BANK" e.g. "2F.0.0"
            Object fuelLevelPv = ObdProt.PidPvs.get("2F.0.0");
            if (fuelLevelPv instanceof EcuDataPv) {
                Object value = ((EcuDataPv) fuelLevelPv).get(EcuDataPv.FID_VALUE);
                if (value != null) {
                    float fuelLevel = Float.parseFloat(value.toString());
                    fuelLevelValue.setText(String.format("%.1f", fuelLevel));
                    log.info("Fuel level updated: " + fuelLevel + "%");
                }
            } else {
                log.warning("Fuel level PV (2F.0.0) not found or wrong type");
            }
        } catch (Exception e) {
            log.warning("updateDisplayedValues error: " + e.getMessage());
        }
    }

    private float getCurrentSpeed() {
        // Get current speed from OBD data (PID 0x0D)
        // Key format is "PID.SENSOR.BANK" e.g. "0D.0.0"
        try {
            Object speedPv = ObdProt.PidPvs.get("0D.0.0");
            log.info("Speed PV lookup: " + (speedPv != null ? "FOUND" : "NULL"));
            if (speedPv instanceof EcuDataPv) {
                Object value = ((EcuDataPv) speedPv).get(EcuDataPv.FID_VALUE);
                log.info("Speed value: " + value);
                if (value != null) {
                    float kmh = Float.parseFloat(value.toString());
                    float mph = kmh * 0.621371f;
                    log.info("Speed: " + kmh + " km/h = " + mph + " mph");
                    return mph;
                }
            }
        } catch (Exception e) {
            log.warning("Error getting speed: " + e.getMessage());
        }
        log.info("Speed: returning 0 (no data)");
        return 0f;
    }

    private float getCurrentRPM() {
        // Get current RPM from OBD data (PID 0x0C)
        // Key format is "PID.SENSOR.BANK" e.g. "0C.0.0"
        try {
            Object rpmPv = ObdProt.PidPvs.get("0C.0.0");
            log.info("RPM PV lookup: " + (rpmPv != null ? "FOUND" : "NULL"));
            if (rpmPv instanceof EcuDataPv) {
                Object value = ((EcuDataPv) rpmPv).get(EcuDataPv.FID_VALUE);
                log.info("RPM value: " + value);
                if (value != null) {
                    float rpm = Float.parseFloat(value.toString());
                    log.info("RPM: " + rpm);
                    return rpm;
                }
            }
        } catch (Exception e) {
            log.warning("Error getting RPM: " + e.getMessage());
        }
        log.info("RPM: returning 800 (default idle)");
        return 800f; // Idle RPM default
    }

    private float getCurrentEngineLoad() {
        // Get current engine load from OBD data (PID 0x04)
        // Key format is "PID.SENSOR.BANK" e.g. "04.0.0"
        try {
            Object loadPv = ObdProt.PidPvs.get("04.0.0");
            log.info("Load PV lookup: " + (loadPv != null ? "FOUND" : "NULL"));
            if (loadPv instanceof EcuDataPv) {
                Object value = ((EcuDataPv) loadPv).get(EcuDataPv.FID_VALUE);
                log.info("Load value: " + value);
                if (value != null) {
                    float load = Float.parseFloat(value.toString());
                    log.info("Engine Load: " + load + "%");
                    return load;
                }
            }
        } catch (Exception e) {
            log.warning("Error getting engine load: " + e.getMessage());
        }
        log.info("Engine Load: returning 20 (default)");
        return 20f; // Default light load
    }

    private float getThrottlePosition() {
        // Get throttle position from OBD data (PID 0x11)
        // Key format is "PID.SENSOR.BANK" e.g. "11.0.0"
        try {
            Object throttlePv = ObdProt.PidPvs.get("11.0.0");
            if (throttlePv instanceof EcuDataPv) {
                Object value = ((EcuDataPv) throttlePv).get(EcuDataPv.FID_VALUE);
                if (value != null) {
                    float throttle = Float.parseFloat(value.toString());
                    return throttle;
                }
            }
        } catch (Exception e) {
            // Silently ignore
        }
        return 0f; // Default closed throttle
    }

    private void updateTimeToEmpty() {
        // Calculate time to empty based on current fuel level and flow rate
        try {
            // Get current fuel level percentage
            String fuelLevelStr = fuelLevelValue.getText().toString();
            float fuelLevelPct = Float.parseFloat(fuelLevelStr);

            // Get current fuel flow rate (gal/h)
            String fuelFlowStr = fuelFlowValue.getText().toString();
            float fuelFlowRate = Float.parseFloat(fuelFlowStr);

            // Assume 15 gallon tank (typical sedan)
            float tankCapacity = 15.0f;
            float remainingFuel = (fuelLevelPct / 100f) * tankCapacity;

            // Calculate time to empty (hours)
            if (fuelFlowRate > 0.05f) {
                // Only calculate if we have meaningful fuel flow
                float hoursToEmpty = remainingFuel / fuelFlowRate;

                // Format as hours:minutes or just hours if less than 10
                if (hoursToEmpty < 10f) {
                    int hours = (int) hoursToEmpty;
                    int minutes = (int) ((hoursToEmpty - hours) * 60);
                    timeToEmptyValue.setText(String.format("%d:%02d", hours, minutes));
                } else {
                    // Just show hours for long durations
                    timeToEmptyValue.setText(String.format("%.1f", hoursToEmpty));
                }
            } else {
                // No fuel flow or vehicle stopped
                timeToEmptyValue.setText("--");
            }
        } catch (Exception e) {
            timeToEmptyValue.setText("--");
        }
    }
}
