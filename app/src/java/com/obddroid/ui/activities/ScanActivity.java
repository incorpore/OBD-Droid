package com.obddroid.ui.activities;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.FileProvider;

import com.obddroid.R;
import com.obddroid.scan.DiagnosticAnalyzer;
import com.obddroid.scan.ReportArtifacts;
import com.obddroid.scan.ReportBuilder;
import com.obddroid.scan.ScanConfiguration;
import com.obddroid.scan.ScanOrchestrator;
import com.obddroid.scan.ScanReport;
import com.obddroid.scan.ScanResultsManager;
import com.obddroid.scan.StageResult;
import com.obddroid.services.VehicleManager;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.obddroid.features.copilot.ui.CoPilotActivity;

public class ScanActivity extends AppCompatActivity implements ScanOrchestrator.ScanProgressListener {

    private TextView vehicleName;
    private TextView vehicleDetails;
    private TextView progressText;
    private TextView progressPercentage;
    private TextView currentStageText;
    private ProgressBar progressBar;
    private LinearLayout stagesContainer;
    private CardView resultsCard;
    private TextView resultsSummary;
    private CardView aiAnalysisCard;
    private TextView aiAnalysisText;
    private ProgressBar aiAnalysisProgress;
    private Button startButton;
    private Button cancelButton;
    private Button analyzeButton;
    private Button viewCopilotButton;
    private Button shareButton;

    private ScanOrchestrator scanService;
    private boolean serviceBound = false;
    private int totalStages = 0;
    private int completedStages = 0;
    private Map<Integer, View> stageViews = new HashMap<>();
    private ScanReport lastReport;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            ScanOrchestrator.LocalBinder binder = (ScanOrchestrator.LocalBinder) service;
            scanService = binder.getService();
            scanService.setProgressListener(ScanActivity.this);
            serviceBound = true;

            // If scan is already running, update UI
            if (scanService.isScanning()) {
                onScanStartInProgress();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
            scanService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Full Vehicle Scan");
        }

        initializeViews();
        setupListeners();
        loadVehicleInfo();

        // Bind to scan service
        Intent intent = new Intent(this, ScanOrchestrator.class);
        startService(intent); // Ensure service is created
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void initializeViews() {
        vehicleName = findViewById(R.id.vehicle_name);
        vehicleDetails = findViewById(R.id.vehicle_details);
        progressText = findViewById(R.id.progress_text);
        progressPercentage = findViewById(R.id.progress_percentage);
        currentStageText = findViewById(R.id.current_stage_text);
        progressBar = findViewById(R.id.progress_bar);
        stagesContainer = findViewById(R.id.stages_container);
        resultsCard = findViewById(R.id.results_card);
        resultsSummary = findViewById(R.id.results_summary);
        aiAnalysisCard = findViewById(R.id.ai_analysis_card);
        aiAnalysisText = findViewById(R.id.ai_analysis_text);
        aiAnalysisProgress = findViewById(R.id.ai_analysis_progress);
        startButton = findViewById(R.id.start_button);
        cancelButton = findViewById(R.id.cancel_button);
        analyzeButton = findViewById(R.id.analyze_button);
        viewCopilotButton = findViewById(R.id.view_copilot_button);
        shareButton = findViewById(R.id.share_button);
    }

    private void setupListeners() {
        startButton.setOnClickListener(v -> startScan());
        cancelButton.setOnClickListener(v -> cancelScan());
        analyzeButton.setOnClickListener(v -> runAIAnalysis());
        viewCopilotButton.setOnClickListener(v -> openCoPilot());
        shareButton.setOnClickListener(v -> shareScanReport());
    }

    private void loadVehicleInfo() {
        VehicleManager vehicleManager = VehicleManager.getInstance(this);

        String displayName = vehicleManager.getVehicleDisplayName();
        if (displayName != null && !displayName.isEmpty()) {
            vehicleName.setText(displayName);
        }

        String vin = vehicleManager.getCurrentVIN();
        if (vin != null && !vin.isEmpty()) {
            vehicleDetails.setText("VIN: " + vin + "\nReady for comprehensive diagnostic scan");
        } else {
            vehicleDetails.setText("Ready for comprehensive diagnostic scan");
        }
    }

