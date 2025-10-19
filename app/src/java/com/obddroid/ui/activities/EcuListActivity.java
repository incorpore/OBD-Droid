package com.obddroid.ui.activities;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;
import com.obddroid.R;
import com.obddroid.core.obd.ElmProt;
import com.obddroid.core.obd.ObdProt;
import com.obddroid.services.CommService;
import com.obddroid.services.EcuDiscoveryService;
import com.obddroid.ui.adapters.EcuAdapter;
import com.obddroid.ui.components.VehicleInfoFooter;
import com.obddroid.vehicle.EcuCacheManager;
import com.obddroid.vehicle.EcuInfo;
import com.obddroid.vehicle.EcuManager;
import com.obddroid.vehicle.EcuScan;
import com.obddroid.vehicle.EcuScanHistoryManager;
import com.obddroid.vehicle.VehicleManager;

import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Activity for displaying the list of discovered ECUs in the vehicle.
 * Shows ECU addresses, names, calibration IDs, and other diagnostic information.
 */
public class EcuListActivity extends AppCompatActivity implements EcuManager.EcuManagerListener {

    private static final Logger log = Logger.getLogger(EcuListActivity.class.getName());
    private static final int REQUEST_CODE_IMPORT_CSV = 1001;

    private RecyclerView recyclerView;
    private EcuAdapter adapter;
    private View emptyView;
    private ProgressBar progressBar;
    private Button scanButton;
    private View snackbarAnchor;  // Anchor view for snackbars
    private EcuManager ecuManager;
    private EcuCacheManager cacheManager;
    private EcuScanHistoryManager scanHistoryManager;
    private VehicleInfoFooter vehicleInfoFooter;
    private boolean allowAutoUpdates = false;  // Don't show auto-discovered ECUs until scan is done

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ecu_list);

        // Set up action bar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.ecu_list_title);
        }

        // Initialize views
        recyclerView = findViewById(R.id.ecu_recycler_view);
        emptyView = findViewById(R.id.empty_view);
        progressBar = findViewById(R.id.progress_bar);
        scanButton = findViewById(R.id.scan_button);
        vehicleInfoFooter = findViewById(R.id.vehicle_footer);
        snackbarAnchor = findViewById(R.id.content_frame);  // Use content frame for snackbar creation

        // Set up RecyclerView
        adapter = new EcuAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        // Set up ECU Manager
        ecuManager = EcuManager.getInstance();

        // Clear any old ECU data to start fresh
        // This prevents showing stale Mode 9 data from the app's automatic discovery
        ecuManager.clear();

        ecuManager.addListener(this);

        // Set up scan button
        scanButton.setOnClickListener(v -> scanForEcus());

        // Set up vehicle info footer (already initialized above)
        View overlay = findViewById(R.id.footer_overlay);

        if (vehicleInfoFooter != null && overlay != null) {
            vehicleInfoFooter.setOverlayView(overlay);
            log.info("Footer overlay wired up successfully");
        }

        // Initialize cache manager
        cacheManager = new EcuCacheManager(this);

        // Initialize scan history manager
        scanHistoryManager = new EcuScanHistoryManager(this);

        // Start with empty view - wait for user to tap scan or import
        updateEmptyView();
    }

    @Override
    protected void onResume() {
        super.onResume();
        ecuManager.startListening();
        // DON'T load data here - only load after scan
        updateEmptyView();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Don't stop listening - let it continue in background
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ecuManager.removeListener(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.ecu_list, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == android.R.id.home) {
            finish();
            return true;
        } else if (id == R.id.action_rescan_ecus) {
            scanForEcus();
            return true;
        } else if (id == R.id.action_compare_scans) {
            openCompareScans();
            return true;
        } else if (id == R.id.action_export_csv) {
            exportToCSV();
            return true;
        } else if (id == R.id.action_import_csv) {
            importFromCSV();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Load existing ECU data from manager
     */
    private void loadEcuData() {
        List<EcuInfo> ecuList = ecuManager.getEcuList();
        log.info("Loading ECU data: " + ecuList.size() + " ECUs found");

        adapter.setEcuList(ecuList);
        updateEmptyView();
    }

    /**
     * Scan for ECUs using the isolated discovery service.
     * This uses Mode 9 commands with headers enabled to properly identify ECU addresses.
     * NOTE: This is SEPARATE from the standard Mode 9 flow used elsewhere in the app.
     */
    private void scanForEcus() {
        log.info("Scan for ECUs requested (using isolated discovery service)");

        // Check if connected to vehicle
        if (CommService.elm == null) {
            showSnackbar("Not connected to vehicle", Snackbar.LENGTH_SHORT);
            return;
        }

        // Check if we're actually connected (not just initialized)
        ElmProt.STAT status = CommService.elm.getStatus();
        if (status != ElmProt.STAT.CONNECTED && status != ElmProt.STAT.ECU_DETECTED) {
            showSnackbar("Please connect to vehicle first", Snackbar.LENGTH_SHORT);
            return;
        }

        // Show progress
        progressBar.setVisibility(View.VISIBLE);
        scanButton.setEnabled(false);

        // Clear existing data
        ecuManager.clear();
        adapter.setEcuList(null);

        showSnackbar("Scanning for ECUs with detailed discovery...", Snackbar.LENGTH_SHORT);

        // Use the NEW isolated discovery service
        log.info("Starting isolated ECU discovery (with headers enabled)");

        // Run discovery on background thread
        new Thread(() -> {
            try {
                // Create discovery service (no params needed - uses CommService.elm)
                EcuDiscoveryService discoveryService = new EcuDiscoveryService();

                // Start discovery (returns CompletableFuture)
                Map<Integer, EcuDiscoveryService.EcuDiscoveryInfo> discoveryData =
                    discoveryService.discoverEcus().get(15, java.util.concurrent.TimeUnit.SECONDS);

                log.info("Discovery complete! Found " + discoveryData.size() + " ECUs");

                // Update ECU manager with discovery data
                ecuManager.updateFromDiscoveryData(discoveryData);

                // Update UI on main thread
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    scanButton.setEnabled(true);

                    // Enable auto-updates now that we've done our first scan
                    allowAutoUpdates = true;

                    List<EcuInfo> ecuList = ecuManager.getEcuList();
                    loadEcuData();  // Refresh display

                    // Save scan results to cache
                    String vin = VehicleManager.getInstance().getCurrentVIN();
                    if (vin != null && !vin.isEmpty() && !ecuList.isEmpty()) {
                        cacheManager.saveScanResults(vin, ecuList);
                        log.info("Saved " + ecuList.size() + " ECUs to cache for VIN");
                    }

                    if (ecuList.isEmpty()) {
                        showSnackbar("No ECUs found. Vehicle may not support Mode 9.",
                            Snackbar.LENGTH_LONG);
                    } else {
                        showSnackbar("Found " + ecuList.size() + " ECU(s) with detailed info",
                            Snackbar.LENGTH_SHORT);

                        // Auto-save scan to history
                        saveScanToHistory(ecuList);
                    }
                });

            } catch (java.util.concurrent.TimeoutException e) {
                log.warning("ECU discovery timed out after 15 seconds");
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    scanButton.setEnabled(true);
                    showSnackbar("Discovery timed out - try again or check connection",
                        Snackbar.LENGTH_LONG);
                });
            } catch (Exception e) {
                log.warning("Error during ECU discovery: " + e.getMessage());
                e.printStackTrace();

                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    scanButton.setEnabled(true);
                    showSnackbar("Error scanning for ECUs: " + e.getMessage(),
                        Snackbar.LENGTH_LONG);
                });
            }
        }).start();
    }

    /**
     * Load cached ECU scan results for the current VIN
     */
    private void loadCachedData() {
        String vin = VehicleManager.getInstance().getCurrentVIN();
        if (vin == null || vin.isEmpty()) {
            log.info("No VIN available - skipping cache load");
            return;
        }

        List<EcuInfo> cachedEcus = cacheManager.loadScanResults(vin);
        if (cachedEcus != null && !cachedEcus.isEmpty()) {
            log.info("Loading " + cachedEcus.size() + " cached ECUs for VIN");

            // Populate adapter with cached data directly
            adapter.setEcuList(cachedEcus);
            updateEmptyView();

            // Enable auto-updates since we have valid data
            allowAutoUpdates = true;

            showSnackbar("Loaded " + cachedEcus.size() + " ECU(s) from cache",
                Snackbar.LENGTH_SHORT);
        }
    }

    /**
     * Export ECU data to CSV file
     */
    private void exportToCSV() {
        // Get ECU data from adapter (which shows cached or scanned data)
        List<EcuInfo> ecuList = adapter.getEcuList();
        if (ecuList.isEmpty()) {
            showSnackbar("No ECU data to export", Snackbar.LENGTH_SHORT);
            return;
        }

        try {
            // Get VIN and extract last 6 digits
            String vin = VehicleManager.getInstance(this).getCurrentVIN();
            String vinSuffix = "UNKNOWN";
            if (vin != null && vin.length() >= 6) {
                vinSuffix = vin.substring(vin.length() - 6);
            }

            // Create filename with VIN suffix and timestamp
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
            String timestamp = sdf.format(new Date());
            String filename = vinSuffix + "-ecu_modules-" + timestamp + ".csv";

            // Use MediaStore for Android 10+ to save to Documents/OBDroid
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ - Use MediaStore API
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "text/csv");
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/OBDroid");

                Uri uri = getContentResolver().insert(MediaStore.Files.getContentUri("external"), values);
                if (uri != null) {
                    OutputStream outputStream = getContentResolver().openOutputStream(uri);
                    OutputStreamWriter writer = new OutputStreamWriter(outputStream);

                    // Write header
                    writer.append("ECU Address,Name,Type,Calibration ID,CVN\n");

                    // Write data
                    for (EcuInfo ecu : ecuList) {
                        writer.append(String.format("0x%X", ecu.getAddress())).append(",");
                        writer.append(csvEscape(ecu.getName())).append(",");
                        writer.append(csvEscape(ecu.getEcuType())).append(",");
                        writer.append(csvEscape(ecu.getCalibrationId())).append(",");
                        writer.append(csvEscape(ecu.getCalibrationVerification())).append("\n");
                    }

                    writer.flush();
                    writer.close();
                    outputStream.close();

                    log.info("Exported " + ecuList.size() + " ECUs via MediaStore to: Documents/OBDroid/" + filename);
                    showSnackbar("Exported " + ecuList.size() + " ECU(s) to:\nDocuments/OBDroid/" + filename,
                        Snackbar.LENGTH_LONG);
                } else {
                    throw new IOException("Failed to create file in Documents");
                }
            } else {
                // Android 9 and below - Use legacy file system
                File documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
                File obdroidDir = new File(documentsDir, "OBDroid");
                if (!obdroidDir.exists()) {
                    obdroidDir.mkdirs();
                }

                File csvFile = new File(obdroidDir, filename);
                FileWriter writer = new FileWriter(csvFile);

                // Write header
                writer.append("ECU Address,Name,Type,Calibration ID,CVN\n");

                // Write data
                for (EcuInfo ecu : ecuList) {
                    writer.append(String.format("0x%X", ecu.getAddress())).append(",");
                    writer.append(csvEscape(ecu.getName())).append(",");
                    writer.append(csvEscape(ecu.getEcuType())).append(",");
                    writer.append(csvEscape(ecu.getCalibrationId())).append(",");
                    writer.append(csvEscape(ecu.getCalibrationVerification())).append("\n");
                }

                writer.flush();
                writer.close();

                log.info("Exported " + ecuList.size() + " ECUs to: " + csvFile.getAbsolutePath());
                showSnackbar("Exported " + ecuList.size() + " ECU(s) to:\nDocuments/OBDroid/" + csvFile.getName(),
                    Snackbar.LENGTH_LONG);
            }

        } catch (IOException e) {
            log.severe("Error exporting CSV: " + e.getMessage());
            showSnackbar("Export failed: " + e.getMessage(),
                Snackbar.LENGTH_LONG);
        }
    }

    /**
     * Import ECU data from CSV file
     */
    private void importFromCSV() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/*");
        startActivityForResult(intent, REQUEST_CODE_IMPORT_CSV);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_IMPORT_CSV && resultCode == RESULT_OK) {
            if (data != null && data.getData() != null) {
                importCSVFile(data.getData());
            }
        }
    }

    /**
     * Import CSV file and populate ECU list
     */
    private void importCSVFile(Uri uri) {
        try {
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(getContentResolver().openInputStream(uri)));

            List<EcuInfo> importedEcus = new java.util.ArrayList<>();
            String line;
            boolean isFirstLine = true;

            while ((line = reader.readLine()) != null) {
                if (isFirstLine) {
                    isFirstLine = false;  // Skip header
                    continue;
                }

                String[] parts = csvParse(line);
                if (parts.length >= 5) {
                    try {
                        // Parse: ECU Address, Name, Type (ignored - auto-derived), Calibration ID, CVN
                        String addressStr = parts[0].trim();
                        int address = Integer.decode(addressStr);  // Handles 0x prefix
                        String name = parts[1].trim();
                        // Skip parts[2] - type is auto-derived from name/address
                        String calId = parts[3].trim();
                        String cvn = parts[4].trim();

                        EcuInfo ecu = new EcuInfo(address);
                        if (!name.isEmpty()) ecu.setName(name);
                        if (!calId.isEmpty()) ecu.setCalibrationId(calId);
                        if (!cvn.isEmpty()) ecu.setCalibrationVerification(cvn);

                        importedEcus.add(ecu);
                    } catch (NumberFormatException e) {
                        log.warning("Skipping invalid line: " + line);
                    }
                }
            }

            reader.close();

            // Update adapter with imported data
            adapter.setEcuList(importedEcus);
            updateEmptyView();

            showSnackbar("Imported " + importedEcus.size() + " ECU(s)",
                Snackbar.LENGTH_LONG);

            log.info("Successfully imported " + importedEcus.size() + " ECUs");

        } catch (IOException e) {
            log.severe("Error importing CSV: " + e.getMessage());
            showSnackbar("Import failed: " + e.getMessage(),
                Snackbar.LENGTH_LONG);
        }
    }

    /**
     * Parse CSV line handling quoted values
     */
    private String[] csvParse(String line) {
        java.util.ArrayList<String> result = new java.util.ArrayList<>();
        boolean inQuotes = false;
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        result.add(current.toString());

        return result.toArray(new String[0]);
    }

    /**
     * Escape CSV values (handle commas and quotes)
     */
    private String csvEscape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /**
     * Show a snackbar anchored above the vehicle footer
     */
    private void showSnackbar(String message, int duration) {
        Snackbar snackbar = Snackbar.make(snackbarAnchor, message, duration);
        snackbar.setAnchorView(vehicleInfoFooter);  // Position above the footer
        snackbar.show();
    }

    /**
     * Update empty view visibility based on ECU count
     */
    private void updateEmptyView() {
        if (adapter.getItemCount() == 0) {
            recyclerView.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
        }
    }

    @Override
    public void onEcuDiscovered(EcuInfo ecu) {
        // Ignore automatic ECU discoveries until user explicitly scans
        if (!allowAutoUpdates) {
            log.fine("Ignoring auto-discovered ECU (waiting for manual scan): " + ecu);
            return;
        }

        runOnUiThread(() -> {
            log.info("ECU discovered: " + ecu);
            adapter.addEcu(ecu);
            updateEmptyView();
        });
    }

    @Override
    public void onEcuUpdated(EcuInfo ecu) {
        // Ignore automatic ECU updates until user explicitly scans
        if (!allowAutoUpdates) {
            log.fine("Ignoring auto-updated ECU (waiting for manual scan): " + ecu);
            return;
        }

        runOnUiThread(() -> {
            log.info("ECU updated: " + ecu);
            adapter.updateEcu(ecu);
        });
    }

    /**
     * Save scan to history for later comparison
     */
    private void saveScanToHistory(List<EcuInfo> ecuList) {
        try {
            String vin = VehicleManager.getInstance(this).getCurrentVIN();
            EcuScan scan = new EcuScan(vin, ecuList);
            boolean saved = scanHistoryManager.saveScan(scan);
            if (saved) {
                log.info("Saved ECU scan to history: " + ecuList.size() + " ECUs");
            } else {
                log.warning("Failed to save ECU scan to history");
            }
        } catch (Exception e) {
            log.severe("Error saving scan to history: " + e.getMessage());
        }
    }

    /**
     * Open scan comparison dialog
     */
    private void openCompareScans() {
        // Get scan history for current VIN
        String vin = VehicleManager.getInstance(this).getCurrentVIN();
        if (vin == null || vin.isEmpty()) {
            showSnackbar("No VIN available", Snackbar.LENGTH_SHORT);
            return;
        }

        List<EcuScan> scans = scanHistoryManager.getScansForVin(vin);
        if (scans.size() < 2) {
            showSnackbar("Need at least 2 scans to compare. Perform another scan first.",
                Snackbar.LENGTH_LONG);
            return;
        }

        // Show scan comparison dialog
        showScanComparisonDialog(scans);
    }

    /**
     * Show dialog for selecting scans to compare
     */
    private void showScanComparisonDialog(List<EcuScan> scans) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Compare ECU Scans");
        builder.setMessage("Select two scans to compare:\n\nBaseline (older scan) vs Current (newer scan)");

        // For now, auto-select: oldest vs newest
        // Future: let user pick which two
        EcuScan baseline = scans.get(scans.size() - 1);  // Oldest
        EcuScan current = scans.get(0);  // Newest

        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US);
        String message = "Baseline: " + sdf.format(baseline.getDate()) +
                        "\nCurrent: " + sdf.format(current.getDate()) +
                        "\n\nCompare these scans?";

        builder.setMessage(message);
        builder.setPositiveButton("Compare", (dialog, which) -> {
            showComparisonResult(baseline, current);
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    /**
     * Show comparison results in a dialog
     */
    private void showComparisonResult(EcuScan baseline, EcuScan current) {
        StringBuilder result = new StringBuilder();
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US);

        result.append("=== ECU Comparison ===\n\n");
        result.append("Baseline: ").append(sdf.format(baseline.getDate())).append("\n");
        result.append("Current:  ").append(sdf.format(current.getDate())).append("\n\n");

        // Compare ECUs
        int changedCount = 0;
        int unchangedCount = 0;
        List<EcuInfo> baselineEcus = baseline.getEcus();
        List<EcuInfo> currentEcus = current.getEcus();

        for (EcuInfo currentEcu : currentEcus) {
            EcuInfo baselineEcu = findEcuByAddress(baselineEcus, currentEcu.getAddress());

            result.append("---\n");
            result.append(currentEcu.getName()).append(" (").append(String.format("0x%X", currentEcu.getAddress())).append(")\n");

            if (baselineEcu == null) {
                result.append("⚠ NEW ECU (not in baseline)\n");
                changedCount++;
            } else {
                // Compare Cal ID
                String baseCalId = baselineEcu.getCalibrationId();
                String currCalId = currentEcu.getCalibrationId();
                boolean calIdChanged = !equals(baseCalId, currCalId);

                // Compare CVN
                String baseCvn = baselineEcu.getCalibrationVerification();
                String currCvn = currentEcu.getCalibrationVerification();
                boolean cvnChanged = !equals(baseCvn, currCvn);

                if (calIdChanged || cvnChanged) {
                    changedCount++;
                    result.append("⚠ CHANGED\n");

                    if (calIdChanged) {
                        result.append("  Cal ID: ").append(baseCalId).append("\n");
                        result.append("       → ").append(currCalId).append("\n");
                    }

                    if (cvnChanged) {
                        result.append("  CVN: ").append(baseCvn).append("\n");
                        result.append("    → ").append(currCvn).append("\n");
                    }
                } else {
                    unchangedCount++;
                    result.append("✓ No changes\n");
                }
            }
        }

        // Check for removed ECUs
        for (EcuInfo baselineEcu : baselineEcus) {
            if (findEcuByAddress(currentEcus, baselineEcu.getAddress()) == null) {
                result.append("---\n");
                result.append(baselineEcu.getName()).append(" (").append(String.format("0x%X", baselineEcu.getAddress())).append(")\n");
                result.append("⚠ REMOVED (not in current scan)\n");
                changedCount++;
            }
        }

        result.append("\n=== Summary ===\n");
        result.append("Changed: ").append(changedCount).append("\n");
        result.append("Unchanged: ").append(unchangedCount).append("\n");

        // Show result dialog
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("ECU Comparison Results");
        builder.setMessage(result.toString());
        builder.setPositiveButton("OK", null);
        builder.setNeutralButton("Export", (dialog, which) -> {
            // TODO: Export comparison report
            showSnackbar("Export comparison - Coming soon", Snackbar.LENGTH_SHORT);
        });
        builder.show();
    }

    private EcuInfo findEcuByAddress(List<EcuInfo> ecus, int address) {
        for (EcuInfo ecu : ecus) {
            if (ecu.getAddress() == address) {
                return ecu;
            }
        }
        return null;
    }

    private boolean equals(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }
}
