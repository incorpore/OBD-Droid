package com.obddroid.ui.activities;

import android.content.ContentValues;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.obddroid.R;
import com.obddroid.core.ecu.EcuDataPv;
import com.obddroid.core.obd.ObdProt;
import com.obddroid.core.pvs.PvChangeEvent;
import com.obddroid.core.pvs.PvChangeListener;
import com.obddroid.services.CommService;
import com.obddroid.ui.components.VehicleInfoFooter;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

/**
 * Emissions Diagnostics Activity
 *
 * Displays comprehensive emissions monitoring information:
 * - Monitor readiness status (Mode 1 PID 0x01)
 * - IUMPR performance data (Mode 9 PID 0x08)
 * - Combined visual dashboard
 * - Pre-emissions test readiness check
 *
 * This combines data from multiple OBD services to provide mechanics
 * and vehicle owners with complete emissions system health information.
 */
public class EmissionsActivity extends AppCompatActivity implements PvChangeListener {

    private static final Logger log = Logger.getLogger(EmissionsActivity.class.getName());

    // UI Components
    private TextView overallStatusText;
    private TextView overallStatusSubtext;
    private TextView lastUpdatedText;
    private CardView overallStatusCard;
    private LinearLayout monitorsContainer;
    private VehicleInfoFooter vehicleInfoFooter;
    private View snackbarAnchor;  // Anchor view for snackbars

    // Monitor tracking
    private Map<String, MonitorData> monitorDataMap = new java.util.LinkedHashMap<>();

    // Update handler
    private Handler updateHandler = new Handler(Looper.getMainLooper());
    private static final long UPDATE_INTERVAL = 2000; // Update every 2 seconds

    // Service management
    private int previousService = ObdProt.OBD_SVC_NONE;
    private boolean dataQueryInProgress = false;

    // Colors
    private static final int COLOR_READY = Color.parseColor("#4CAF50");      // Green
    private static final int COLOR_NOT_READY = Color.parseColor("#FFC107");  // Amber
    private static final int COLOR_ERROR = Color.parseColor("#F44336");       // Red
    private static final int COLOR_UNKNOWN = Color.parseColor("#9E9E9E");    // Gray

    /**
     * Data structure to hold monitor information
     */
    private static class MonitorData {
        String name;
        boolean isReady;
        boolean isAvailable;
        int conditions;
        int completions;

        MonitorData(String name) {
            this.name = name;
            this.isReady = false;
            this.isAvailable = false;  // Default to NOT available - only mark available when we find data
            this.conditions = 0;
            this.completions = 0;
        }

        float getRatio() {
            if (conditions == 0) return 0f;
            return (float) completions / conditions;
        }

        int getPercentage() {
            return Math.round(getRatio() * 100);
        }

        String getPercentageDisplay() {
            int percentage = getPercentage();
            if (percentage > 100) {
                return "100%+";
            }
            return String.valueOf(percentage) + "%";
        }

        /**
         * Get IUMPR quality indicator for regulatory compliance
         * NOTE: This is DIFFERENT from emissions readiness!
         * - Emissions Ready: Needs ≥1 completion
         * - IUMPR Quality: Measures how frequently the monitor runs
         */
        String getIUMPRQuality() {
            if (completions == 0) {
                return null;  // No quality indicator if never completed
            }

            float ratio = getRatio();

            // EVAP has stricter CARB requirement (52%)
            if (name.contains("EVAP")) {
                if (ratio >= 0.52f) return "🟢 Excellent";  // Meets CARB 52% minimum
                if (ratio >= 0.10f) return "🟡 Good";       // Meets basic 10% minimum
                return "🟠 Low Frequency";                    // Below minimums but still ready
            }

            // Other monitors use 10% threshold
            if (ratio >= 0.10f) return "🟢 Excellent";      // Meets minimum
            return "🟠 Low Frequency";                        // Below minimum but still ready
        }

        /**
         * Get explanation of IUMPR quality (for tooltip/help)
         */
        String getIUMPRQualityExplanation() {
            if (completions == 0) return null;

            String quality = getIUMPRQuality();
            if (quality == null) return null;

            if (quality.contains("Excellent")) {
                return "Monitor runs frequently - exceeds regulatory minimums";
            } else if (quality.contains("Good")) {
                return "Monitor runs regularly - meets regulatory minimums";
            } else {
                return "Monitor runs infrequently but has completed successfully";
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        log.info("=== EmissionsActivity onCreate() ===");

        setContentView(R.layout.activity_emissions);

        // Set navigation bar color to match footer
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setNavigationBarColor(0xFF212121);  // #212121
        }

        // Setup action bar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Emissions Diagnostics");
        }