    private void startScan() {
        if (!serviceBound || scanService == null) {
            Toast.makeText(this, "Scan service not ready", Toast.LENGTH_SHORT).show();
            return;
        }

        if (scanService.isScanning()) {
            Toast.makeText(this, "Scan already in progress", Toast.LENGTH_SHORT).show();
            return;
        }

        // Reset UI
        stagesContainer.removeAllViews();
        stageViews.clear();
        completedStages = 0;
        totalStages = 0;
        resultsCard.setVisibility(View.GONE);

        // Update buttons
        startButton.setVisibility(View.GONE);
        cancelButton.setVisibility(View.VISIBLE);
        viewCopilotButton.setVisibility(View.GONE);
        analyzeButton.setVisibility(View.GONE);
        shareButton.setVisibility(View.GONE);

        // Start scan with default configuration
        ScanConfiguration config = ScanConfiguration.getDefault();
        scanService.startScan(config);
    }

    private void cancelScan() {
        if (serviceBound && scanService != null) {
            scanService.cancelScan();
        }
    }

    private void openCoPilot() {
        Intent intent = new Intent(this, CoPilotActivity.class);
        startActivity(intent);
    }

    private void onScanStartInProgress() {
        startButton.setVisibility(View.GONE);
        cancelButton.setVisibility(View.VISIBLE);
        progressText.setText("Scan in progress...");
        analyzeButton.setVisibility(View.GONE);
        shareButton.setVisibility(View.GONE);
        viewCopilotButton.setVisibility(View.GONE);
    }

    @Override
    public void onScanStarted(int totalStages) {
        runOnUiThread(() -> {
            this.totalStages = totalStages;
            this.completedStages = 0;

            progressBar.setMax(totalStages);
            progressBar.setProgress(0);
            progressText.setText("Starting scan...");
            progressPercentage.setText("0%");
            currentStageText.setText(totalStages + " stages to complete");

            vehicleDetails.setText("Scanning in progress - please wait");
            analyzeButton.setVisibility(View.GONE);
            shareButton.setVisibility(View.GONE);
            viewCopilotButton.setVisibility(View.GONE);
        });
    }

    @Override
    public void onStageStarted(int stageIndex, String stageName) {
        runOnUiThread(() -> {
            // Add stage to UI if not already present
            if (!stageViews.containsKey(stageIndex)) {
                View stageView = createStageView(stageName, "in_progress");
                stagesContainer.addView(stageView);
                stageViews.put(stageIndex, stageView);
            }

            updateStageView(stageIndex, stageName, "in_progress", null);

            currentStageText.setText("Stage " + (stageIndex + 1) + "/" + totalStages + ": " + stageName);
            progressText.setText(stageName + "...");
        });
    }

    @Override
    public void onStageCompleted(int stageIndex, StageResult result) {
        runOnUiThread(() -> {
            completedStages++;

            int progress = (int) ((completedStages / (float) totalStages) * 100);
            progressBar.setProgress(completedStages);
            progressPercentage.setText(progress + "%");

            String status = result.isSuccess() ? "success" :
                           result.getStatus() == StageResult.Status.SKIPPED ? "skipped" : "failed";

            updateStageView(stageIndex, null, status, result.getMessage());

            if (completedStages == totalStages) {
                currentStageText.setText("All stages completed");
            }
        });
    }

    @Override
    public void onScanCompleted(ScanReport report) {
        runOnUiThread(() -> {
            this.lastReport = report;

            // Store scan results for CoPilot access
            ScanResultsManager.getInstance(this).storeScanReport(report);

            progressBar.setProgress(totalStages);
            progressPercentage.setText("100%");
            progressText.setText("Scan Complete!");
            currentStageText.setText("Duration: " + (report.getTotalDurationMs() / 1000.0) + " seconds");

            vehicleDetails.setText("Scan completed successfully");

            // Show results
            resultsCard.setVisibility(View.VISIBLE);
            resultsSummary.setText(report.getSummary());

            // Update buttons
            cancelButton.setVisibility(View.GONE);
            startButton.setText("Scan Again");
            startButton.setVisibility(View.VISIBLE);
            analyzeButton.setVisibility(View.VISIBLE);
            viewCopilotButton.setVisibility(View.VISIBLE);

            ReportArtifacts artifacts = ensureReportArtifacts(report);
            if (artifacts != null) {
                shareButton.setVisibility(View.VISIBLE);
            } else {
                shareButton.setVisibility(View.GONE);
            }

            Toast.makeText(this, "Scan complete! " + report.getStageResults().size() + " stages finished",
                Toast.LENGTH_LONG).show();
        });
    }

