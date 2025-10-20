package com.obddroid.ui.activities;

import android.app.AlertDialog;
import android.app.SearchManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;
import com.obddroid.R;
import com.obddroid.services.FaultCodeService;
import com.obddroid.ui.adapters.FaultCodeListAdapter;
import com.obddroid.ui.components.VehicleInfoFooter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;

/**
 * Activity for displaying fault codes (DTCs) from the vehicle.
 * Shows current and pending fault codes with ability to scan and clear codes.
 */
public class FaultCodesActivity extends AppCompatActivity {

    private static final Logger log = Logger.getLogger(FaultCodesActivity.class.getSimpleName());

    // UI components
    private CardView milStatusCard;
    private ImageView milStatusIcon;
    private TextView milStatusText;
    private TextView milStatusSubtitle;
    private RecyclerView faultCodesList;
    private ProgressBar progressBar;
    private Button scanButton;
    private LinearLayout emptyView;
    private VehicleInfoFooter vehicleInfoFooter;

    // Service and adapter
    private FaultCodeService faultCodeService;
    private FaultCodeListAdapter adapter;

    // State
    private List<FaultCodeService.FaultCodeInfo> currentCodes = Collections.emptyList();
    private boolean isScanning;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fault_codes);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setNavigationBarColor(ContextCompat.getColor(this, R.color.background_secondary));
        }

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.fault_codes_title);
        }

        faultCodeService = new FaultCodeService();

        initializeViews();
        setupClickListeners();

        vehicleInfoFooter = findViewById(R.id.vehicle_footer);
        if (vehicleInfoFooter != null) {
            vehicleInfoFooter.setOverlayView(findViewById(R.id.footer_overlay));
        }

        showEmptyState();
    }

    private void initializeViews() {
        milStatusCard = findViewById(R.id.mil_status_card);
        milStatusIcon = findViewById(R.id.mil_status_icon);
        milStatusText = findViewById(R.id.mil_status_text);
        milStatusSubtitle = findViewById(R.id.mil_status_subtitle);
        faultCodesList = findViewById(R.id.fault_codes_list);
        progressBar = findViewById(R.id.progress_bar);
        scanButton = findViewById(R.id.scan_button);
        emptyView = findViewById(R.id.empty_view);

        faultCodesList.setLayoutManager(new LinearLayoutManager(this));
        faultCodesList.setNestedScrollingEnabled(false);

        adapter = new FaultCodeListAdapter();
        faultCodesList.setAdapter(adapter);
        adapter.setOnCodeClickListener(code -> showFaultCodeOptionsDialog(code));
    }

    private void setupClickListeners() {
        scanButton.setOnClickListener(v -> startScan());
    }

    private void startScan() {
        if (isScanning) {
            log.fine("Scan request ignored – already scanning");
            return;
        }

        isScanning = true;
        showProgress();

        faultCodeService.scanAllCodes()
            .thenAccept(codes -> runOnUiThread(() -> handleScanComplete(codes)))
            .exceptionally(error -> {
                runOnUiThread(() -> handleScanError(error));
                return null;
            });
    }

    private void handleScanComplete(List<FaultCodeService.FaultCodeInfo> codes) {
        isScanning = false;
        currentCodes = codes != null ? new ArrayList<>(codes) : Collections.emptyList();

        hideProgress();

        if (currentCodes.isEmpty()) {
            showNoCodesState();
        } else {
            showFaultCodes(currentCodes);
        }
    }

    private void handleScanError(Throwable error) {
        log.severe("Fault code scan failed: " + error);
        isScanning = false;
        currentCodes = Collections.emptyList();

        hideProgress();
        showEmptyState();

        String message = extractErrorMessage(error);
        Snackbar.make(
            findViewById(android.R.id.content),
            getString(R.string.fault_codes_scan_failed, message),
            Snackbar.LENGTH_LONG
        ).show();
    }

    private void clearCodes() {
        if (isScanning) {
            log.fine("Clear request ignored – operation already in progress");
            return;
        }

        if (currentCodes.isEmpty()) {
            log.fine("Clear request ignored – no codes to clear");
            return;
        }

        isScanning = true;
        showProgress();

        faultCodeService.clearFaultCodes()
            .thenAccept(success -> runOnUiThread(() -> {
                isScanning = false;
                if (success) {
                    Snackbar.make(
                        findViewById(android.R.id.content),
                        R.string.fault_codes_clear_success,
                        Snackbar.LENGTH_SHORT
                    ).show();
                    startScan();
                } else {
                    hideProgress();
                    Snackbar.make(
                        findViewById(android.R.id.content),
                        getString(R.string.fault_codes_clear_failed, getString(R.string.fault_codes_generic_error)),
                        Snackbar.LENGTH_LONG
                    ).show();
                }
            }))
            .exceptionally(error -> {
                runOnUiThread(() -> {
                    isScanning = false;
                    hideProgress();
                    String message = extractErrorMessage(error);
                    Snackbar.make(
                        findViewById(android.R.id.content),
                        getString(R.string.fault_codes_clear_failed, message),
                        Snackbar.LENGTH_LONG
                    ).show();
                });
                return null;
            });
    }

    private void showFaultCodes(List<FaultCodeService.FaultCodeInfo> codes) {
        emptyView.setVisibility(View.GONE);
        faultCodesList.setVisibility(View.VISIBLE);
        milStatusCard.setVisibility(View.VISIBLE);

        int cardColor = ContextCompat.getColor(this, R.color.fault_error);
        int iconColor = ContextCompat.getColor(this, R.color.text_primary_dark);
        milStatusCard.setCardBackgroundColor(cardColor);
        milStatusIcon.setColorFilter(iconColor);

        int count = codes.size();
        String header = getResources().getQuantityString(R.plurals.fault_codes_count, count, count);
        milStatusText.setText(header);

        int pending = 0;
        for (FaultCodeService.FaultCodeInfo code : codes) {
            if (code.isPending) {
                pending++;
            }
        }
        int confirmed = count - pending;
        milStatusSubtitle.setText(getString(R.string.fault_codes_status_summary, confirmed, pending));

        adapter.setFaultCodes(codes);
        scanButton.setText(R.string.fault_codes_button_rescan);
    }

    private void showNoCodesState() {
        emptyView.setVisibility(View.GONE);
        faultCodesList.setVisibility(View.GONE);
        milStatusCard.setVisibility(View.VISIBLE);

        int cardColor = ContextCompat.getColor(this, R.color.fault_success);
        int iconColor = ContextCompat.getColor(this, R.color.text_primary_dark);
        milStatusCard.setCardBackgroundColor(cardColor);
        milStatusIcon.setColorFilter(iconColor);

        milStatusText.setText(R.string.fault_codes_no_codes);
        milStatusSubtitle.setText(R.string.fault_codes_engine_ok);

        adapter.setFaultCodes(Collections.emptyList());
        scanButton.setText(R.string.fault_codes_button_rescan);
    }

    private void showEmptyState() {
        emptyView.setVisibility(View.VISIBLE);
        faultCodesList.setVisibility(View.GONE);
        milStatusCard.setVisibility(View.GONE);

        adapter.setFaultCodes(Collections.emptyList());
        scanButton.setText(R.string.fault_codes_button_scan);
    }

    private void showProgress() {
        progressBar.setVisibility(View.VISIBLE);
        emptyView.setVisibility(View.GONE);
        scanButton.setEnabled(false);
    }

    private void hideProgress() {
        progressBar.setVisibility(View.GONE);
        scanButton.setEnabled(true);
    }

    private String extractErrorMessage(Throwable throwable) {
        if (throwable instanceof CompletionException || throwable instanceof ExecutionException) {
            Throwable cause = throwable.getCause();
            if (cause != null) {
                return extractErrorMessage(cause);
            }
        }
        if (throwable == null || throwable.getMessage() == null || throwable.getMessage().trim().isEmpty()) {
            return getString(R.string.fault_codes_generic_error);
        }
        return throwable.getMessage();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.fault_codes_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            return true;
        } else if (id == R.id.action_rescan) {
            startScan();
            return true;
        } else if (id == R.id.action_clear_codes) {
            clearCodes();
            return true;
        } else if (id == R.id.action_info) {
            showInfoDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showInfoDialog() {
        new AlertDialog.Builder(this)
            .setTitle(R.string.fault_codes_info_title)
            .setMessage(R.string.fault_codes_info_message)
            .setPositiveButton(R.string.fault_codes_info_ack, null)
            .setIcon(android.R.drawable.ic_menu_info_details)
            .show();
    }

    private void showFaultCodeOptionsDialog(FaultCodeService.FaultCodeInfo code) {
        String title = code.code + (code.isPending ? " (Pending)" : " (Confirmed)");
        String message = code.description;

        new AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Search Web", (dialog, which) -> searchFaultCodeOnWeb(code.code))
            .setNeutralButton("Copy Code", (dialog, which) -> copyFaultCodeToClipboard(code.code, code.description))
            .setNegativeButton("Close", null)
            .setIcon(android.R.drawable.ic_dialog_info)
            .show();
    }

    private void searchFaultCodeOnWeb(String code) {
        try {
            Intent intent = new Intent(Intent.ACTION_WEB_SEARCH);
            intent.putExtra(SearchManager.QUERY, "OBD " + code);
            startActivity(intent);
        } catch (Exception e) {
            Snackbar.make(findViewById(android.R.id.content),
                "Unable to search: " + e.getMessage(),
                Snackbar.LENGTH_SHORT).show();
        }
    }

    private void copyFaultCodeToClipboard(String code, String description) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            String text = code + " - " + description;
            ClipData clip = ClipData.newPlainText("OBD Fault Code", text);
            clipboard.setPrimaryClip(clip);
            Snackbar.make(findViewById(android.R.id.content),
                "Copied: " + code,
                Snackbar.LENGTH_SHORT).show();
        }
    }
}