        // Initialize views
        initializeViews();

        // Initialize monitor data
        initializeMonitors();

        // Start periodic updates
        startPeriodicUpdates();
    }

    private void initializeViews() {
        overallStatusCard = findViewById(R.id.overall_status_card);
        overallStatusText = findViewById(R.id.overall_status_text);
        overallStatusSubtext = findViewById(R.id.overall_status_subtext);
        lastUpdatedText = findViewById(R.id.last_updated_text);
        monitorsContainer = findViewById(R.id.monitors_container);
        vehicleInfoFooter = findViewById(R.id.vehicle_info_footer);
        snackbarAnchor = findViewById(R.id.content_frame);  // Use CoordinatorLayout for snackbar creation

        log.info("Views initialized");
    }

    /**
     * Show a snackbar anchored above the vehicle footer
     */
    private void showSnackbar(String message) {
        com.google.android.material.snackbar.Snackbar snackbar =
            com.google.android.material.snackbar.Snackbar.make(snackbarAnchor, message,
                com.google.android.material.snackbar.Snackbar.LENGTH_SHORT);
        snackbar.setAnchorView(vehicleInfoFooter);  // Position above the footer
        snackbar.show();
    }

    private void initializeMonitors() {
        // Continuous monitors (always must be ready)
        monitorDataMap.put("MISFIRE", new MonitorData("Misfire Detection"));
        monitorDataMap.put("FUEL", new MonitorData("Fuel System Monitor"));
        monitorDataMap.put("CCM", new MonitorData("Comprehensive Component (CCM)"));

        // Non-continuous monitors
        monitorDataMap.put("CATALYST", new MonitorData("Catalyst Efficiency"));
        monitorDataMap.put("EVAP", new MonitorData("EVAP System"));
        monitorDataMap.put("O2SENSOR", new MonitorData("O2 Sensor Monitor"));
        monitorDataMap.put("O2HEATER", new MonitorData("O2 Sensor Heater"));
        monitorDataMap.put("EGR", new MonitorData("EGR System"));
        monitorDataMap.put("AIR", new MonitorData("Secondary Air System"));

        log.info("Monitor data structures initialized");
    }

