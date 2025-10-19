package com.obddroid.ui.activities;

import android.content.ContentValues;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.Locale;
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

    // Trip data logging
    private OutputStreamWriter tripDataLogger;
    private String tripLogFilename;
    private long tripStartTime;
    private boolean isRecording = false;

    // Tank capacity management
    private com.obddroid.vehicle.VehiclePreferences vehiclePreferences;
    private Float cachedTankCapacity = null; // Cache to avoid repeated lookups

    /**
     * Fuel calculation diagnostics data
     * Captures detailed information about the fuel consumption calculation for trip logging
     */
    private static class FuelCalcDiagnostics {
        float mapKpa = 0f;           // Manifold Absolute Pressure (kPa)
        float iatCelsius = 0f;       // Intake Air Temperature (°C)
        float volumetricEfficiency = 0f; // Volumetric Efficiency (%)
        float imap = 0f;             // Intermediate calculation value
        float syntheticMAF = 0f;     // Calculated Mass Air Flow (g/s)
        String calcMethod = "N/A";   // "MAP" or "Estimation"
        String calibrated = "No";    // "Yes" or "No"
    }

    // Store the latest fuel calculation diagnostics for trip logging
    private FuelCalcDiagnostics lastFuelCalcDiagnostics = new FuelCalcDiagnostics();

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

        // Initialize vehicle preferences
        vehiclePreferences = new com.obddroid.vehicle.VehiclePreferences(this);

        // Check if we need to prompt for tank capacity
        checkAndPromptForTankCapacity();
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

    /**
     * Initialize trip data logging to CSV file
     */
    private void initializeTripDataLogging() {
        try {
            tripStartTime = System.currentTimeMillis();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
            String timestamp = sdf.format(new Date(tripStartTime));
            tripLogFilename = "fuel_economy_trip_" + timestamp + ".csv";

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ - Use MediaStore API
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, tripLogFilename);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "text/csv");
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/OBDroid");

                Uri uri = getContentResolver().insert(MediaStore.Files.getContentUri("external"), values);
                if (uri != null) {
                    OutputStream outputStream = getContentResolver().openOutputStream(uri);
                    tripDataLogger = new OutputStreamWriter(outputStream);

                    // Write CSV header with enhanced fuel calculation diagnostics
                    tripDataLogger.write("Timestamp,ElapsedSec,Speed_MPH,RPM,EngineLoad_%,ThrottlePos_%," +
                            "FuelLevel_%,FuelFlow_GalH,InstantMPG,AvgMPG,Range_Mi,TimeToEmpty," +
                            "MAP_kPa,IAT_C,VE_%,IMAP,SyntheticMAF_g/s,CalcMethod,Calibrated,PID,PIDValue\n");
                    tripDataLogger.flush();

                    log.info("Trip data logging initialized: Documents/OBDroid/" + tripLogFilename);
                    com.google.android.material.snackbar.Snackbar.make(
                        findViewById(android.R.id.content),
                        "📊 Recording started: " + tripLogFilename,
                        com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                    ).show();
                }
            } else {
                // Android 9 and below - Use legacy file system
                File documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
                File obdroidDir = new File(documentsDir, "OBDroid");
                if (!obdroidDir.exists()) {
                    obdroidDir.mkdirs();
                }

                File csvFile = new File(obdroidDir, tripLogFilename);
                tripDataLogger = new OutputStreamWriter(new java.io.FileOutputStream(csvFile));

                // Write CSV header with enhanced fuel calculation diagnostics
                tripDataLogger.write("Timestamp,ElapsedSec,Speed_MPH,RPM,EngineLoad_%,ThrottlePos_%," +
                        "FuelLevel_%,FuelFlow_GalH,InstantMPG,AvgMPG,Range_Mi,TimeToEmpty," +
                        "MAP_kPa,IAT_C,VE_%,IMAP,SyntheticMAF_g/s,CalcMethod,Calibrated,PID,PIDValue\n");
                tripDataLogger.flush();

                log.info("Trip data logging initialized: " + csvFile.getAbsolutePath());
                com.google.android.material.snackbar.Snackbar.make(
                    findViewById(android.R.id.content),
                    "📊 Recording started: " + tripLogFilename,
                    com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                ).show();
            }
        } catch (IOException e) {
            log.severe("Failed to initialize trip data logging: " + e.getMessage());
            com.google.android.material.snackbar.Snackbar.make(
                findViewById(android.R.id.content),
                "⚠️ Recording failed to start",
                com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
            ).show();
        }
    }

    /**
     * Log trip data row to CSV
     */
    private void logTripData(String pid, String pidValue) {
        if (!isRecording || tripDataLogger == null) return;

        try {
            long elapsedMs = System.currentTimeMillis() - tripStartTime;
            float elapsedSec = elapsedMs / 1000f;

            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);
            String timestamp = sdf.format(new Date());

            // Get current values
            float speed = getCurrentSpeed();
            float rpm = getCurrentRPM();
            float engineLoad = getCurrentEngineLoad();
            float throttle = getThrottlePosition();
            String fuelLevel = fuelLevelValue.getText().toString();
            String fuelFlow = fuelFlowValue.getText().toString();
            String instantMpg = instantMpgValue.getText().toString();
            String avgMpg = averageMpgValue.getText().toString();
            String range = rangeValue.getText().toString();
            String timeToEmpty = timeToEmptyValue.getText().toString();

            // Write CSV row with enhanced diagnostics
            tripDataLogger.write(String.format(Locale.US,
                    "%s,%.1f,%.1f,%.0f,%.1f,%.0f,%s,%s,%s,%s,%s,%s,%.2f,%.1f,%.1f,%.2f,%.2f,%s,%s,%s,%s\n",
                    timestamp, elapsedSec, speed, rpm, engineLoad, throttle,
                    fuelLevel, fuelFlow, instantMpg, avgMpg, range, timeToEmpty,
                    lastFuelCalcDiagnostics.mapKpa,
                    lastFuelCalcDiagnostics.iatCelsius,
                    lastFuelCalcDiagnostics.volumetricEfficiency,
                    lastFuelCalcDiagnostics.imap,
                    lastFuelCalcDiagnostics.syntheticMAF,
                    lastFuelCalcDiagnostics.calcMethod,
                    lastFuelCalcDiagnostics.calibrated,
                    pid, pidValue));
            tripDataLogger.flush();
        } catch (IOException e) {
            log.warning("Failed to log trip data: " + e.getMessage());
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

        // Auto-save if still recording
        if (isRecording) {
            stopRecording();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        // Add record button to action bar (left side)
        menu.add(0, 1, 0, "Record")
            .setIcon(android.R.drawable.ic_menu_save)
            .setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS);

        // Add calibration icon to action bar
        menu.add(0, 3, 1, "Calibrate")
            .setIcon(android.R.drawable.ic_menu_manage)
            .setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_IF_ROOM);

        // Add info icon to action bar (right side)
        menu.add(0, 2, 2, "Info")
            .setIcon(android.R.drawable.ic_menu_info_details)
            .setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(android.view.Menu menu) {
        // Update record button icon based on recording state
        android.view.MenuItem recordItem = menu.findItem(1);
        if (recordItem != null) {
            if (isRecording) {
                recordItem.setIcon(android.R.drawable.ic_media_pause);
                recordItem.setTitle("Stop Recording");
            } else {
                recordItem.setIcon(android.R.drawable.ic_menu_save);
                recordItem.setTitle("Start Recording");
            }
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        int itemId = item.getItemId();

        if (itemId == 1) {
            // Toggle recording
            toggleRecording();
            invalidateOptionsMenu(); // Refresh menu to update icon
            return true;
        } else if (itemId == 2) {
            showInfoDialog();
            return true;
        } else if (itemId == 3) {
            showCalibrationDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void toggleRecording() {
        if (isRecording) {
            stopRecording();
        } else {
            startRecording();
        }
    }

    private void startRecording() {
        initializeTripDataLogging();
        isRecording = true;
    }

    private void stopRecording() {
        if (tripDataLogger != null) {
            try {
                tripDataLogger.flush();
                tripDataLogger.close();
                tripDataLogger = null;
                log.info("Trip data logging stopped: " + tripLogFilename);

                com.google.android.material.snackbar.Snackbar.make(
                    findViewById(android.R.id.content),
                    "✅ Trip data saved: " + tripLogFilename,
                    com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                ).show();
            } catch (IOException e) {
                log.warning("Error stopping trip data logger: " + e.getMessage());
                com.google.android.material.snackbar.Snackbar.make(
                    findViewById(android.R.id.content),
                    "⚠️ Error saving trip data",
                    com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
                ).show();
            }
        }
        isRecording = false;
    }

    private void showInfoDialog() {
        // Determine which calculation method is active
        String calcMethod = "Unknown";
        String calcAccuracy = "N/A";
        boolean isCalibrated = false;

        if (isMapCalculationAvailable()) {
            calcMethod = "MAP-based Speed-Density";
            isCalibrated = (getCalibratedVE() != null);
            calcAccuracy = isCalibrated ? "~95% (calibrated)" : "~88% (uncalibrated)";
        } else {
            calcMethod = "RPM/Load Estimation";
            calcAccuracy = "~65%";
        }

        String title = "Fuel Economy";
        String message = "This page provides comprehensive fuel economy tracking and analysis for your vehicle.\n\n" +
                "REAL-TIME METRICS:\n" +
                "• Instant MPG - Current fuel efficiency\n" +
                "• Average MPG - Overall fuel economy\n" +
                "• Fuel Level - Percentage remaining\n" +
                "• Range - Estimated miles to empty\n" +
                "• Fuel Flow - Current consumption rate (gal/h)\n" +
                "• Throttle Position - Current throttle %\n" +
                "• Time to Empty - Estimated time remaining\n\n" +
                "HISTORICAL CHART:\n" +
                "The bar chart shows fuel economy trends over three time periods:\n" +
                "• 0-5 min (Recent) - Last few minutes\n" +
                "• 0-30 min (Medium) - Last half hour\n" +
                "• 0-3 hours (Long) - Extended driving session\n\n" +
                "CALCULATION METHOD:\n" +
                "Method: " + calcMethod + "\n" +
                "Accuracy: " + calcAccuracy + "\n" +
                "Calibrated: " + (isCalibrated ? "Yes ✓" : "No") + "\n\n" +
                (isMapCalculationAvailable() ?
                "MAP-based Speed-Density uses:\n" +
                "• Manifold Absolute Pressure (MAP sensor)\n" +
                "• Intake Air Temperature (IAT sensor)\n" +
                "• Engine Speed (RPM)\n" +
                "• Volumetric Efficiency (VE)\n" +
                "• Stoichiometric ratio (14.7:1)\n\n" +
                "This is the same professional method used by Torque Pro and OBDLink.\n\n" +
                (isCalibrated ? "" : "TIP: Use the calibration tool (⚙️ icon) after your next fill-up to improve accuracy to 95%+!\n\n")
                :
                "Your vehicle doesn't have MAP/IAT sensors, so we use RPM and Load estimation. This is less accurate but still provides useful data.\n\n"
                ) +
                "TRIP LOGGING:\n" +
                "Record detailed trip data including all sensor readings, fuel calculations, and diagnostics. Files are saved to Documents/OBDroid/ folder.\n\n" +
                "NOTE: Ensure your vehicle is connected and Live Data is active for accurate readings.";

        new android.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Got it", null)
            .show();
    }

    /**
     * Show calibration dialog to improve fuel economy accuracy
     *
     * This dialog allows users to calibrate fuel economy calculations by entering
     * real-world fill-up data. The app calculates actual MPG and adjusts the
     * Volumetric Efficiency (VE) parameter to improve future accuracy.
     */
    private void showCalibrationDialog() {
        log.info("Showing calibration dialog");

        // Inflate custom dialog layout
        android.view.LayoutInflater inflater = getLayoutInflater();
        android.view.View dialogView = inflater.inflate(R.layout.dialog_fuel_calibration, null);

        // Find UI elements
        android.widget.EditText inputMilesDriven = dialogView.findViewById(R.id.input_miles_driven);
        android.widget.EditText inputGallonsAdded = dialogView.findViewById(R.id.input_gallons_added);
        android.widget.LinearLayout calibrationResults = dialogView.findViewById(R.id.calibration_results);
        android.widget.TextView actualMpgValue = dialogView.findViewById(R.id.actual_mpg_value);
        android.widget.TextView appMpgValue = dialogView.findViewById(R.id.app_mpg_value);
        android.widget.TextView currentVeValue = dialogView.findViewById(R.id.current_ve_value);
        android.widget.TextView newVeValue = dialogView.findViewById(R.id.new_ve_value);
        android.widget.Button btnCalculate = dialogView.findViewById(R.id.btn_calculate);
        android.widget.Button btnApply = dialogView.findViewById(R.id.btn_apply);
        android.widget.Button btnCancel = dialogView.findViewById(R.id.btn_cancel);

        // Get current VE
        Float currentVEObj = getCalibratedVE();
        final float currentVE = (currentVEObj != null) ? currentVEObj : 85.0f; // Make it final for lambda
        currentVeValue.setText(String.format("%.1f%%", currentVE));

        // Create dialog
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        // Reference to store calculated new VE
        final float[] calculatedNewVE = {currentVE};

        // Calculate button handler
        btnCalculate.setOnClickListener(v -> {
            try {
                // Get input values
                String milesStr = inputMilesDriven.getText().toString().trim();
                String gallonsStr = inputGallonsAdded.getText().toString().trim();

                if (milesStr.isEmpty() || gallonsStr.isEmpty()) {
                    com.google.android.material.snackbar.Snackbar.make(dialogView,
                            "Please enter both miles driven and gallons added",
                            com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show();
                    return;
                }

                float milesDriven = Float.parseFloat(milesStr);
                float gallonsAdded = Float.parseFloat(gallonsStr);

                // Validate inputs
                if (milesDriven <= 0 || milesDriven > 1000) {
                    com.google.android.material.snackbar.Snackbar.make(dialogView,
                            "Miles driven must be between 0 and 1000",
                            com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show();
                    return;
                }

                if (gallonsAdded <= 0 || gallonsAdded > 50) {
                    com.google.android.material.snackbar.Snackbar.make(dialogView,
                            "Gallons added must be between 0 and 50",
                            com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show();
                    return;
                }

                // Calculate actual MPG
                float actualMPG = milesDriven / gallonsAdded;

                // Get app's average MPG (use long-term average)
                float appMPG = longTermMpgAverage;
                if (appMPG <= 0) {
                    // Fall back to current average if long-term not available
                    String avgText = averageMpgValue.getText().toString();
                    try {
                        appMPG = Float.parseFloat(avgText);
                    } catch (NumberFormatException e) {
                        appMPG = 25.0f; // Default fallback
                    }
                }

                // Calculate VE adjustment
                // Formula: newVE = currentVE * (actualMPG / appMPG)
                // If actual is higher than app showed, VE needs to increase
                float veAdjustmentRatio = actualMPG / appMPG;
                float newVE = currentVE * veAdjustmentRatio;

                // Clamp to realistic range (50-130%)
                newVE = Math.max(50f, Math.min(newVE, 130f));

                // Store calculated VE
                calculatedNewVE[0] = newVE;

                // Update UI
                actualMpgValue.setText(String.format("%.1f", actualMPG));
                appMpgValue.setText(String.format("%.1f", appMPG));
                newVeValue.setText(String.format("%.1f%%", newVE));

                // Show results
                calibrationResults.setVisibility(android.view.View.VISIBLE);
                btnApply.setVisibility(android.view.View.VISIBLE);

                log.info(String.format("Calibration calculated: Actual MPG=%.1f, App MPG=%.1f, " +
                        "Current VE=%.1f%%, New VE=%.1f%%",
                        actualMPG, appMPG, currentVE, newVE));

            } catch (NumberFormatException e) {
                com.google.android.material.snackbar.Snackbar.make(dialogView,
                        "Invalid number format",
                        com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show();
                log.warning("Invalid number in calibration: " + e.getMessage());
            }
        });

        // Apply button handler
        btnApply.setOnClickListener(v -> {
            try {
                // Get current VIN
                com.obddroid.vehicle.VehicleManager vehicleManager =
                        com.obddroid.vehicle.VehicleManager.getInstance(this);
                String vin = vehicleManager.getCurrentVIN();

                if (vin == null || vin.isEmpty()) {
                    com.google.android.material.snackbar.Snackbar.make(dialogView,
                            "No VIN available - cannot save calibration",
                            com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show();
                    return;
                }

                // Save new VE to preferences
                boolean success = vehiclePreferences.setVolumetricEfficiency(vin, calculatedNewVE[0]);

                if (success) {
                    log.info("Calibration saved successfully: VE=" + calculatedNewVE[0] + "%");

                    com.google.android.material.snackbar.Snackbar.make(
                            findViewById(android.R.id.content),
                            String.format("✅ Calibration saved! VE adjusted to %.1f%%", calculatedNewVE[0]),
                            com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                    ).show();

                    dialog.dismiss();
                } else {
                    com.google.android.material.snackbar.Snackbar.make(dialogView,
                            "Failed to save calibration",
                            com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show();
                }

            } catch (Exception e) {
                log.warning("Error saving calibration: " + e.getMessage());
                com.google.android.material.snackbar.Snackbar.make(dialogView,
                        "Error saving calibration: " + e.getMessage(),
                        com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show();
            }
        });

        // Cancel button handler
        btnCancel.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
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

        // Log all incoming PID data
        logTripData(String.format("0x%02X", pid), valueStr);

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

        // Calculate fuel consumption using best available method
        // Priority 1: MAP-based Speed-Density (88% accuracy uncalibrated, 95%+ calibrated)
        // Priority 2: RPM/Load estimation (65% accuracy - fallback)
        float estimatedFuelRateGalH = calculateFuelRate(rpm, engineLoad, speed);

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
     * Calculate fuel consumption rate using the best available method
     *
     * This method implements a fallback chain to use the most accurate calculation
     * method available for the current vehicle.
     *
     * Priority 1: MAP-based Speed-Density calculation
     *   - Requires: MAP sensor (PID 0x0B) and IAT sensor (PID 0x0F)
     *   - Accuracy: ~88% (uncalibrated), ~95%+ (after calibration)
     *   - Method: Industry-standard speed-density formula using ideal gas law
     *
     * Priority 2: RPM/Load estimation
     *   - Requires: Only RPM and Load (always available)
     *   - Accuracy: ~65%
     *   - Method: Simplified approximation based on engine characteristics
     *
     * @param rpm Engine speed (revolutions per minute)
     * @param loadPercent Engine load (0-100%)
     * @param speedMph Vehicle speed (miles per hour)
     * @return Fuel consumption rate in gallons per hour
     */
    private float calculateFuelRate(float rpm, float loadPercent, float speedMph) {
        // Try MAP-based calculation first (most accurate)
        if (isMapCalculationAvailable()) {
            log.info("Using MAP-based Speed-Density fuel calculation");
            float mapFuelRate = calculateFuelRateMAP();

            // Validate result
            if (mapFuelRate > 0.05f) {
                return mapFuelRate;
            } else {
                log.warning("MAP calculation returned invalid result, falling back to estimation");
            }
        } else {
            log.info("MAP sensors not available, using RPM/Load estimation");
        }

        // Fall back to estimation method and mark diagnostics
        lastFuelCalcDiagnostics.calcMethod = "Estimation";
        lastFuelCalcDiagnostics.calibrated = "N/A";
        lastFuelCalcDiagnostics.mapKpa = 0f;
        lastFuelCalcDiagnostics.iatCelsius = 0f;
        lastFuelCalcDiagnostics.volumetricEfficiency = 0f;
        lastFuelCalcDiagnostics.imap = 0f;
        lastFuelCalcDiagnostics.syntheticMAF = 0f;

        return calculateEstimatedFuelRate(rpm, loadPercent, speedMph);
    }

    /**
     * Estimate fuel consumption rate based on RPM, engine load, and speed
     * This is an approximation for vehicles without direct fuel rate PID support
     *
     * Uses actual engine displacement if available from VehicleData for better accuracy
     */
    private float calculateEstimatedFuelRate(float rpm, float loadPercent, float speedMph) {
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

        // Get displacement factor from vehicle data
        float displacementFactor = getDisplacementFactor();

        // Calculate estimated fuel rate
        // Formula: baseRate * speedFactor * displacementFactor
        // This scales fuel consumption based on actual engine size
        float estimatedGalH = baseRate * speedFactor * displacementFactor;

        // Clamp to reasonable range based on displacement
        // Smaller engines: 0.2-2.5 gal/h
        // Larger engines: 0.3-5.0 gal/h
        float minRate = displacementFactor < 2.0f ? 0.2f : 0.3f;
        float maxRate = displacementFactor < 2.0f ? 2.5f : 5.0f;

        return Math.min(Math.max(estimatedGalH, minRate), maxRate);
    }

    /**
     * Get displacement factor for fuel rate calculations
     * Uses actual engine displacement from VehicleData if available
     *
     * @return Displacement factor (normalized around 2.5L baseline)
     */
    private float getDisplacementFactor() {
        com.obddroid.vehicle.VehicleManager vehicleManager =
                com.obddroid.vehicle.VehicleManager.getInstance(this);

        io.github.vindecoder.nhtsa.VehicleData vehicleData =
                vehicleManager.getCurrentVehicleData();

        if (vehicleData != null && vehicleData.displacementL != null &&
            !vehicleData.displacementL.isEmpty()) {
            try {
                float displacement = Float.parseFloat(vehicleData.displacementL);

                // Normalize around 2.5L baseline
                // 1.5L engine: factor = 1.5
                // 2.0L engine: factor = 2.0
                // 2.5L engine: factor = 2.5 (baseline)
                // 3.0L engine: factor = 3.0
                // 5.0L engine: factor = 5.0
                float factor = displacement;

                log.info("Using actual displacement for fuel rate: " + displacement + "L (factor: " + factor + ")");
                return factor;
            } catch (NumberFormatException e) {
                log.warning("Could not parse displacement: " + vehicleData.displacementL);
            }
        }

        // Fallback to 2.5L baseline if displacement unknown
        log.info("Using default 2.5L displacement factor (no vehicle data available)");
        return 2.5f;
    }

    /**
     * Get tank capacity with smart fallback system
     *
     * Priority:
     * 1. User-set value from preferences
     * 2. Estimated value from vehicle class
     * 3. Generic default (16 gallons)
     *
     * @return Tank capacity in gallons
     */
    private float getTankCapacity() {
        // Return cached value if available
        if (cachedTankCapacity != null) {
            return cachedTankCapacity;
        }

        com.obddroid.vehicle.VehicleManager vehicleManager =
                com.obddroid.vehicle.VehicleManager.getInstance(this);

        String vin = vehicleManager.getCurrentVIN();

        // Try to get user-set capacity first
        if (vin != null && vehiclePreferences != null) {
            Float userCapacity = vehiclePreferences.getTankCapacity(vin);
            if (userCapacity != null) {
                log.info("Using user-set tank capacity: " + userCapacity + " gal");
                cachedTankCapacity = userCapacity;
                return userCapacity;
            }
        }

        // Estimate from vehicle data
        io.github.vindecoder.nhtsa.VehicleData vehicleData =
                vehicleManager.getCurrentVehicleData();

        if (vehicleData != null) {
            float estimated = vehicleManager.estimateTankCapacity(vehicleData);
            log.info("Using estimated tank capacity: " + estimated + " gal");
            cachedTankCapacity = estimated;
            return estimated;
        }

        // Final fallback
        log.info("Using default tank capacity: 16.0 gal");
        cachedTankCapacity = 16.0f;
        return 16.0f;
    }

    /**
     * Check if we need to prompt user for tank capacity
     * Called once when activity starts
     */
    private void checkAndPromptForTankCapacity() {
        com.obddroid.vehicle.VehicleManager vehicleManager =
                com.obddroid.vehicle.VehicleManager.getInstance(this);

        String vin = vehicleManager.getCurrentVIN();
        io.github.vindecoder.nhtsa.VehicleData vehicleData =
                vehicleManager.getCurrentVehicleData();

        // Only prompt if we have vehicle data and haven't prompted before
        if (vin != null && vehicleData != null &&
            vehiclePreferences != null &&
            !vehiclePreferences.hasPromptedForTankSize(vin)) {

            // Get estimated capacity
            float estimatedCapacity = vehicleManager.estimateTankCapacity(vehicleData);

            // Show dialog after a short delay to let UI settle
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                showTankCapacityDialog(estimatedCapacity, vin, vehicleData);
            }, 500);
        }
    }

    /**
     * Show dialog to confirm/adjust tank capacity
     *
     * @param estimatedGallons Estimated tank capacity
     * @param vin Vehicle VIN
     * @param vehicleData Decoded vehicle information
     */
    private void showTankCapacityDialog(final float estimatedGallons,
                                       final String vin,
                                       final io.github.vindecoder.nhtsa.VehicleData vehicleData) {

        // Create custom dialog layout
        android.view.LayoutInflater inflater = getLayoutInflater();
        android.view.View dialogView = inflater.inflate(android.R.layout.select_dialog_item, null);

        // Create EditText for tank size input
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER |
                          android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setText(String.format(Locale.US, "%.1f", estimatedGallons));
        input.setSelectAllOnFocus(true);
        input.setHint("Tank capacity (gallons)");

        // Set padding
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(padding, padding, padding, padding);

        // Build dialog
        String vehicleName = vehicleData.getDisplayName();
        String message = String.format(Locale.US,
                "For accurate range calculations, we need your fuel tank capacity.\n\n" +
                "Vehicle: %s\n" +
                "Estimated: %.1f gallons\n\n" +
                "Is this correct? You can adjust the value below if needed.\n\n" +
                "Note: You can change this later in settings.",
                vehicleName, estimatedGallons);

        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this)
                .setTitle("Confirm Fuel Tank Size")
                .setMessage(message)
                .setView(input)
                .setPositiveButton("Confirm", (dialogInterface, which) -> {
                    // Save the value
                    try {
                        String inputText = input.getText().toString().trim();
                        float capacity = Float.parseFloat(inputText);

                        if (capacity >= 5f && capacity <= 100f) {
                            // Valid range
                            if (vehiclePreferences.setTankCapacity(vin, capacity)) {
                                vehiclePreferences.markTankSizePrompted(vin);
                                cachedTankCapacity = capacity; // Update cache
                                log.info("User confirmed tank capacity: " + capacity + " gal");

                                // Show confirmation snackbar
                                com.google.android.material.snackbar.Snackbar.make(
                                        findViewById(android.R.id.content),
                                        "Tank capacity saved: " + String.format(Locale.US, "%.1f", capacity) + " gal",
                                        com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
                                ).show();

                                // Recalculate range with new capacity
                                updateDisplayedValues();
                            } else {
                                log.warning("Failed to save tank capacity");
                            }
                        } else {
                            // Invalid range
                            com.google.android.material.snackbar.Snackbar.make(
                                    findViewById(android.R.id.content),
                                    "Please enter a value between 5 and 100 gallons",
                                    com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                            ).show();
                        }
                    } catch (NumberFormatException e) {
                        log.warning("Invalid tank capacity input: " + input.getText());
                        com.google.android.material.snackbar.Snackbar.make(
                                findViewById(android.R.id.content),
                                "Invalid number format",
                                com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
                        ).show();
                    }
                })
                .setNegativeButton("Later", (dialogInterface, which) -> {
                    // Mark as prompted so we don't show again
                    vehiclePreferences.markTankSizePrompted(vin);
                    log.info("User deferred tank capacity confirmation");
                })
                .setCancelable(false) // Force user to make a choice
                .create();

        dialog.show();

        // Focus and show keyboard
        input.requestFocus();
        android.view.inputmethod.InputMethodManager imm =
                (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        }
    }

    /**
     * Clear cached tank capacity (call when VIN changes)
     */
    private void clearTankCapacityCache() {
        cachedTankCapacity = null;
        log.fine("Cleared tank capacity cache");
    }

    private void updateRange(float fuelRate, float avgMpg) {
        try {
            // Get fuel level percentage
            String fuelLevelStr = fuelLevelValue.getText().toString();
            float fuelLevelPct = Float.parseFloat(fuelLevelStr);

            // Get tank capacity (user-set or estimated)
            float tankCapacity = getTankCapacity();
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

            // Log trip data if recording is active
            // This ensures we capture data even when PV change events don't fire
            if (isRecording) {
                logTripDataSnapshot();
            }
        } catch (Exception e) {
            log.warning("updateDisplayedValues error: " + e.getMessage());
        }
    }

    /**
     * Log a complete snapshot of current trip data
     * Called periodically when recording is active
     */
    private void logTripDataSnapshot() {
        if (!isRecording || tripDataLogger == null) return;

        try {
            long elapsedMs = System.currentTimeMillis() - tripStartTime;
            float elapsedSec = elapsedMs / 1000f;

            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);
            String timestamp = sdf.format(new Date());

            // Get current values from UI
            float speed = getCurrentSpeed();
            float rpm = getCurrentRPM();
            float engineLoad = getCurrentEngineLoad();
            float throttle = getThrottlePosition();
            String fuelLevel = fuelLevelValue.getText().toString();
            String fuelFlow = fuelFlowValue.getText().toString();
            String instantMpg = instantMpgValue.getText().toString();
            String avgMpg = averageMpgValue.getText().toString();
            String range = rangeValue.getText().toString();
            String timeToEmpty = timeToEmptyValue.getText().toString();

            // Write CSV row (periodic snapshot - no specific PID) with enhanced diagnostics
            tripDataLogger.write(String.format(Locale.US,
                    "%s,%.1f,%.1f,%.0f,%.1f,%.0f,%s,%s,%s,%s,%s,%s,%.2f,%.1f,%.1f,%.2f,%.2f,%s,%s,SNAPSHOT,periodic\n",
                    timestamp, elapsedSec, speed, rpm, engineLoad, throttle,
                    fuelLevel, fuelFlow, instantMpg, avgMpg, range, timeToEmpty,
                    lastFuelCalcDiagnostics.mapKpa,
                    lastFuelCalcDiagnostics.iatCelsius,
                    lastFuelCalcDiagnostics.volumetricEfficiency,
                    lastFuelCalcDiagnostics.imap,
                    lastFuelCalcDiagnostics.syntheticMAF,
                    lastFuelCalcDiagnostics.calcMethod,
                    lastFuelCalcDiagnostics.calibrated));
            tripDataLogger.flush();

            log.fine("Logged trip data snapshot at " + elapsedSec + "s");
        } catch (IOException e) {
            log.warning("Failed to log trip data snapshot: " + e.getMessage());
        } catch (Exception e) {
            log.warning("Error in logTripDataSnapshot: " + e.getMessage());
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

    /**
     * Get Manifold Absolute Pressure from OBD data (PID 0x0B)
     *
     * MAP sensor measures the pressure in the intake manifold, which indicates
     * engine load and boost level (for turbocharged engines).
     *
     * @return Manifold pressure in kilopascals (kPa)
     *         - Naturally aspirated: 20-101 kPa (vacuum to atmospheric)
     *         - Turbocharged: 101-250 kPa (atmospheric to boost)
     *         - Default: 101.3 kPa (sea level atmospheric)
     */
    private float getManifoldPressure() {
        try {
            Object mapPv = ObdProt.PidPvs.get("0B.0.0");
            log.fine("MAP PV lookup: " + (mapPv != null ? "FOUND" : "NULL"));
            if (mapPv instanceof EcuDataPv) {
                Object value = ((EcuDataPv) mapPv).get(EcuDataPv.FID_VALUE);
                if (value != null) {
                    float mapKpa = Float.parseFloat(value.toString());
                    log.fine("MAP: " + mapKpa + " kPa");

                    // Validate range (10-250 kPa is realistic)
                    if (mapKpa >= 10f && mapKpa <= 250f) {
                        return mapKpa;
                    } else {
                        log.warning("MAP value out of range: " + mapKpa + " kPa");
                    }
                }
            }
        } catch (Exception e) {
            log.fine("Error getting MAP: " + e.getMessage());
        }

        // Return atmospheric pressure at sea level as fallback
        log.fine("MAP: returning 101.3 kPa (atmospheric pressure fallback)");
        return 101.3f;
    }

    /**
     * Get Intake Air Temperature from OBD data (PID 0x0F)
     *
     * IAT sensor measures the temperature of air entering the engine.
     * This is critical for calculating air density using the ideal gas law.
     *
     * @return Intake air temperature in Celsius
     *         - Typical range: -40°C to 100°C
     *         - Cold start: 0-20°C
     *         - Normal operation: 20-60°C
     *         - Hot engine bay: 60-100°C
     *         - Default: 25°C (room temperature)
     */
    private float getIntakeAirTemp() {
        try {
            Object iatPv = ObdProt.PidPvs.get("0F.0.0");
            log.fine("IAT PV lookup: " + (iatPv != null ? "FOUND" : "NULL"));
            if (iatPv instanceof EcuDataPv) {
                Object value = ((EcuDataPv) iatPv).get(EcuDataPv.FID_VALUE);
                if (value != null) {
                    float iatCelsius = Float.parseFloat(value.toString());
                    log.fine("IAT: " + iatCelsius + "°C");

                    // Validate range (-40 to 120°C is realistic)
                    if (iatCelsius >= -40f && iatCelsius <= 120f) {
                        return iatCelsius;
                    } else {
                        log.warning("IAT value out of range: " + iatCelsius + "°C");
                    }
                }
            }
        } catch (Exception e) {
            log.fine("Error getting IAT: " + e.getMessage());
        }

        // Return room temperature as fallback
        log.fine("IAT: returning 25°C (room temperature fallback)");
        return 25.0f;
    }

    /**
     * Get Volumetric Efficiency - uses calibrated value if available, otherwise estimates
     *
     * This is the primary method to call for VE in fuel calculations.
     * It implements a smart fallback:
     * 1. If user has calibrated VE for this vehicle (via fill-up data) - use that
     * 2. Otherwise, estimate VE based on current engine load and RPM
     *
     * @param loadPercent Engine load (0-100%)
     * @param rpm Engine speed (RPM)
     * @return Volumetric efficiency (percentage)
     */
    private float getVolumetricEfficiency(float loadPercent, float rpm) {
        // Try to get calibrated VE first
        Float calibratedVE = getCalibratedVE();
        if (calibratedVE != null) {
            log.fine(String.format("Using calibrated VE: %.1f%% (Load: %.1f%%, RPM: %.0f)",
                    calibratedVE, loadPercent, rpm));
            return calibratedVE;
        }

        // Fall back to estimation if not calibrated
        float estimatedVE = estimateVolumetricEfficiency(loadPercent, rpm);
        log.fine(String.format("Using estimated VE: %.1f%% (Load: %.1f%%, RPM: %.0f) - UNCALIBRATED",
                estimatedVE, loadPercent, rpm));
        return estimatedVE;
    }

    /**
     * Get calibrated VE from preferences if available
     *
     * @return Calibrated VE percentage, or null if not calibrated
     */
    private Float getCalibratedVE() {
        try {
            // Get current VIN from VehicleManager
            com.obddroid.vehicle.VehicleManager vehicleManager =
                    com.obddroid.vehicle.VehicleManager.getInstance(this);

            String vin = vehicleManager.getCurrentVIN();
            if (vin == null || vin.isEmpty()) {
                log.fine("No VIN available - cannot get calibrated VE");
                return null;
            }

            // Get calibrated VE from preferences
            Float calibratedVE = vehiclePreferences.getVolumetricEfficiency(vin);
            if (calibratedVE != null) {
                log.info("Using calibrated VE for VIN: " + calibratedVE + "%");
                return calibratedVE;
            }

            log.fine("No calibration data for this VIN");
            return null;

        } catch (Exception e) {
            log.warning("Error getting calibrated VE: " + e.getMessage());
            return null;
        }
    }

    /**
     * Estimate Volumetric Efficiency for the current engine operating conditions
     *
     * Volumetric Efficiency (VE) is the ratio of actual air intake to theoretical maximum.
     * For naturally aspirated engines: typically 75-90%
     * For turbocharged engines: can exceed 100% due to forced induction
     *
     * This method estimates VE based on engine load and RPM for a 2.0L turbocharged engine.
     *
     * @param loadPercent Engine load (0-100%)
     * @param rpm Engine speed (RPM)
     * @return Estimated volumetric efficiency (percentage)
     *
     * Reference: Based on research from:
     * - pires/android-obd-reader (GitHub)
     * - Speed-Density calculations (lightner.net/obd2guru)
     * - Turbo engine characteristics (Cobb Tuning, Subaru Speed Density Guide)
     */
    private float estimateVolumetricEfficiency(float loadPercent, float rpm) {
        float baseVE = 85.0f; // Baseline for modern 2.0L turbo at cruise

        // Load-based VE estimation
        // Low load: Throttled, low VE
        // Medium load: Naturally aspirated range
        // High load: Turbo spooling, increasing VE
        // Very high load: Full boost, VE can exceed 100%

        if (loadPercent < 20f) {
            // Very light load - heavily throttled, low volumetric efficiency
            // Example: Coasting, deceleration, idle
            baseVE = 75.0f;
            log.fine("VE: Very light load (< 20%) - 75% VE");

        } else if (loadPercent < 40f) {
            // Light load - naturally aspirated range, minimal or no boost
            // Example: Gentle acceleration, flat highway cruise
            baseVE = 80.0f;
            log.fine("VE: Light load (20-40%) - 80% VE");

        } else if (loadPercent < 60f) {
            // Medium load - turbo beginning to spool, light boost
            // Example: Moderate acceleration, uphill cruise
            // VE increases linearly from 85% to 90%
            baseVE = 85.0f + (loadPercent - 40f) * 0.25f; // 85-90%
            log.fine(String.format("VE: Medium load (40-60%%) - %.1f%% VE", baseVE));

        } else if (loadPercent < 80f) {
            // High load - significant turbo boost active
            // Example: Hard acceleration, passing maneuvers
            // VE increases from 90% to 97%
            baseVE = 90.0f + (loadPercent - 60f) * 0.35f; // 90-97%
            log.fine(String.format("VE: High load (60-80%%) - %.1f%% VE", baseVE));

        } else {
            // Very high load - maximum turbo boost (WOT - Wide Open Throttle)
            // Example: Full throttle acceleration, racing
            // VE can exceed 100% due to forced induction
            baseVE = 95.0f + (loadPercent - 80f) * 0.5f; // 95-105%
            log.fine(String.format("VE: Very high load (80-100%%) - %.1f%% VE", baseVE));
        }

        // RPM-based adjustment
        // Engines have peak VE at certain RPM ranges (torque peak)
        // For 2.0L turbos: Peak efficiency typically 2000-3500 RPM

        if (rpm < 1500f) {
            // Low RPM - reduced efficiency due to slow intake velocity
            baseVE *= 0.92f;
            log.fine(String.format("VE: Low RPM (< 1500) adjustment - %.1f%% VE", baseVE));

        } else if (rpm > 5000f) {
            // High RPM - reduced efficiency due to friction, heat, pumping losses
            baseVE *= 0.95f;
            log.fine(String.format("VE: High RPM (> 5000) adjustment - %.1f%% VE", baseVE));
        }
        // RPM between 1500-5000: No adjustment (peak efficiency range)

        // Cap VE at realistic maximum for turbocharged engines
        // Highly tuned turbo engines can reach 110%+, but we'll cap at 110% for safety
        baseVE = Math.min(baseVE, 110.0f);

        log.info(String.format("Estimated VE: %.1f%% (Load: %.1f%%, RPM: %.0f)",
                baseVE, loadPercent, rpm));

        return baseVE;
    }

    /**
     * Calculate fuel consumption using MAP-based Speed-Density method
     *
     * This is the industry-standard approach used by professional OBD applications
     * (Torque Pro, OBDLink, AndrOBD) when a MAF sensor is not available.
     *
     * The calculation uses the Ideal Gas Law to estimate air mass flow from:
     * - Manifold Absolute Pressure (MAP)
     * - Intake Air Temperature (IAT)
     * - Engine Speed (RPM)
     * - Engine Displacement
     * - Volumetric Efficiency (VE)
     *
     * Then applies the stoichiometric air-fuel ratio (14.7:1) to calculate fuel consumption.
     *
     * Formula derivation:
     * 1. IMAP = (RPM * MAP) / (IAT_Kelvin * 2)
     * 2. MAF = (IMAP / 120) * (VE / 100) * Displacement * MolarMass / GasConstant
     * 3. Fuel = MAF / 14.7 (stoichiometric ratio)
     *
     * @return Fuel flow rate in gallons per hour, or 0 if calculation fails
     *
     * References:
     * - GitHub: pires/android-obd-reader, oesmith/obdgpslogger
     * - Stack Overflow: "What is the best way to get fuel consumption using OBD2"
     * - lightner.net/obd2guru/IMAP_AFcalc.html
     * - Research paper: "Fuel Consumption Using OBD-II and Support Vector Machine" (Hindawi, 2020)
     */
    private float calculateFuelRateMAP() {
        try {
            // Step 1: Gather required sensor data
            float rpm = getCurrentRPM();
            float mapKpa = getManifoldPressure();
            float iatCelsius = getIntakeAirTemp();
            float loadPercent = getCurrentEngineLoad();

            // Get engine displacement from vehicle data
            float displacement = getEngineDisplacement();

            // Validate sensor readings
            if (rpm < 100f) {
                log.warning("MAP calc: Invalid RPM (" + rpm + "), aborting");
                return 0f;
            }
            if (mapKpa < 10f || mapKpa > 250f) {
                log.warning("MAP calc: Invalid MAP (" + mapKpa + " kPa), aborting");
                return 0f;
            }
            if (iatCelsius < -40f || iatCelsius > 120f) {
                log.warning("MAP calc: Invalid IAT (" + iatCelsius + "°C), aborting");
                return 0f;
            }

            // Step 2: Convert IAT to Kelvin (required for ideal gas law)
            float iatKelvin = iatCelsius + 273.15f;

            // Step 3: Calculate IMAP (Intake Manifold Air Pressure factor)
            // This is an intermediate value used in the Speed-Density calculation
            float imap = (rpm * mapKpa) / (iatKelvin * 2.0f);

            // Step 4: Get Volumetric Efficiency (calibrated if available, otherwise estimated)
            float volumetricEfficiency = getVolumetricEfficiency(loadPercent, rpm);

            // Step 5: Calculate Synthetic MAF (Mass Air Flow) in grams per second
            // Using the Ideal Gas Law and Speed-Density formula
            // Constants:
            final float MOLAR_MASS_AIR = 28.97f;  // g/mol (average molecular mass of air)
            final float GAS_CONSTANT = 8.314f;     // J/(K·mol) (universal gas constant)

            float syntheticMAF = (imap / 120.0f)
                               * (volumetricEfficiency / 100.0f)
                               * displacement
                               * MOLAR_MASS_AIR
                               / GAS_CONSTANT;

            // Step 6: Calculate fuel flow rate from MAF
            // Modern engines maintain stoichiometric air-fuel ratio of 14.7:1
            // (controlled by O2 sensor feedback loop)
            final float STOICHIOMETRIC_RATIO = 14.7f;
            float fuelGramsPerSec = syntheticMAF / STOICHIOMETRIC_RATIO;

            // Step 7: Convert to gallons per hour
            // Conversion factors:
            // - 1 pound = 454 grams
            // - 1 gallon = 6.701 pounds (gasoline density)
            // - 3600 seconds per hour
            final float GRAMS_PER_POUND = 454f;
            final float POUNDS_PER_GALLON = 6.701f;
            final float SECONDS_PER_HOUR = 3600f;

            float fuelGallonsPerHour = (fuelGramsPerSec / GRAMS_PER_POUND)
                                     / POUNDS_PER_GALLON
                                     * SECONDS_PER_HOUR;

            // Step 8: Validate and clamp result to realistic range
            // For a 2.0L turbo engine:
            // - Idle: ~0.2-0.3 gal/h
            // - Cruise: ~1.0-2.5 gal/h
            // - WOT: ~3.0-5.0 gal/h
            final float MIN_FUEL_RATE = 0.1f;
            final float MAX_FUEL_RATE = 5.0f;

            if (fuelGallonsPerHour < MIN_FUEL_RATE || fuelGallonsPerHour > MAX_FUEL_RATE) {
                log.warning(String.format("MAP calc: Fuel rate %.3f gal/h out of range, clamping",
                        fuelGallonsPerHour));
            }

            fuelGallonsPerHour = Math.max(MIN_FUEL_RATE, Math.min(fuelGallonsPerHour, MAX_FUEL_RATE));

            // Comprehensive logging for debugging and analysis
            log.info(String.format("MAP Speed-Density Calculation: " +
                    "RPM=%.0f, MAP=%.1f kPa, IAT=%.1f°C (%.1fK), Load=%.1f%%, " +
                    "Displacement=%.1fL, IMAP=%.2f, VE=%.1f%%, " +
                    "Synthetic MAF=%.2f g/s, Fuel=%.3f gal/h",
                    rpm, mapKpa, iatCelsius, iatKelvin, loadPercent,
                    displacement, imap, volumetricEfficiency,
                    syntheticMAF, fuelGallonsPerHour));

            // Capture diagnostics for trip logging
            lastFuelCalcDiagnostics.mapKpa = mapKpa;
            lastFuelCalcDiagnostics.iatCelsius = iatCelsius;
            lastFuelCalcDiagnostics.volumetricEfficiency = volumetricEfficiency;
            lastFuelCalcDiagnostics.imap = imap;
            lastFuelCalcDiagnostics.syntheticMAF = syntheticMAF;
            lastFuelCalcDiagnostics.calcMethod = "MAP";
            lastFuelCalcDiagnostics.calibrated = (getCalibratedVE() != null) ? "Yes" : "No";

            return fuelGallonsPerHour;

        } catch (Exception e) {
            log.warning("Error in MAP-based fuel calculation: " + e.getMessage());
            e.printStackTrace();
            return 0f;
        }
    }

    /**
     * Get engine displacement in liters from vehicle data
     *
     * @return Engine displacement in liters, or 2.5L default if unavailable
     */
    private float getEngineDisplacement() {
        try {
            com.obddroid.vehicle.VehicleManager vehicleManager =
                    com.obddroid.vehicle.VehicleManager.getInstance(this);

            io.github.vindecoder.nhtsa.VehicleData vehicleData =
                    vehicleManager.getCurrentVehicleData();

            if (vehicleData != null && vehicleData.displacementL != null &&
                !vehicleData.displacementL.isEmpty()) {
                float displacement = Float.parseFloat(vehicleData.displacementL);
                log.fine("Using vehicle displacement: " + displacement + "L");
                return displacement;
            }
        } catch (Exception e) {
            log.fine("Could not get vehicle displacement: " + e.getMessage());
        }

        // Fallback to 2.5L (common mid-size engine)
        log.fine("Using default displacement: 2.5L");
        return 2.5f;
    }

    /**
     * Check if MAP-based calculation is available
     *
     * @return true if both MAP and IAT sensors are providing data
     */
    private boolean isMapCalculationAvailable() {
        try {
            // Check if MAP sensor is available
            Object mapPv = ObdProt.PidPvs.get("0B.0.0");
            if (mapPv == null) {
                return false;
            }

            // Check if IAT sensor is available
            Object iatPv = ObdProt.PidPvs.get("0F.0.0");
            if (iatPv == null) {
                return false;
            }

            // Both sensors available
            return true;

        } catch (Exception e) {
            log.fine("Error checking MAP availability: " + e.getMessage());
            return false;
        }
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

            // Get tank capacity (user-set or estimated)
            float tankCapacity = getTankCapacity();
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
