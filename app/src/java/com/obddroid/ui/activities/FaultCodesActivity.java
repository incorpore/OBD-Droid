package com.obddroid.ui.activities;

import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;
import com.obddroid.R;
import com.obddroid.services.FaultCodeService;
import com.obddroid.ui.adapters.FaultCodeListAdapter;
import com.obddroid.ui.components.VehicleInfoFooter;

import java.util.List;
import java.util.logging.Logger;

/**
 * Activity for displaying fault codes (DTCs) from the vehicle.
 * Shows current and pending fault codes with ability to scan and clear codes.
 *
 * @author Wal33D <aquataze@yahoo.com>
 */
public class FaultCodesActivity extends AppCompatActivity {

    private static final Logger log = Logger.getLogger(FaultCodesActivity.class.getName());

    // UI Components
    private CardView milStatusCard;
    private ImageView milStatusIcon;
    private TextView milStatusText;
    private TextView milStatusSubtitle;
    private LinearLayout clearCodesButton;
    private RecyclerView faultCodesList;
    private ProgressBar progressBar;
    private Button scanButton;
    private LinearLayout emptyView;
    private VehicleInfoFooter vehicleInfoFooter;

    // Service and Adapter
    private FaultCodeService faultCodeService;
    private FaultCodeListAdapter adapter;