    @Override
    protected void onResume() {
        super.onResume();
        log.info("=== EmissionsActivity onResume() ===");

        // Register PV change listeners
        if (ObdProt.PidPvs != null) {
            ObdProt.PidPvs.addPvChangeListener(this);
        }
        if (ObdProt.VidPvs != null) {
            ObdProt.VidPvs.addPvChangeListener(this);
        }

        // Request emissions data if connected
        requestEmissionsData();

        // Update display
        updateDisplay();
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Unregister listeners
        if (ObdProt.PidPvs != null) {
            ObdProt.PidPvs.removePvChangeListener(this);
        }
        if (ObdProt.VidPvs != null) {
            ObdProt.VidPvs.removePvChangeListener(this);
        }

        // Stop updates
        updateHandler.removeCallbacksAndMessages(null);

        // Restore original OBD service if we changed it
        if (dataQueryInProgress && CommService.elm != null && previousService != ObdProt.OBD_SVC_NONE) {
            log.info("Activity pausing - restoring service: " + previousService);
            CommService.elm.setService(previousService, true);
            dataQueryInProgress = false;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        log.info("onCreateOptionsMenu called - inflating menu");
        getMenuInflater().inflate(R.menu.emissions_menu, menu);
        log.info("Menu inflated, items: " + menu.size());
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        log.info("onOptionsItemSelected: ID=" + item.getItemId());

        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        } else if (item.getItemId() == R.id.action_rescan) {
            log.info("Rescan requested from overflow menu");
            updateDisplay();
            showSnackbar("Refreshing emissions data...");
            return true;
        } else if (item.getItemId() == R.id.action_export) {
            log.info("Export requested from overflow menu");
            exportEmissionsReport();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void pvChanged(PvChangeEvent event) {
        runOnUiThread(this::updateDisplay);
    }

    private void requestEmissionsData() {
        log.info("=== Checking for emissions data ===");

        // Check if data already exists
        boolean hasPidData = ObdProt.PidPvs != null && !ObdProt.PidPvs.isEmpty();
        boolean hasVidData = ObdProt.VidPvs != null && !ObdProt.VidPvs.isEmpty();
        log.info("PidPvs: " + (hasPidData ? ObdProt.PidPvs.size() + " items" : "empty/null"));
        log.info("VidPvs: " + (hasVidData ? ObdProt.VidPvs.size() + " items" : "empty/null"));

        if (CommService.elm == null) {
            log.warning("Not connected to vehicle - will display cached data if available");
        } else {
            log.info("Connected - current OBD service: " + CommService.elm.getService());
        }

        // Always try to update display with whatever data exists
        log.info("Updating display with available data...");
        updateDisplay();

        // If we have both types of data, we're done
        if (hasPidData && hasVidData) {
            log.info("Both PidPvs and VidPvs have data - emissions page populated");
            return;
        }

        // If missing data and connected, suggest user to navigate to other screens first
        if (CommService.elm != null && (!hasPidData || !hasVidData)) {
            log.info("Some data missing - user should visit Live Data or Vehicle Info tabs first");
            updateHandler.postDelayed(() -> {
                String message = "Tip: Visit 'Live Data' and 'Vehicle Info' tabs first to populate emissions data";
                showSnackbar(message);
            }, 1000);
        }
    }

    private void startPeriodicUpdates() {
        updateHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                updateDisplay();
                updateHandler.postDelayed(this, UPDATE_INTERVAL);
            }
        }, UPDATE_INTERVAL);
    }

    private boolean hasExportableData() {
        for (MonitorData monitor : monitorDataMap.values()) {
            if (monitor.isAvailable || monitor.conditions > 0 || monitor.completions > 0) {
                return true;
            }
        }
        return false;
    }

    private java.util.List<MonitorData> getOrderedMonitorList() {
        java.util.List<MonitorData> ordered = new java.util.ArrayList<>();
        ordered.add(monitorDataMap.get("MISFIRE"));
        ordered.add(monitorDataMap.get("FUEL"));
        ordered.add(monitorDataMap.get("CCM"));
        ordered.add(monitorDataMap.get("CATALYST"));
        ordered.add(monitorDataMap.get("EVAP"));
        ordered.add(monitorDataMap.get("O2SENSOR"));
        ordered.add(monitorDataMap.get("O2HEATER"));
        ordered.add(monitorDataMap.get("EGR"));
        ordered.add(monitorDataMap.get("AIR"));
        return ordered;
    }

    private void exportEmissionsReport() {
        if (!hasExportableData()) {
            showSnackbar("No emissions data available to export yet");
            return;
        }

        // Ensure latest values are displayed/exported
        updateDisplay();

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String displayTimestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
        String fileName = "emissions_report_" + timestamp + ".csv";

        OutputStreamWriter writer = null;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "text/csv");
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/OBDroid");

                Uri uri = getContentResolver().insert(MediaStore.Files.getContentUri("external"), values);
                if (uri == null) {
                    throw new IOException("Failed to create MediaStore entry");
                }

                OutputStream outputStream = getContentResolver().openOutputStream(uri);
                if (outputStream == null) {
                    throw new IOException("Failed to open MediaStore output stream");
                }
                writer = new OutputStreamWriter(outputStream);
            } else {
                File documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
                File obdroidDir = new File(documentsDir, "OBDroid");
                if (!obdroidDir.exists() && !obdroidDir.mkdirs()) {
                    throw new IOException("Unable to create export directory: " + obdroidDir.getAbsolutePath());
                }
                File csvFile = new File(obdroidDir, fileName);
                writer = new OutputStreamWriter(new FileOutputStream(csvFile));
            }

            if (writer == null) {
                throw new IOException("Output stream writer was null");
            }

            // Compute summary stats
            int availableCount = 0;
            int readyCount = 0;
            for (MonitorData monitor : monitorDataMap.values()) {
                if (monitor.isAvailable) {
                    availableCount++;
                    if (monitor.isReady) {
                        readyCount++;
                    }
                }
            }
            int notReadyCount = availableCount - readyCount;

            writer.write(String.format(Locale.US,
                    "OBD-Droid Emissions Report,%s\n", displayTimestamp));
            writer.write(String.format(Locale.US,
                    "Available Monitors,%d\nReady Monitors,%d\nMonitors Needing Drive Cycle,%d\n\n",
                    availableCount, readyCount, notReadyCount));
            writer.write("Monitor,Available,Ready,Completions,Conditions,Completion %,IUMPR Quality\n");

            for (MonitorData monitor : getOrderedMonitorList()) {
                if (monitor == null) {
                    continue;
                }
                String completionPercent = monitor.getPercentageDisplay();
                String quality = monitor.getIUMPRQuality();
                // Avoid commas disrupting CSV by replacing with semicolons
                if (quality != null) {
                    quality = quality.replace(",", ";");
                } else {
                    quality = "";
                }

                writer.write(String.format(Locale.US,
                        "\"%s\",%s,%s,%d,%d,%s,%s\n",
                        monitor.name,
                        monitor.isAvailable ? "Yes" : "No",
                        monitor.isReady ? "Yes" : "No",
                        monitor.completions,
                        monitor.conditions,
                        completionPercent,
                        quality));
            }

            writer.flush();
            showSnackbar("Emissions report saved: " + fileName);
            log.info("Emissions report exported successfully: " + fileName);
        } catch (IOException e) {
            log.warning("Failed to export emissions report: " + e.getMessage());
            showSnackbar("Failed to export emissions report");
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private void updateDisplay() {
        log.info(">>> updateDisplay() called");
        log.info("  PidPvs: " + (ObdProt.PidPvs != null ? ObdProt.PidPvs.size() + " items" : "null"));
        log.info("  VidPvs: " + (ObdProt.VidPvs != null ? ObdProt.VidPvs.size() + " items" : "null"));

        // Update monitor readiness from PID 0x01
        updateMonitorReadiness();

        // Update IUMPR data from Mode 9 PID 0x08
        updateIUMPRData();

        // Count how many monitors have data
        int monitorsWithData = 0;
        for (MonitorData monitor : monitorDataMap.values()) {
            if (monitor.conditions > 0 || monitor.isAvailable) {
                monitorsWithData++;
            }
        }
        log.info("  Monitors with data: " + monitorsWithData + "/9");

        // Update overall status
        updateOverallStatus();

        // Rebuild monitor cards
        rebuildMonitorCards();

        log.info("<<< updateDisplay() complete");
    }

    private void updateMonitorReadiness() {
        if (ObdProt.PidPvs == null || ObdProt.PidPvs.isEmpty()) {
            log.info("updateMonitorReadiness: PidPvs is " + (ObdProt.PidPvs == null ? "null" : "empty"));
            return;
        }

        log.info("updateMonitorReadiness: Parsing " + ObdProt.PidPvs.size() + " PID entries...");

        // Iterate through PidPvs to find monitor status entries
        int foundCount = 0;
        for (Object key : ObdProt.PidPvs.keySet()) {
            Object value = ObdProt.PidPvs.get(key);
            if (value instanceof EcuDataPv) {
                EcuDataPv pv = (EcuDataPv) value;
                String description = String.valueOf(pv.get(EcuDataPv.FID_DESCRIPT));
                Object formattedVal = pv.get(EcuDataPv.FID_VALUE);

                if (description == null || formattedVal == null) continue;

                String statusStr = formattedVal.toString();

                // Parse the status string format:
                // "(*) Available\n(*) Complete" or "(  ) Not available\n(  ) Incomplete"
                boolean isAvailable = statusStr.contains("(*) Available") || statusStr.contains("(*) Complete");
                boolean isComplete = statusStr.contains("(*) Complete");

                // Map descriptions to monitors
                MonitorData monitor = null;
                if (description.toLowerCase().contains("misfire")) {
                    monitor = monitorDataMap.get("MISFIRE");
                } else if (description.toLowerCase().contains("fuel system")) {
                    monitor = monitorDataMap.get("FUEL");
                } else if (description.toLowerCase().contains("component")) {
                    monitor = monitorDataMap.get("CCM");
                } else if (description.toLowerCase().contains("catalyst")) {
                    monitor = monitorDataMap.get("CATALYST");
                } else if (description.toLowerCase().contains("evap")) {
                    monitor = monitorDataMap.get("EVAP");
                } else if (description.toLowerCase().contains("oxygen sensor heater")) {
                    monitor = monitorDataMap.get("O2HEATER");
                } else if (description.toLowerCase().contains("oxygen sensor")) {
                    monitor = monitorDataMap.get("O2SENSOR");
                } else if (description.toLowerCase().contains("egr")) {
                    monitor = monitorDataMap.get("EGR");
                } else if (description.toLowerCase().contains("secondary air") || description.toLowerCase().contains("air system")) {
                    monitor = monitorDataMap.get("AIR");
                }

                if (monitor != null) {
                    monitor.isAvailable = isAvailable;
                    // Mode 01 PID 01 readiness status is AUTHORITATIVE for emissions testing
                    // This takes precedence over IUMPR ratio (Mode 09)
                    monitor.isReady = isComplete;
                    foundCount++;
                    log.info("  Found monitor: " + description.substring(0, Math.min(40, description.length())) + " -> available=" + isAvailable + ", complete=" + isComplete);
                }
            }
        }
        log.info("updateMonitorReadiness: Found " + foundCount + " monitor status entries");
    }

    private void updateIUMPRData() {
        if (ObdProt.VidPvs == null || ObdProt.VidPvs.isEmpty()) {
            log.info("updateIUMPRData: VidPvs is " + (ObdProt.VidPvs == null ? "null" : "empty"));
            return;
        }

        log.info("updateIUMPRData: Parsing " + ObdProt.VidPvs.size() + " VID entries...");

        // Parse all IUMPR data by iterating through VidPvs
        // We need to find completions and conditions for each monitor
        int obdConditions = 0;
        int ignitionCounter = 0;
        int foundIUMPRCount = 0;

        // Catalyst data
        int catComp1 = 0, catCond1 = 0, catComp2 = 0, catCond2 = 0;
        // O2 Sensor data
        int o2sComp1 = 0, o2sCond1 = 0, o2sComp2 = 0, o2sCond2 = 0;
        // EVAP data
        int evapComp = 0, evapCond = 0;
        // EGR data
        int egrComp = 0, egrCond = 0;
        // AIR data
        int airComp = 0, airCond = 0;
        // O2 Heater data
        int hccatComp = 0, hccatCond = 0;

        // Iterate through all VidPvs entries
        for (Object key : ObdProt.VidPvs.keySet()) {
            Object value = ObdProt.VidPvs.get(key);
            if (value instanceof EcuDataPv) {
                EcuDataPv pv = (EcuDataPv) value;
                String description = String.valueOf(pv.get(EcuDataPv.FID_DESCRIPT));
                int intValue = getIntValue(pv);

                if (description == null) continue;

                // Debug: Log all O2-related entries
                if (description.toLowerCase().contains("o2") || description.toLowerCase().contains("sensor")) {
                    log.info("  VID entry: " + description + " = " + intValue);
                }

                // Match descriptions to data fields
                if (description.contains("OBD Monitoring Conditions Encountered")) {
                    obdConditions = intValue;
                    foundIUMPRCount++;
                    log.info("  Found: OBD Conditions = " + intValue);
                } else if (description.contains("Ignition Counter")) {
                    ignitionCounter = intValue;
                    foundIUMPRCount++;
                    log.info("  Found: Ignition Counter = " + intValue);
                }
                // Catalyst Monitor
                else if (description.contains("Catalyst Monitor Completion") && description.contains("Bank 1")) {
                    catComp1 = intValue;
                } else if (description.contains("Catalyst Monitor Conditions") && description.contains("Bank 1")) {
                    catCond1 = intValue;
                } else if (description.contains("Catalyst Monitor Completion") && description.contains("Bank 2")) {
                    catComp2 = intValue;
                } else if (description.contains("Catalyst Monitor Conditions") && description.contains("Bank 2")) {
                    catCond2 = intValue;
                }
                // O2 Sensor Monitor
                else if (description.contains("O2 Sensor Monitor Completion") && description.contains("Bank 1")) {
                    o2sComp1 = intValue;
                } else if (description.contains("O2 Sensor Monitor Conditions") && description.contains("Bank 1")) {
                    o2sCond1 = intValue;
                } else if (description.contains("O2 Sensor Monitor Completion") && description.contains("Bank 2")) {
                    o2sComp2 = intValue;
                } else if (description.contains("O2 Sensor Monitor Conditions") && description.contains("Bank 2")) {
                    o2sCond2 = intValue;
                }
                // EVAP
                else if (description.contains("EVAP") && description.contains("Completion")) {
                    evapComp = intValue;
                } else if (description.contains("EVAP") && description.contains("Conditions")) {
                    evapCond = intValue;
                }
                // EGR
                else if (description.contains("EGR") && description.contains("Completion")) {
                    egrComp = intValue;
                } else if (description.contains("EGR") && description.contains("Conditions")) {
                    egrCond = intValue;
                }
                // AIR (Secondary Air)
                else if ((description.contains("AIR") || description.contains("Secondary Air")) && description.contains("Completion")) {
                    airComp = intValue;
                } else if ((description.contains("AIR") || description.contains("Secondary Air")) && description.contains("Conditions")) {
                    airCond = intValue;
                }
                // O2 Heater / Heated Catalyst
                else if (description.contains("Heated Catalyst") && description.contains("Completion")) {
                    hccatComp = intValue;
                } else if (description.contains("Heated Catalyst") && description.contains("Conditions")) {
                    hccatCond = intValue;
                }
            }
        }

        // Update continuous monitors (use OBD conditions or ignition counter)
        if (obdConditions > 0 || ignitionCounter > 0) {
            int conditions = obdConditions > 0 ? obdConditions : ignitionCounter;
            updateContinuousMonitor("MISFIRE", conditions);
            updateContinuousMonitor("FUEL", conditions);
            updateContinuousMonitor("CCM", conditions);
        }

        // Update non-continuous monitors
        updateMonitorPerformance("CATALYST", catComp1 + catComp2, catCond1 + catCond2);
        updateMonitorPerformance("O2SENSOR", o2sComp1 + o2sComp2, o2sCond1 + o2sCond2);
        updateMonitorPerformance("EVAP", evapComp, evapCond);
        updateMonitorPerformance("EGR", egrComp, egrCond);
        updateMonitorPerformance("AIR", airComp, airCond);
        updateMonitorPerformance("O2HEATER", hccatComp, hccatCond);

        log.info("updateIUMPRData: Found " + foundIUMPRCount + " IUMPR entries total");
        log.info("  OBD Conditions: " + obdConditions + ", Ignition: " + ignitionCounter);
        log.info("  Catalyst: comp=" + (catComp1+catComp2) + ", cond=" + (catCond1+catCond2));
        log.info("  O2 Sensor: comp=" + (o2sComp1+o2sComp2) + ", cond=" + (o2sCond1+o2sCond2));
        log.info("  EVAP: comp=" + evapComp + ", cond=" + evapCond);
    }

    private int getIntValue(EcuDataPv pv) {
        try {
            Object dataValue = pv.get(EcuDataPv.FID_VALUE);
            if (dataValue != null) {
                String valStr = dataValue.toString().replaceAll("[^0-9]", "");
                if (!valStr.isEmpty()) {
                    return Integer.parseInt(valStr);
                }
            }
        } catch (Exception e) {
            // Ignore parse errors
        }
        return 0;
    }

    private void updateContinuousMonitor(String monitorKey, int conditions) {
        MonitorData monitor = monitorDataMap.get(monitorKey);
        if (monitor == null) return;

        // Continuous monitors are always running when engine is on
        // So we set completions = conditions (100% completion rate)
        monitor.conditions = conditions;
        monitor.completions = conditions;

        // Continuous monitors are READY if they have any data
        // They run continuously so if we have IUMPR counts, they're operational
        if (conditions > 0) {
            monitor.isAvailable = true;  // Mark as available if we have data
            monitor.isReady = true;
        }
    }

    private void updateMonitorPerformance(String monitorKey, int completions, int conditions) {
        MonitorData monitor = monitorDataMap.get(monitorKey);
        if (monitor == null) return;

        // Update IUMPR data (Mode 09) - for display/informational purposes
        monitor.completions = completions;
        monitor.conditions = conditions;

        // Monitor is available if it has IUMPR condition data
        // This indicates the monitor is equipped and operating
        if (conditions > 0) {
            monitor.isAvailable = true;

            // FALLBACK: If Mode 01 readiness data is not available (empty PidPvs),
            // use IUMPR completion count. A monitor is "Ready" if it has completed
            // at least once (completions > 0), regardless of the IUMPR ratio.
            // The ratio (e.g., 0.002%) is for regulatory compliance tracking,
            // NOT for emissions test readiness. Mode 01 PID 01 is AUTHORITATIVE
            // when available, but this fallback prevents all monitors showing
            // "Not Ready" when Mode 01 is unavailable.
            if (ObdProt.PidPvs == null || ObdProt.PidPvs.isEmpty()) {
                monitor.isReady = (completions > 0);
            }
            // else: Mode 01 status (set in updateMonitorReadiness) takes precedence
        }
    }

    private void updateOverallStatus() {
        int availableCount = 0;
        int readyCount = 0;

        // Count available and ready monitors
        for (MonitorData monitor : monitorDataMap.values()) {
            if (monitor.isAvailable) {
                availableCount++;
                if (monitor.isReady) {
                    readyCount++;
                }
            }
        }

        int notReadyCount = availableCount - readyCount;

        log.info("=== Overall Status Calculation ===");
        log.info("  Available monitors: " + availableCount);
        log.info("  Ready monitors: " + readyCount);
        log.info("  Not ready monitors: " + notReadyCount);

        // Determine overall status
        // EPA standard: For 2001+ vehicles, 1 monitor may be not ready
        // Vehicle is ready if: (1) at least some monitors are available, AND
        //                      (2) no more than 1 monitor is not ready
        boolean hasData = availableCount > 0;
        boolean isReady = hasData && (notReadyCount <= 1);

        log.info("  hasData: " + hasData + ", isReady: " + isReady);

        if (isReady) {
            log.info("  Setting status: READY (green)");

            overallStatusCard.setCardBackgroundColor(COLOR_READY);
            overallStatusText.setText("✓ READY FOR EMISSIONS TEST");
            overallStatusSubtext.setText(String.format(
                "%d of %d monitors complete • Passes EPA standards",
                readyCount, availableCount
            ));
        } else if (!hasData) {
            // No data available yet
            log.info("  Setting status: WAITING FOR DATA (gray)");
            overallStatusCard.setCardBackgroundColor(COLOR_UNKNOWN);
            overallStatusText.setText("⚠ WAITING FOR DATA");
            overallStatusSubtext.setText("Connect to vehicle to retrieve emissions monitor status");
        } else {
            log.info("  Setting status: NOT READY (yellow)");
            overallStatusCard.setCardBackgroundColor(COLOR_NOT_READY);
            overallStatusText.setText("⚠ NOT READY FOR EMISSIONS TEST");
            overallStatusSubtext.setText(String.format(
                "%d of %d monitors complete • %d monitor(s) need drive cycle",
                readyCount, availableCount, notReadyCount
            ));
        }

        // Update timestamp
        updateTimestamp();

        log.info("=== Overall Status Update Complete ===");
    }

    private void updateTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("h:mm a", Locale.US);
        String currentTime = sdf.format(new Date());
        lastUpdatedText.setText("Last updated: " + currentTime);
    }

    private void rebuildMonitorCards() {
        monitorsContainer.removeAllViews();

        // Add continuous monitors section
        addSectionHeader("CONTINUOUS MONITORS", "(Always Running)");
        addMonitorCard(monitorDataMap.get("MISFIRE"), true);
        addMonitorCard(monitorDataMap.get("FUEL"), true);
        addMonitorCard(monitorDataMap.get("CCM"), true);

        // Add non-continuous monitors section (sorted: Ready → Not Ready → Not Equipped)
        addSectionHeader("NON-CONTINUOUS MONITORS", "(Requires Specific Conditions)");

        // Collect and sort non-continuous monitors
        java.util.List<MonitorData> nonContinuousMonitors = new java.util.ArrayList<>();
        nonContinuousMonitors.add(monitorDataMap.get("CATALYST"));
        nonContinuousMonitors.add(monitorDataMap.get("EVAP"));
        nonContinuousMonitors.add(monitorDataMap.get("O2SENSOR"));
        nonContinuousMonitors.add(monitorDataMap.get("O2HEATER"));
        nonContinuousMonitors.add(monitorDataMap.get("EGR"));
        nonContinuousMonitors.add(monitorDataMap.get("AIR"));

        // Sort: Ready monitors first, then Not Ready, then Not Equipped at bottom
        java.util.Collections.sort(nonContinuousMonitors, new java.util.Comparator<MonitorData>() {
            @Override
            public int compare(MonitorData m1, MonitorData m2) {
                // Prioritize available monitors over non-available
                if (m1.isAvailable != m2.isAvailable) {
                    return m1.isAvailable ? -1 : 1;  // Available first
                }
                // Among available monitors, prioritize ready over not ready
                if (m1.isAvailable && m2.isAvailable) {
                    if (m1.isReady != m2.isReady) {
                        return m1.isReady ? -1 : 1;  // Ready first
                    }
                }
                return 0;  // Keep original order for same status
            }
        });

        // Add sorted monitors
        for (MonitorData monitor : nonContinuousMonitors) {
            addMonitorCard(monitor, false);
        }
    }

    private void addSectionHeader(String title, String subtitle) {
        View headerView = getLayoutInflater().inflate(R.layout.emissions_section_header, monitorsContainer, false);
        TextView titleText = headerView.findViewById(R.id.section_title);
        TextView subtitleText = headerView.findViewById(R.id.section_subtitle);

        titleText.setText(title);
        subtitleText.setText(subtitle);

        monitorsContainer.addView(headerView);
    }

    private void addMonitorCard(MonitorData monitor, boolean isContinuous) {
        if (monitor == null) return;

        View cardView = getLayoutInflater().inflate(R.layout.emissions_monitor_card, monitorsContainer, false);

        // Get view components
        View statusIndicator = cardView.findViewById(R.id.monitor_status_indicator);
        TextView nameText = cardView.findViewById(R.id.monitor_name);
        TextView statusText = cardView.findViewById(R.id.monitor_status_text);
        TextView performanceText = cardView.findViewById(R.id.monitor_performance_text);
        View performanceBar = cardView.findViewById(R.id.monitor_performance_bar);

        // Set monitor name
        nameText.setText(monitor.name);

        // Set status
        if (!monitor.isAvailable) {
            statusIndicator.setBackgroundColor(COLOR_UNKNOWN);
            statusText.setText("Not Equipped");
            performanceText.setVisibility(View.GONE);
            performanceBar.setVisibility(View.GONE);
        } else if (monitor.conditions == 0) {
            // Monitor marked as "available" but has no condition data = not really available
            // Force it to not available so it doesn't count against EPA readiness
            monitor.isAvailable = false;
            statusIndicator.setBackgroundColor(COLOR_UNKNOWN);
            statusText.setText("No Data");
            performanceText.setText("Drive cycle needed");
            performanceBar.setVisibility(View.GONE);
        } else if (monitor.isReady) {
            statusIndicator.setBackgroundColor(COLOR_READY);

            // Clean, simple status text
            statusText.setText("✓ Ready");

            // Build performance text with quality indicator on separate lines
            String iumprQuality = monitor.getIUMPRQuality();
            StringBuilder perfText = new StringBuilder();

            // Line 1: Percentage and ratio
            perfText.append(String.format(
                "%s (%,d / %,d)",
                monitor.getPercentageDisplay(),
                monitor.completions,
                monitor.conditions
            ));

            // Line 2: Quality indicator (if available)
            if (iumprQuality != null) {
                perfText.append("\n").append(iumprQuality);

                // Line 3: Explanation for low frequency monitors
                if (iumprQuality.contains("Low Frequency")) {
                    perfText.append("\n⚠️ ").append(monitor.getIUMPRQualityExplanation());
                }
            }

            performanceText.setText(perfText.toString());

            // Set performance bar width
            performanceBar.setVisibility(View.VISIBLE);
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) performanceBar.getLayoutParams();
            params.weight = monitor.getRatio();
            performanceBar.setLayoutParams(params);
            performanceBar.setBackgroundColor(COLOR_READY);
        } else {
            statusIndicator.setBackgroundColor(COLOR_NOT_READY);
            statusText.setText("⚠ Not Ready");

            if (monitor.completions == 0 && monitor.conditions > 0) {
                performanceText.setText(String.format(
                    "0%% • %,d conditions encountered, 0 completions",
                    monitor.conditions
                ));
            } else {
                performanceText.setText(String.format(
                    "%s • %,d completions / %,d conditions (needs more driving)",
                    monitor.getPercentageDisplay(),
                    monitor.completions,
                    monitor.conditions
                ));
            }

            // Set performance bar width
            performanceBar.setVisibility(View.VISIBLE);
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) performanceBar.getLayoutParams();
            params.weight = monitor.getRatio();
            performanceBar.setLayoutParams(params);
            performanceBar.setBackgroundColor(COLOR_NOT_READY);
        }

        monitorsContainer.addView(cardView);
    }
}