    private ReportArtifacts ensureReportArtifacts(ScanReport report) {
        if (report == null) {
            return null;
        }

        ReportArtifacts artifacts = report.getArtifacts();
        if (artifacts != null && artifactExists(artifacts)) {
            return artifacts;
        }

        try {
            ReportBuilder builder = new ReportBuilder(this);
            artifacts = builder.build(report);
            report.attachArtifacts(artifacts);
            return artifacts;
        } catch (IOException e) {
            Toast.makeText(this, "Unable to prepare report artifacts: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return null;
        }
    }

    private boolean artifactExists(ReportArtifacts artifacts) {
        if (artifacts == null) return false;
        return (artifacts.getArchiveFile() != null && artifacts.getArchiveFile().exists()) ||
               (artifacts.getMarkdownFile() != null && artifacts.getMarkdownFile().exists()) ||
               (artifacts.getJsonFile() != null && artifacts.getJsonFile().exists());
    }

    private void shareScanReport() {
        if (lastReport == null) {
            Toast.makeText(this, "Run a scan before sharing", Toast.LENGTH_SHORT).show();
            return;
        }

        ReportArtifacts artifacts = ensureReportArtifacts(lastReport);
        if (artifacts == null) {
            return;
        }

        File shareFile = null;
        String mimeType = "application/zip";

        if (artifacts.getArchiveFile() != null && artifacts.getArchiveFile().exists()) {
            shareFile = artifacts.getArchiveFile();
            mimeType = "application/zip";
        } else if (artifacts.getMarkdownFile() != null && artifacts.getMarkdownFile().exists()) {
            shareFile = artifacts.getMarkdownFile();
            mimeType = "text/markdown";
        } else if (artifacts.getJsonFile() != null && artifacts.getJsonFile().exists()) {
            shareFile = artifacts.getJsonFile();
            mimeType = "application/json";
        } else {
            shareFile = writeSummaryToCache(lastReport);
            mimeType = "text/plain";
        }

        if (shareFile == null || !shareFile.exists()) {
            Toast.makeText(this, "No report file available to share", Toast.LENGTH_LONG).show();
            return;
        }

        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", shareFile);

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType(mimeType);
        shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "OBD-Droid Scan Report " + lastReport.getScanId());
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        try {
            startActivity(Intent.createChooser(shareIntent, "Share Scan Report"));
        } catch (Exception e) {
            Toast.makeText(this, "Unable to share report: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private File writeSummaryToCache(ScanReport report) {
        try {
            File cacheDir = new File(getCacheDir(), "scan_reports");
            if (!cacheDir.exists() && !cacheDir.mkdirs()) {
                return null;
            }

            File summaryFile = new File(cacheDir, report.getScanId() + "_summary.txt");
            try (FileWriter writer = new FileWriter(summaryFile, false)) {
                writer.write(report.getSummary());
            }
            return summaryFile;
        } catch (IOException e) {
            Toast.makeText(this, "Unable to create summary file: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return null;
        }
    }

    private void runAIAnalysis() {
        if (lastReport == null) {
            Toast.makeText(this, "No scan data available for analysis", Toast.LENGTH_SHORT).show();
            return;
        }

        // AI diagnostic analysis is now integrated with CoPilot
        // Show message directing users to use CoPilot for analysis
        aiAnalysisCard.setVisibility(View.VISIBLE);
        aiAnalysisProgress.setVisibility(View.GONE);
        aiAnalysisText.setText("AI diagnostic analysis is now integrated with CoPilot!\n\n" +
            "Open CoPilot and ask:\n" +
            "• \"Analyze my scan results\"\n" +
            "• \"What do these fault codes mean?\"\n" +
            "• \"What should I fix first?\"\n\n" +
            "CoPilot can run scans, analyze results, and provide detailed repair recommendations - all in one conversation!");
        analyzeButton.setEnabled(true);
        analyzeButton.setText("Open CoPilot");

        // Update button to open CoPilot with scan analysis request
        analyzeButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, com.obddroid.features.copilot.ui.CoPilotActivity.class);
            intent.putExtra(com.obddroid.features.copilot.ui.CoPilotActivity.EXTRA_INITIAL_MESSAGE,
                "Analyze my latest scan results and tell me what's wrong. What should I fix first?");
            startActivity(intent);
        });
    }

