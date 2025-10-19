package com.obddroid.ui.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.obddroid.R;
import com.obddroid.ui.components.VehicleInfoFooter;
import com.obddroid.vehicle.EcuScan;
import com.obddroid.vehicle.EcuScanHistoryManager;
import com.obddroid.vehicle.VehicleManager;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * Activity for selecting a baseline scan to compare against current ECU data.
 * Shows a list of historical scans with option to import from CSV.
 */
public class BaselineScanSelectionActivity extends AppCompatActivity {

    private static final Logger log = Logger.getLogger(BaselineScanSelectionActivity.class.getName());
    public static final String EXTRA_SELECTED_SCAN = "selected_scan";
    public static final String EXTRA_IMPORT_CSV_REQUESTED = "import_csv_requested";
    private static final int REQUEST_CODE_IMPORT_CSV = 1001;

    private LinearLayout scanListContainer;
    private TextView showMoreButton;
    private Button importCsvButton;
    private VehicleInfoFooter vehicleInfoFooter;
    private EcuScanHistoryManager scanHistoryManager;
    private List<EcuScan> scans;

    private static final int INITIAL_LIMIT = 5;
    private boolean isExpanded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_baseline_scan_selection);

        // Set up action bar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Select Baseline Scan");
        }

        // Initialize views
        scanListContainer = findViewById(R.id.scan_list_container);
        showMoreButton = findViewById(R.id.show_more_button);
        importCsvButton = findViewById(R.id.btn_import_csv);
        vehicleInfoFooter = findViewById(R.id.vehicle_footer);

        // Set up vehicle info footer
        View overlay = findViewById(R.id.footer_overlay);
        if (vehicleInfoFooter != null && overlay != null) {
            vehicleInfoFooter.setOverlayView(overlay);
            log.info("Footer overlay wired up successfully");
        }

        // Initialize scan history manager
        scanHistoryManager = new EcuScanHistoryManager(this);

        // Load scans for current VIN
        String vin = VehicleManager.getInstance(this).getCurrentVIN();
        if (vin != null && !vin.isEmpty()) {
            scans = scanHistoryManager.getScansForVin(vin);
            populateScans();
        } else {
            log.warning("No VIN available");
            finish();
        }

        // Show more button click listener
        showMoreButton.setOnClickListener(v -> {
            isExpanded = !isExpanded;
            populateScans();
        });

        // Import CSV button
        importCsvButton.setOnClickListener(v -> {
            Intent resultIntent = new Intent();
            resultIntent.putExtra(EXTRA_IMPORT_CSV_REQUESTED, true);
            setResult(RESULT_OK, resultIntent);
            finish();
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Populate the scan list based on expansion state
     */
    private void populateScans() {
        scanListContainer.removeAllViews();

        if (scans == null || scans.isEmpty()) {
            TextView emptyText = new TextView(this);
            emptyText.setText("No saved scans available");
            emptyText.setTextSize(16);
            emptyText.setPadding(32, 32, 32, 32);
            emptyText.setGravity(android.view.Gravity.CENTER);
            emptyText.setTextColor(getResources().getColor(android.R.color.darker_gray));
            scanListContainer.addView(emptyText);
            return;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US);
        int limit = isExpanded ? scans.size() : Math.min(INITIAL_LIMIT, scans.size());

        for (int i = 0; i < limit; i++) {
            EcuScan scan = scans.get(i);
            TextView scanItem = new TextView(this);
            scanItem.setText(sdf.format(scan.getDate()) + " (" + scan.getEcus().size() + " ECUs)");
            scanItem.setTextSize(16);
            scanItem.setPadding(32, 32, 32, 32);
            scanItem.setBackground(getDrawable(android.R.drawable.list_selector_background));
            scanItem.setClickable(true);
            scanItem.setFocusable(true);

            final int index = i;
            scanItem.setOnClickListener(v -> {
                // Return selected scan
                Intent resultIntent = new Intent();
                resultIntent.putExtra(EXTRA_SELECTED_SCAN, scans.get(index));
                setResult(RESULT_OK, resultIntent);
                finish();
            });

            scanListContainer.addView(scanItem);
        }

        // Show/hide "Show more" button
        if (scans.size() > INITIAL_LIMIT) {
            showMoreButton.setVisibility(View.VISIBLE);
            showMoreButton.setText(isExpanded ? "▲ Show less" : "▼ Show more scans");
        } else {
            showMoreButton.setVisibility(View.GONE);
        }
    }
}
