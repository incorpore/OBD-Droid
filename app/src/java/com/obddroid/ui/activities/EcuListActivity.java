package com.obddroid.ui.activities;

import android.os.Bundle;
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
import com.obddroid.vehicle.EcuInfo;
import com.obddroid.vehicle.EcuManager;

import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Activity for displaying the list of discovered ECUs in the vehicle.
 * Shows ECU addresses, names, calibration IDs, and other diagnostic information.
 */
public class EcuListActivity extends AppCompatActivity implements EcuManager.EcuManagerListener {

    private static final Logger log = Logger.getLogger(EcuListActivity.class.getName());

    private RecyclerView recyclerView;
    private EcuAdapter adapter;
    private View emptyView;
    private ProgressBar progressBar;
    private Button scanButton;
    private EcuManager ecuManager;

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

        // Set up RecyclerView
        adapter = new EcuAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        // Set up ECU Manager
        ecuManager = EcuManager.getInstance();
        ecuManager.addListener(this);

        // Set up scan button
        scanButton.setOnClickListener(v -> scanForEcus());

        // Load existing ECU data
        loadEcuData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        ecuManager.startListening();
        loadEcuData();
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
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
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
            Snackbar.make(recyclerView, "Not connected to vehicle", Snackbar.LENGTH_SHORT).show();
            return;
        }

        // Check if we're actually connected (not just initialized)
        ElmProt.STAT status = CommService.elm.getStatus();
        if (status != ElmProt.STAT.CONNECTED && status != ElmProt.STAT.ECU_DETECTED) {
            Snackbar.make(recyclerView, "Please connect to vehicle first", Snackbar.LENGTH_SHORT).show();
            return;
        }

        // Show progress
        progressBar.setVisibility(View.VISIBLE);
        scanButton.setEnabled(false);

        // Clear existing data
        ecuManager.clear();
        adapter.setEcuList(null);

        Snackbar.make(recyclerView, "Scanning for ECUs with detailed discovery...", Snackbar.LENGTH_SHORT).show();

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

                    List<EcuInfo> ecuList = ecuManager.getEcuList();
                    loadEcuData();  // Refresh display

                    if (ecuList.isEmpty()) {
                        Snackbar.make(recyclerView,
                            "No ECUs found. Vehicle may not support Mode 9.",
                            Snackbar.LENGTH_LONG).show();
                    } else {
                        Snackbar.make(recyclerView,
                            "Found " + ecuList.size() + " ECU(s) with detailed info",
                            Snackbar.LENGTH_SHORT).show();
                    }
                });

            } catch (java.util.concurrent.TimeoutException e) {
                log.warning("ECU discovery timed out after 15 seconds");
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    scanButton.setEnabled(true);
                    Snackbar.make(recyclerView,
                        "Discovery timed out - try again or check connection",
                        Snackbar.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                log.warning("Error during ECU discovery: " + e.getMessage());
                e.printStackTrace();

                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    scanButton.setEnabled(true);
                    Snackbar.make(recyclerView,
                        "Error scanning for ECUs: " + e.getMessage(),
                        Snackbar.LENGTH_LONG).show();
                });
            }
        }).start();
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
        runOnUiThread(() -> {
            log.info("ECU discovered: " + ecu);
            adapter.addEcu(ecu);
            updateEmptyView();
        });
    }

    @Override
    public void onEcuUpdated(EcuInfo ecu) {
        runOnUiThread(() -> {
            log.info("ECU updated: " + ecu);
            adapter.updateEcu(ecu);
        });
    }
}