    @Override
    public void onScanCancelled() {
        runOnUiThread(() -> {
            progressText.setText("Scan Cancelled");
            currentStageText.setText("Scan was cancelled by user");
            vehicleDetails.setText("Scan cancelled - partial data may be available");

            cancelButton.setVisibility(View.GONE);
            startButton.setText("Start New Scan");
            startButton.setVisibility(View.VISIBLE);
            analyzeButton.setVisibility(View.GONE);
            shareButton.setVisibility(View.GONE);
            viewCopilotButton.setVisibility(View.GONE);

            Toast.makeText(this, "Scan cancelled", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onScanFailed(Exception error) {
        runOnUiThread(() -> {
            progressText.setText("Scan Failed");
            currentStageText.setText("Error: " + error.getMessage());
            vehicleDetails.setText("Scan failed - please try again");

            cancelButton.setVisibility(View.GONE);
            startButton.setText("Retry Scan");
            startButton.setVisibility(View.VISIBLE);
            analyzeButton.setVisibility(View.GONE);
            shareButton.setVisibility(View.GONE);
            viewCopilotButton.setVisibility(View.GONE);

            Toast.makeText(this, "Scan failed: " + error.getMessage(), Toast.LENGTH_LONG).show();
        });
    }

    private View createStageView(String stageName, String status) {
        LinearLayout stageLayout = new LinearLayout(this);
        stageLayout.setOrientation(LinearLayout.HORIZONTAL);
        stageLayout.setPadding(0, dpToPx(8), 0, dpToPx(8));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        stageLayout.setLayoutParams(params);

        // Status icon
        TextView icon = new TextView(this);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
            dpToPx(24), dpToPx(24)
        );
        iconParams.gravity = Gravity.CENTER_VERTICAL;
        iconParams.setMarginEnd(dpToPx(12));
        icon.setLayoutParams(iconParams);
        icon.setGravity(Gravity.CENTER);
        icon.setTextSize(16);
        icon.setTag("icon");

        // Stage name
        TextView nameText = new TextView(this);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f
        );
        nameText.setLayoutParams(nameParams);
        nameText.setText(stageName);
        nameText.setTextColor(getColor(R.color.text_primary));
        nameText.setTextSize(14);
        nameText.setTag("name");

        // Message text (initially hidden)
        TextView messageText = new TextView(this);
        messageText.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        messageText.setTextColor(getColor(R.color.text_secondary));
        messageText.setTextSize(12);
        messageText.setVisibility(View.GONE);
        messageText.setTag("message");

        stageLayout.addView(icon);
        stageLayout.addView(nameText);

        updateStageStatus(stageLayout, status, null);

        return stageLayout;
    }

    private void updateStageView(int stageIndex, String stageName, String status, String message) {
        View stageView = stageViews.get(stageIndex);
        if (stageView != null && stageView instanceof LinearLayout) {
            if (stageName != null) {
                TextView nameText = stageView.findViewWithTag("name");
                if (nameText != null) {
                    nameText.setText(stageName);
                }
            }
            updateStageStatus((LinearLayout) stageView, status, message);
        }
    }

    private void updateStageStatus(LinearLayout stageLayout, String status, String message) {
        TextView icon = stageLayout.findViewWithTag("icon");

        if (icon != null) {
            switch (status) {
                case "pending":
                    icon.setText("○");
                    icon.setTextColor(getColor(R.color.text_secondary));
                    break;
                case "in_progress":
                    icon.setText("⟳");
                    icon.setTextColor(getColor(R.color.colorPrimary));
                    break;
                case "success":
                    icon.setText("✓");
                    icon.setTextColor(getColor(R.color.fault_success));
                    break;
                case "skipped":
                    icon.setText("⊘");
                    icon.setTextColor(getColor(R.color.text_secondary));
                    break;
                case "failed":
                    icon.setText("✗");
                    icon.setTextColor(getColor(R.color.fault_error));
                    break;
            }
        }
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (serviceBound) {
            unbindService(serviceConnection);
            serviceBound = false;
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
}