    // State
    private List<FaultCodeService.FaultCodeInfo> currentCodes;
    private boolean isScanning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fault_codes);

        // Set navigation bar color to match footer
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setNavigationBarColor(0xFF2C2C2C);  // Match footer
        }

        // Set up action bar with back button
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Fault Codes");
        }

        // Initialize views
        initializeViews();

        // Initialize service
        faultCodeService = new FaultCodeService();

        // Set up click listeners
        setupClickListeners();

        // Initialize vehicle footer
        vehicleInfoFooter = findViewById(R.id.vehicle_footer);
        if (vehicleInfoFooter != null) {
            vehicleInfoFooter.setOverlayView(findViewById(R.id.footer_overlay));
        }

        // Show empty state initially
        showEmptyState();
    }

    private void initializeViews() {
        milStatusCard = findViewById(R.id.mil_status_card);
        milStatusIcon = findViewById(R.id.mil_status_icon);
        milStatusText = findViewById(R.id.mil_status_text);
        milStatusSubtitle = findViewById(R.id.mil_status_subtitle);
        clearCodesButton = findViewById(R.id.clear_codes_button);
        faultCodesList = findViewById(R.id.fault_codes_list);
        progressBar = findViewById(R.id.progress_bar);
        scanButton = findViewById(R.id.scan_button);
        emptyView = findViewById(R.id.empty_view);

        // Setup RecyclerView with adapter
        faultCodesList.setLayoutManager(new LinearLayoutManager(this));
        faultCodesList.setNestedScrollingEnabled(false);

        adapter = new FaultCodeListAdapter();
        faultCodesList.setAdapter(adapter);

        // Set click listener for fault code items
        adapter.setOnCodeClickListener(code -> {
            // TODO: Show fault code options dialog (freeze frame, search online, etc.)
            Snackbar.make(
                findViewById(android.R.id.content),
                "Clicked: " + code.code,
                Snackbar.LENGTH_SHORT
            ).show();
        });
    }

    private void setupClickListeners() {
        // Scan button click
        if (scanButton != null) {
            scanButton.setOnClickListener(v -> startScan());
        }

        // Clear codes button click
        if (clearCodesButton != null) {
            clearCodesButton.setOnClickListener(v -> clearCodes());
        }
    }

    /**
     * Start scanning for fault codes
     */
    private void startScan() {
        if (isScanning) {
            log.info("Scan already in progress");
            return;
        }

        log.info("Starting fault code scan");
        isScanning = true;

        // Show progress, hide empty state
        showProgress();

        // Start scan using FaultCodeService
        faultCodeService.scanFaultCodes()
            .thenAccept(codes -> {
                runOnUiThread(() -> {
                    handleScanComplete(codes);
                });
            })
            .exceptionally(error -> {
                runOnUiThread(() -> {
                    handleScanError(error);
                });
                return null;
            });
    }

    /**
     * Handle successful scan completion
     */
    private void handleScanComplete(List<FaultCodeService.FaultCodeInfo> codes) {
        log.info("Scan complete - found " + codes.size() + " fault codes");
        isScanning = false;
        currentCodes = codes;

        hideProgress();

        if (codes.isEmpty()) {
            // No codes found - show success state
            showNoCodesState();
        } else {
            // Codes found - show them
            showFaultCodes(codes);
        }
    }

    /**
     * Handle scan error
     */
    private void handleScanError(Throwable error) {
        log.severe("Fault code scan failed: " + error.getMessage());
        isScanning = false;

        hideProgress();
        showEmptyState();

        Snackbar.make(
            findViewById(android.R.id.content),
            "Scan failed: " + error.getMessage(),
            Snackbar.LENGTH_LONG
        ).show();
    }

    /**
     * Clear all fault codes
     */
    private void clearCodes() {
        log.info("Clearing fault codes");

        faultCodeService.clearFaultCodes()
            .thenAccept(success -> {
                runOnUiThread(() -> {
                    if (success) {
                        Snackbar.make(
                            findViewById(android.R.id.content),
                            "Fault codes cleared successfully",
                            Snackbar.LENGTH_SHORT
                        ).show();

                        // Re-scan to confirm
                        startScan();
                    }
                });
            })
            .exceptionally(error -> {
                runOnUiThread(() -> {
                    Snackbar.make(
                        findViewById(android.R.id.content),
                        "Failed to clear codes: " + error.getMessage(),
                        Snackbar.LENGTH_LONG
                    ).show();
                });
                return null;
            });
    }

    /**
     * Show fault codes in list
     */
    private void showFaultCodes(List<FaultCodeService.FaultCodeInfo> codes) {
        // Hide empty state
        if (emptyView != null) {
            emptyView.setVisibility(View.GONE);
        }

        // Show MIL status card (red - codes present)
        if (milStatusCard != null) {
            milStatusCard.setVisibility(View.VISIBLE);
            milStatusCard.setCardBackgroundColor(0xFFEF5350);  // Red for errors
        }

        if (milStatusIcon != null) {
            milStatusIcon.setColorFilter(0xFFFFFFFF);  // White icon
        }

        if (milStatusText != null) {
            milStatusText.setText(codes.size() + " Fault Code" + (codes.size() > 1 ? "s" : "") + " Found");
        }

        if (milStatusSubtitle != null) {
            long pending = codes.stream().filter(c -> c.isPending).count();
            long confirmed = codes.size() - pending;
            milStatusSubtitle.setText(confirmed + " confirmed • " + pending + " pending");
        }

        // Show clear codes button
        if (clearCodesButton != null) {
            clearCodesButton.setVisibility(View.VISIBLE);
        }

        // Update RecyclerView with fault codes
        if (faultCodesList != null && adapter != null) {
            adapter.setFaultCodes(codes);
            faultCodesList.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Show "no codes found" success state
     */
    private void showNoCodesState() {
        // Hide empty state
        if (emptyView != null) {
            emptyView.setVisibility(View.GONE);
        }

        // Show MIL status card (green - no codes)
        if (milStatusCard != null) {
            milStatusCard.setVisibility(View.VISIBLE);
            milStatusCard.setCardBackgroundColor(0xFF4CAF50);  // Green
        }

        if (milStatusIcon != null) {
            milStatusIcon.setColorFilter(0xFFFFFFFF);  // White icon
        }

        if (milStatusText != null) {
            milStatusText.setText("No Fault Codes");
        }

        if (milStatusSubtitle != null) {
            milStatusSubtitle.setText("Engine running normally");
        }

        // Hide clear codes button
        if (clearCodesButton != null) {
            clearCodesButton.setVisibility(View.GONE);
        }

        // Hide fault codes list
        if (faultCodesList != null) {
            faultCodesList.setVisibility(View.GONE);
        }
    }

    /**
     * Show empty state (before scan)
     */
    private void showEmptyState() {
        if (emptyView != null) {
            emptyView.setVisibility(View.VISIBLE);
        }

        if (milStatusCard != null) {
            milStatusCard.setVisibility(View.GONE);
        }

        if (faultCodesList != null) {
            faultCodesList.setVisibility(View.GONE);
        }

        if (clearCodesButton != null) {
            clearCodesButton.setVisibility(View.GONE);
        }
    }

    /**
     * Show progress indicator
     */
    private void showProgress() {
        if (progressBar != null) {
            progressBar.setVisibility(View.VISIBLE);
        }

        if (emptyView != null) {
            emptyView.setVisibility(View.GONE);
        }

        if (scanButton != null) {
            scanButton.setEnabled(false);
        }
    }

    /**
     * Hide progress indicator
     */
    private void hideProgress() {
        if (progressBar != null) {
            progressBar.setVisibility(View.GONE);
        }

        if (scanButton != null) {
            scanButton.setEnabled(true);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Cleanup if needed
    }
}
