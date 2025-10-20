package com.obddroid.ui.activities;

import android.app.Dialog;
import android.content.ContentValues;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import org.json.JSONArray;
import org.json.JSONObject;

import com.obddroid.R;
import com.obddroid.core.ecu.EcuDataPv;
import com.obddroid.core.obd.ObdProt;
import com.obddroid.core.pvs.ProcessVariables.PvChangeEvent;
import com.obddroid.core.pvs.ProcessVariables.PvChangeListener;
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
        snackbar.getView().setElevation(6f);  // Lower than footer's 8f so it slides from behind
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
            showSnackbar("Rescanning emissions data...");
            // Clear existing data and request fresh data from vehicle
            if (CommService.elm != null) {
                log.info("Clearing cached emissions data for fresh scan");
                // Request fresh data from vehicle
                startEmissionsDataRequest();
            } else {
                showSnackbar("Not connected to vehicle");
            }
            return true;
        } else if (item.getItemId() == R.id.action_export) {
            log.info("Save Report requested from overflow menu");
            showSaveReportDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void pvChanged(PvChangeEvent event) {
        runOnUiThread(this::updateDisplay);
    }

    /**
     * Request emissions data from the vehicle
     * This is called when the activity loads to display existing cached data
     * For fresh data requests, use startEmissionsDataRequest()
     */
    private void requestEmissionsData() {
        log.info("=== Checking for emissions data ===");

        // Check if data already exists
        boolean hasPidData = ObdProt.PidPvs != null && !ObdProt.PidPvs.isEmpty();
        boolean hasVidData = ObdProt.VidPvs != null && !ObdProt.VidPvs.isEmpty();
        log.info("PidPvs: " + (hasPidData ? ObdProt.PidPvs.size() + " items" : "empty/null"));
        log.info("VidPvs: " + (hasVidData ? ObdProt.VidPvs.size() + " items" : "empty/null"));

        if (CommService.elm == null) {
            log.warning("Not connected to vehicle - will display cached data if available");
            updateDisplay();
            return;
        }

        log.info("Connected - current OBD service: " + CommService.elm.getService());

        // If we don't have data and we're connected, start requesting it
        if (!hasPidData || !hasVidData) {
            log.info("Missing emissions data - starting data request from vehicle");
            startEmissionsDataRequest();
        } else {
            log.info("Cached emissions data available - displaying");
            updateDisplay();
        }
    }

    /**
     * Start a fresh emissions data request from the vehicle
     * This switches OBD services to Mode 1 and Mode 9 to retrieve the data
     */
    private void startEmissionsDataRequest() {
        if (CommService.elm == null) {
            log.warning("Cannot request emissions data - not connected to vehicle");
            showSnackbar("Not connected to vehicle");
            return;
        }

        if (dataQueryInProgress) {
            log.info("Data query already in progress, skipping duplicate request");
            return;
        }

        // Save the current service so we can restore it later
        previousService = CommService.elm.getService();
        log.info("Starting emissions data request (previous service: " + previousService + ")");
        dataQueryInProgress = true;

        // Start with Mode 1 to get monitor readiness status
        // The service will automatically request all supported PIDs from Mode 1
        // Don't clear existing data (clearLists=false) to preserve good data
        log.info("Switching to Mode 1 (OBD_SVC_DATA) to request monitor readiness");
        CommService.elm.setService(ObdProt.OBD_SVC_DATA, false);

        // After 3 seconds, switch to Mode 9 to get IUMPR data
        // This gives Mode 1 time to retrieve monitor status data
        updateHandler.postDelayed(() -> {
            if (CommService.elm != null && dataQueryInProgress) {
                log.info("Switching to Mode 9 (OBD_SVC_VEH_INFO) to request IUMPR data");
                CommService.elm.setService(ObdProt.OBD_SVC_VEH_INFO, false);

                // After another 3 seconds, update the display
                updateHandler.postDelayed(() -> {
                    log.info("Emissions data request complete - updating display");
                    updateDisplay();

                    // Mark query as complete but keep the service active for continuous updates
                    // Don't restore the previous service - let the user navigate away naturally
                    dataQueryInProgress = false;
                }, 3000);
            }
        }, 3000);
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

    /**
     * Show dialog to choose export format (PDF, CSV, or JSON)
     */
    private void showSaveReportDialog() {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_save_report);
        dialog.setCancelable(true);

        // PDF option
        View pdfOption = dialog.findViewById(R.id.option_export_pdf);
        pdfOption.setOnClickListener(v -> {
            dialog.dismiss();
            exportAsPDF();
        });

        // CSV option
        View csvOption = dialog.findViewById(R.id.option_export_csv);
        csvOption.setOnClickListener(v -> {
            dialog.dismiss();
            exportEmissionsReport();
        });

        // JSON option
        View jsonOption = dialog.findViewById(R.id.option_export_json);
        jsonOption.setOnClickListener(v -> {
            dialog.dismiss();
            exportAsJSON();
        });

        // Cancel button
        View cancelBtn = dialog.findViewById(R.id.btn_cancel);
        cancelBtn.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    /**
     * Export emissions report as PDF with formatted layout and charts
     */
    private void exportAsPDF() {
        // Ensure latest values are displayed/exported
        updateDisplay();

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String displayTimestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
        String fileName = "emissions_report_" + timestamp + ".pdf";

        PdfDocument document = new PdfDocument();
        OutputStream outputStream = null;

        try {
            // Create PDF page (8.5" x 11" at 72 DPI)
            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(612, 792, 1).create();
            PdfDocument.Page page = document.startPage(pageInfo);
            Canvas canvas = page.getCanvas();

            // Configure paint for text
            Paint titlePaint = new Paint();
            titlePaint.setTextSize(24);
            titlePaint.setColor(Color.BLACK);
            titlePaint.setAntiAlias(true);

            Paint headingPaint = new Paint();
            headingPaint.setTextSize(18);
            headingPaint.setColor(Color.BLACK);
            headingPaint.setAntiAlias(true);

            Paint bodyPaint = new Paint();
            bodyPaint.setTextSize(12);
            bodyPaint.setColor(Color.BLACK);
            bodyPaint.setAntiAlias(true);

            Paint labelPaint = new Paint();
            labelPaint.setTextSize(10);
            labelPaint.setColor(Color.GRAY);
            labelPaint.setAntiAlias(true);

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

            // Draw header
            int y = 60;
            canvas.drawText("OBD-Droid Emissions Report", 50, y, titlePaint);
            y += 30;
            canvas.drawText(displayTimestamp, 50, y, labelPaint);
            y += 40;

            // Draw summary
            canvas.drawText("Emissions Test Readiness Summary", 50, y, headingPaint);
            y += 30;
            canvas.drawText("Available Monitors: " + availableCount, 70, y, bodyPaint);
            y += 20;
            canvas.drawText("Ready Monitors: " + readyCount, 70, y, bodyPaint);
            y += 20;
            canvas.drawText("Monitors Needing Drive Cycle: " + notReadyCount, 70, y, bodyPaint);
            y += 40;

            // Draw readiness indicator
            Paint statusPaint = new Paint();
            statusPaint.setTextSize(16);
            statusPaint.setAntiAlias(true);
            if (readyCount == availableCount && availableCount > 0) {
                statusPaint.setColor(Color.rgb(76, 175, 80)); // Green
                canvas.drawText("✓ READY FOR EMISSIONS TEST", 70, y, statusPaint);
            } else {
                statusPaint.setColor(Color.rgb(255, 152, 0)); // Orange
                canvas.drawText("⚠ NOT READY - Additional Drive Cycle Required", 70, y, statusPaint);
            }
            y += 50;

            // Draw monitor details
            canvas.drawText("Monitor Details", 50, y, headingPaint);
            y += 30;

            for (MonitorData monitor : getOrderedMonitorList()) {
                if (monitor == null) continue;

                // Check if we need a new page
                if (y > 720) {
                    document.finishPage(page);
                    page = document.startPage(pageInfo);
                    canvas = page.getCanvas();
                    y = 60;
                }

                // Monitor name
                canvas.drawText(monitor.name, 70, y, bodyPaint);
                y += 15;

                // Status
                String status = monitor.isAvailable ? (monitor.isReady ? "Ready ✓" : "Not Ready") : "Not Equipped";
                Paint statusTextPaint = new Paint(labelPaint);
                if (monitor.isReady) {
                    statusTextPaint.setColor(Color.rgb(76, 175, 80));
                } else if (monitor.isAvailable) {
                    statusTextPaint.setColor(Color.rgb(255, 152, 0));
                }
                canvas.drawText("Status: " + status, 90, y, statusTextPaint);
                y += 15;

                // IUMPR data
                if (monitor.isAvailable) {
                    String iumprText = String.format(Locale.US, "IUMPR: %d / %d (%s)",
                            monitor.completions, monitor.conditions, monitor.getPercentageDisplay());
                    canvas.drawText(iumprText, 90, y, labelPaint);
                    y += 15;

                    String quality = monitor.getIUMPRQuality();
                    if (quality != null && !quality.isEmpty()) {
                        canvas.drawText("Quality: " + quality, 90, y, labelPaint);
                        y += 15;
                    }
                }

                y += 10; // Spacing between monitors
            }

            document.finishPage(page);

            // Save to file
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/OBDroid");

                Uri uri = getContentResolver().insert(MediaStore.Files.getContentUri("external"), values);
                if (uri == null) {
                    throw new IOException("Failed to create MediaStore entry");
                }

                outputStream = getContentResolver().openOutputStream(uri);
            } else {
                File documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
                File obdroidDir = new File(documentsDir, "OBDroid");
                if (!obdroidDir.exists() && !obdroidDir.mkdirs()) {
                    throw new IOException("Unable to create export directory");
                }
                File pdfFile = new File(obdroidDir, fileName);
                outputStream = new FileOutputStream(pdfFile);
            }

            if (outputStream == null) {
                throw new IOException("Output stream was null");
            }

            document.writeTo(outputStream);
            showSnackbar("PDF report saved: " + fileName);
            log.info("PDF report exported successfully: " + fileName);

        } catch (Exception e) {
            log.warning("Failed to export PDF: " + e.getMessage());
            showSnackbar("Failed to export PDF report");
        } finally {
            document.close();
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    /**
     * Export emissions report as JSON for APIs and developers
     */
    private void exportAsJSON() {
        // Ensure latest values are displayed/exported
        updateDisplay();

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String displayTimestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
        String fileName = "emissions_report_" + timestamp + ".json";

        OutputStreamWriter writer = null;

        try {
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

            // Build JSON structure
            JSONObject root = new JSONObject();
            root.put("report_type", "OBD-Droid Emissions Report");
            root.put("generated_at", displayTimestamp);
            root.put("timestamp", new Date().getTime());

            JSONObject summary = new JSONObject();
            summary.put("available_monitors", availableCount);
            summary.put("ready_monitors", readyCount);
            summary.put("not_ready_monitors", notReadyCount);
            summary.put("is_ready_for_test", (readyCount == availableCount && availableCount > 0));
            summary.put("passes_epa_standards", (readyCount == availableCount && availableCount > 0));
            root.put("summary", summary);

            JSONArray monitorsArray = new JSONArray();
            for (MonitorData monitor : getOrderedMonitorList()) {
                if (monitor == null) continue;

                JSONObject monitorObj = new JSONObject();
                monitorObj.put("name", monitor.name);
                monitorObj.put("available", monitor.isAvailable);
                monitorObj.put("ready", monitor.isReady);
                monitorObj.put("completions", monitor.completions);
                monitorObj.put("conditions", monitor.conditions);
                monitorObj.put("completion_percentage", monitor.getPercentageDisplay());

                String quality = monitor.getIUMPRQuality();
                monitorObj.put("iumpr_quality", quality != null ? quality : "");

                monitorsArray.put(monitorObj);
            }
            root.put("monitors", monitorsArray);

            // Vehicle info (if available)
            VehicleInfoFooter vehicleFooter = findViewById(R.id.vehicle_info_footer);
            if (vehicleFooter != null) {
                JSONObject vehicleInfo = new JSONObject();
                // Add vehicle info if available from VID data
                root.put("vehicle", vehicleInfo);
            }

            // Save to file
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "application/json");
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
                    throw new IOException("Unable to create export directory");
                }
                File jsonFile = new File(obdroidDir, fileName);
                writer = new OutputStreamWriter(new FileOutputStream(jsonFile));
            }

            if (writer == null) {
                throw new IOException("Output stream writer was null");
            }

            writer.write(root.toString(2)); // Pretty print with 2-space indent
            writer.flush();

            showSnackbar("JSON report saved: " + fileName);
            log.info("JSON report exported successfully: " + fileName);

        } catch (Exception e) {
            log.warning("Failed to export JSON: " + e.getMessage());
            showSnackbar("Failed to export JSON report");
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
        // Make a copy of the keys to avoid ConcurrentModificationException
        int foundCount = 0;
        java.util.List<Object> keys = new java.util.ArrayList<>(ObdProt.PidPvs.keySet());
        for (Object key : keys) {
            Object value = ObdProt.PidPvs.get(key);
            if (value instanceof EcuDataPv) {
                EcuDataPv pv = (EcuDataPv) value;
                String description = String.valueOf(pv.get(EcuDataPv.FID_DESCRIPT));
                Object formattedVal = pv.get(EcuDataPv.FID_VALUE);

                if (description == null || formattedVal == null) continue;

                String lowerDescription = description.toLowerCase(Locale.US);
                String monitorKey = null;

                if (lowerDescription.contains("misfire")) {
                    monitorKey = "MISFIRE";
                } else if (lowerDescription.contains("fuel system")) {
                    monitorKey = "FUEL";
                } else if (lowerDescription.contains("component")) {
                    monitorKey = "CCM";
                } else if (lowerDescription.contains("catalyst")) {
                    monitorKey = "CATALYST";
                } else if (lowerDescription.contains("evap")) {
                    monitorKey = "EVAP";
                } else if (lowerDescription.contains("oxygen sensor heater")) {
                    monitorKey = "O2HEATER";
                } else if (lowerDescription.contains("oxygen sensor")) {
                    monitorKey = "O2SENSOR";
                } else if (lowerDescription.contains("egr")) {
                    monitorKey = "EGR";
                } else if (lowerDescription.contains("secondary air") || lowerDescription.contains("air system")) {
                    monitorKey = "AIR";
                }

                if (monitorKey != null) {
                    MonitorData monitor = monitorDataMap.get(monitorKey);
                    if (monitor != null) {
                        applyMonitorStatusFromPid(monitorKey, monitor, formattedVal, description);
                        foundCount++;
                    }
                }
            }
        }
        log.info("updateMonitorReadiness: Found " + foundCount + " monitor status entries");
    }

    private void applyMonitorStatusFromPid(String monitorKey, MonitorData monitor, Object valueObj, String description) {
        boolean isAvailable = false;
        boolean isComplete = false;

        if (valueObj instanceof Number) {
            int raw = ((Number) valueObj).intValue();
            boolean isContinuous = isContinuousMonitorKey(monitorKey);

            if (isContinuous) {
                isAvailable = (raw & 0x01) != 0;
                boolean incompleteBitSet = (raw & 0x10) != 0;
                isComplete = isAvailable && !incompleteBitSet;
            } else {
                // Per OBD-II Mode 1 PID 0x01 standard:
                // Bit=1: Monitor is supported and INCOMPLETE
                // Bit=0: Monitor is NOT SUPPORTED or COMPLETE (ambiguous!)
                // Use IUMPR data to disambiguate when bit=0
                boolean incompleteBitSet = (raw & 0x100) != 0;

                if (incompleteBitSet) {
                    // Bit set = supported but incomplete
                    isAvailable = true;
                    isComplete = false;
                } else {
                    // Bit clear = not supported OR complete
                    // Check if monitor has IUMPR data to know if it's supported
                    if (monitor.conditions > 0) {
                        // Has IUMPR data = supported and complete!
                        isAvailable = true;
                        isComplete = true;
                    } else {
                        // No IUMPR data = not supported
                        isAvailable = false;
                        isComplete = false;
                    }
                }
            }

            log.info(String.format(Locale.US,
                "  Monitor bits [%s] raw=0x%03X -> available=%s, complete=%s (desc=%s)",
                monitorKey,
                raw & 0x1FF,
                isAvailable,
                isComplete,
                description));
        } else {
            String statusStr = String.valueOf(valueObj);
            isAvailable = statusStr.contains("(*) Available") || statusStr.contains("(*) Complete");
            isComplete = statusStr.contains("(*) Complete");
            log.info(String.format(Locale.US,
                "  Monitor text [%s] \"%s\" -> available=%s, complete=%s",
                monitorKey,
                statusStr.replace("\n", " | "),
                isAvailable,
                isComplete));
        }

        monitor.isAvailable = isAvailable;
        monitor.isReady = isComplete && isAvailable;
    }

    private boolean isContinuousMonitorKey(String monitorKey) {
        return "MISFIRE".equals(monitorKey)
            || "FUEL".equals(monitorKey)
            || "CCM".equals(monitorKey);
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
        // Make a copy of the keys to avoid ConcurrentModificationException
        java.util.List<Object> vidKeys = new java.util.ArrayList<>(ObdProt.VidPvs.keySet());
        for (Object key : vidKeys) {
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
                // IMPORTANT: Match OBD Conditions and Ignition Counter EXACTLY to avoid false matches
                // IMPORTANT: Vehicle may return duplicate entries with 0 values - only use non-zero values

                if (description.equals("OBD Monitoring Conditions Encountered Counts") ||
                    description.equals("OBD Monitoring Conditions")) {
                    // Only update if new value is non-zero, or if we don't have a value yet
                    if (intValue > 0 || obdConditions == 0) {
                        if (intValue > obdConditions) {
                            obdConditions = intValue;
                            foundIUMPRCount++;
                            log.info("  Found: OBD Conditions = " + intValue);
                        } else if (intValue == 0 && obdConditions > 0) {
                            log.info("  Ignoring duplicate OBD Conditions with value 0 (keeping " + obdConditions + ")");
                        }
                    }
                } else if (description.equals("Ignition Counter") ||
                          description.equals("Ignition Cycles")) {
                    // Only update if new value is non-zero, or if we don't have a value yet
                    if (intValue > 0 || ignitionCounter == 0) {
                        if (intValue > ignitionCounter) {
                            ignitionCounter = intValue;
                            foundIUMPRCount++;
                            log.info("  Found: Ignition Counter = " + intValue);
                        } else if (intValue == 0 && ignitionCounter > 0) {
                            log.info("  Ignoring duplicate Ignition Counter with value 0 (keeping " + ignitionCounter + ")");
                        }
                    }
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
                // Handle numeric types directly to avoid decimal point removal bug
                if (dataValue instanceof Number) {
                    return ((Number) dataValue).intValue();
                }
                // Handle string values (parse as float first to handle decimals correctly)
                String valStr = dataValue.toString().trim();
                if (!valStr.isEmpty()) {
                    // Parse as float first, then convert to int (avoids "5136.0" → "51360" bug)
                    return (int) Float.parseFloat(valStr);
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
