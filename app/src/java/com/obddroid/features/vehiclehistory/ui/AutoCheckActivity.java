package com.obddroid.features.vehiclehistory.ui;

import android.app.Dialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.FileProvider;

import android.view.Menu;
import android.view.MenuItem;
import com.google.gson.Gson;
import com.obddroid.R;
import com.obddroid.features.vehiclehistory.data.AutoCheckService;
import com.obddroid.features.vehiclehistory.model.AutoCheckReport;
import com.obddroid.services.VehicleManager;
import com.obddroid.ui.components.VehicleInfoFooter;
import com.obddroid.utils.SnackbarHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * World-Class AutoCheck Vehicle History Activity
 * Features sophisticated data visualization and premium UI
 */
public class AutoCheckActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "AutoCheckCache";
    private static final String PREFS_KEY_PREFIX = "report_";

    // Input views
    private CardView emptyStateCard;
    private Button checkHistoryButton;
    private String currentVin;
    private LinearLayout loadingContainer;
    private ProgressBar loadingSpinner;
    private TextView loadingText;

    // Error views
    private CardView errorCard;
    private TextView errorMessage;

    // Report container
    private LinearLayout reportContainer;

    // Hero Score Section
    private TextView vehicleName;
    private TextView vehicleVin;
    private View scoreCircleBackground;
    private TextView autoCheckScore;
    private TextView scoreRange;
    private TextView scoreInterpretation;
    private LinearLayout quickStatusContainer;

    // Score Analysis Section (NEW)
    private CardView scoreAnalysisCard;
    private LinearLayout comparisonContainer;
    private TextView vehicleComparison;
    private LinearLayout outlookContainer;
    private TextView vehicleOutlook;
    private LinearLayout increasingFactorsContainer;
    private LinearLayout increasingFactorsList;
    private LinearLayout decreasingFactorsContainer;
    private LinearLayout decreasingFactorsList;

    // Stats Grid
    private CardView statsCard;
    private TextView statOwners;
    private TextView statOdometer;
    private TextView statServiceRecords;
    private TextView statYear;

    // Safety Section
    private CardView safetyCard;
    private TextView titleBrandValue;
    private TextView accidentValue;
    private TextView structuralValue;
    private TextView airbagValue;
    private TextView rollbackValue;

    // Details Section
    private CardView detailsCard;
    private TextView vehicleDetailsText;

    // Timeline Section
    private CardView timelineCard;
    private TextView timelineSummary;
    private LinearLayout timelineContainer;

    // Report data and PDF tracking
    private AutoCheckReport currentReport;
    private String currentPdfFilePath;
    private boolean isPdfAvailable = false;

    private VehicleInfoFooter vehicleInfoFooter;
    private AutoCheckService autoCheckService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_autocheck);

        // Set navigation bar color to match footer
        getWindow().setNavigationBarColor(Color.parseColor("#212121"));

        // Configure action bar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Vehicle History");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // Initialize service
        autoCheckService = new AutoCheckService(this);

        // Find all views
        findViews();

        // Wire up footer overlay
        setupFooterOverlay();

        // Get VIN from VehicleManager
        loadCurrentVin();

        // Set button click listeners
        checkHistoryButton.setOnClickListener(v -> fetchVehicleHistory());

        // Try to load cached report for current VIN
        loadCachedReport();

        // Check API health on startup
        checkApiHealth();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.vehicle_history_menu, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem saveItem = menu.findItem(R.id.action_save_report);
        if (saveItem != null) {
            saveItem.setVisible(currentReport != null);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_refresh) {
            // Refresh the report - fetch a new one from the API
            SnackbarHelper.showSnackbar(this, "Refreshing vehicle history...", SnackbarHelper.MessageType.INFO);
            fetchVehicleHistory();
            return true;
        } else if (item.getItemId() == R.id.action_save_report) {
            showSaveReportDialog();
            return true;
        } else if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void findViews() {
        // Input views
        emptyStateCard = findViewById(R.id.empty_state_card);
        checkHistoryButton = findViewById(R.id.check_history_button);
        loadingContainer = findViewById(R.id.loading_container);
        loadingSpinner = findViewById(R.id.loading_spinner);
        loadingText = findViewById(R.id.loading_text);
        errorCard = findViewById(R.id.error_card);
        errorMessage = findViewById(R.id.error_message);
        reportContainer = findViewById(R.id.report_container);

        // Hero Score Section
        vehicleName = findViewById(R.id.vehicle_name);
        vehicleVin = findViewById(R.id.vehicle_vin);
        scoreCircleBackground = findViewById(R.id.score_circle_background);
        autoCheckScore = findViewById(R.id.autocheck_score);
        scoreRange = findViewById(R.id.score_range);
        scoreInterpretation = findViewById(R.id.score_interpretation);
        quickStatusContainer = findViewById(R.id.quick_status_container);

        // Score Analysis Section
        scoreAnalysisCard = findViewById(R.id.score_analysis_card);
        comparisonContainer = findViewById(R.id.comparison_container);
        vehicleComparison = findViewById(R.id.vehicle_comparison);
        outlookContainer = findViewById(R.id.outlook_container);
        vehicleOutlook = findViewById(R.id.vehicle_outlook);
        increasingFactorsContainer = findViewById(R.id.increasing_factors_container);
        increasingFactorsList = findViewById(R.id.increasing_factors_list);
        decreasingFactorsContainer = findViewById(R.id.decreasing_factors_container);
        decreasingFactorsList = findViewById(R.id.decreasing_factors_list);

        // Stats Grid
        statsCard = findViewById(R.id.stats_card);
        statOwners = findViewById(R.id.stat_owners);
        statOdometer = findViewById(R.id.stat_odometer);
        statServiceRecords = findViewById(R.id.stat_service_records);
        statYear = findViewById(R.id.stat_year);

        // Safety Section
        safetyCard = findViewById(R.id.safety_card);
        titleBrandValue = findViewById(R.id.title_brand_value);
        accidentValue = findViewById(R.id.accident_value);
        structuralValue = findViewById(R.id.structural_value);
        airbagValue = findViewById(R.id.airbag_value);
        rollbackValue = findViewById(R.id.rollback_value);

        // Details Section
        detailsCard = findViewById(R.id.details_card);
        vehicleDetailsText = findViewById(R.id.vehicle_details_text);

        // Timeline Section
        timelineCard = findViewById(R.id.timeline_card);
        timelineSummary = findViewById(R.id.timeline_summary);
        timelineContainer = findViewById(R.id.timeline_container);
    }

    private void setupFooterOverlay() {
        vehicleInfoFooter = findViewById(R.id.vehicle_footer);
        View overlay = findViewById(R.id.footer_overlay);

        if (vehicleInfoFooter != null && overlay != null) {
            vehicleInfoFooter.setOverlayView(overlay);
        }
    }

    /**
     * Load current VIN from VehicleManager
     */
    private void loadCurrentVin() {
        try {
            VehicleManager vehicleManager = VehicleManager.getInstance();
            currentVin = vehicleManager.getCurrentVIN();

            if (currentVin == null || currentVin.isEmpty()) {
                // Disable button if no VIN available
                checkHistoryButton.setEnabled(false);
                checkHistoryButton.setText("No VIN Available");
            }
        } catch (Exception e) {
            // Disable button if error getting VIN
            checkHistoryButton.setEnabled(false);
            checkHistoryButton.setText("VIN Not Available");
        }
    }

    private void checkApiHealth() {
        autoCheckService.checkHealth(healthy -> {
            if (!healthy) {
                showError("Warning: AutoCheck API server is not reachable.\n\n" +
                        "Please ensure:\n" +
                        "1. Your laptop is running the API server\n" +
                        "2. Both devices are on the same WiFi\n" +
                        "3. Update the IP address if needed");
            }
        });
    }

    private void fetchVehicleHistory() {
        // Use VIN from VehicleManager
        if (currentVin == null || currentVin.isEmpty()) {
            SnackbarHelper.showSnackbar(this, "No VIN available", SnackbarHelper.MessageType.ERROR);
            return;
        }

        if (currentVin.length() != 17) {
            SnackbarHelper.showSnackbar(this, "Invalid VIN length", SnackbarHelper.MessageType.ERROR);
            return;
        }

        String vin = currentVin.toUpperCase();

        // Hide previous results and errors
        errorCard.setVisibility(View.GONE);
        reportContainer.setVisibility(View.GONE);
        isPdfAvailable = false;
        invalidateOptionsMenu();

        // Show loading
        checkHistoryButton.setEnabled(false);
        loadingContainer.setVisibility(View.VISIBLE);

        // Fetch report WITHOUT PDF for faster initial loading
        autoCheckService.fetchReport(vin, new AutoCheckService.AutoCheckCallback() {
            @Override
            public void onSuccess(AutoCheckReport report) {
                // Hide loading
                loadingContainer.setVisibility(View.GONE);
                checkHistoryButton.setEnabled(true);

                // Save report to cache
                saveReportToCache(report);

                // Display report immediately
                displayReport(report);

                SnackbarHelper.showSnackbar(AutoCheckActivity.this,
                    "Report loaded successfully!",
                    SnackbarHelper.MessageType.SUCCESS);

                // Start generating PDF in background (don't wait for it)
                generatePdfInBackground(vin);
            }

            @Override
            public void onError(String error) {
                // Hide loading
                loadingContainer.setVisibility(View.GONE);
                checkHistoryButton.setEnabled(true);

                // Show error
                showError(error);
            }
        });
    }

    /**
     * Display report with world-class visualization
     */
    private void displayReport(AutoCheckReport report) {
        // Store current report for export
        this.currentReport = report;
        invalidateOptionsMenu();

        // Hide error and empty state, show report
        errorCard.setVisibility(View.GONE);
        emptyStateCard.setVisibility(View.GONE);
        reportContainer.setVisibility(View.VISIBLE);

        // ═══════════════════════════════════════
        // HERO SCORE SECTION
        // ═══════════════════════════════════════
        displayHeroSection(report);

        // ═══════════════════════════════════════
        // SCORE ANALYSIS
        // ═══════════════════════════════════════
        displayScoreAnalysis(report);

        // ═══════════════════════════════════════
        // QUICK STATS GRID
        // ═══════════════════════════════════════
        displayStatsGrid(report);

        // ═══════════════════════════════════════
        // SAFETY & TITLE OVERVIEW
        // ═══════════════════════════════════════
        displaySafetySection(report);

        // ═══════════════════════════════════════
        // VEHICLE DETAILS
        // ═══════════════════════════════════════
        displayVehicleDetails(report);

        // ═══════════════════════════════════════
        // HISTORY TIMELINE
        // ═══════════════════════════════════════
        displayTimeline(report);
    }

    private void displayHeroSection(AutoCheckReport report) {
        // Vehicle name
        vehicleName.setText(report.getVehicleName());
        vehicleVin.setText("VIN: " + report.getVin());

        // AutoCheck score with dynamic coloring
        if (report.getScore() != null) {
            int score = report.getScore();
            autoCheckScore.setText(String.valueOf(score));

            // Set circle background based on score
            if (score >= 80) {
                scoreCircleBackground.setBackgroundResource(R.drawable.score_circle_excellent);
                scoreInterpretation.setText("Excellent Condition");
                scoreInterpretation.setTextColor(Color.parseColor("#4CAF50"));
            } else if (score >= 60) {
                scoreCircleBackground.setBackgroundResource(R.drawable.score_circle_good);
                scoreInterpretation.setText("Good Condition");
                scoreInterpretation.setTextColor(Color.parseColor("#FFC107"));
            } else if (score >= 40) {
                scoreCircleBackground.setBackgroundResource(R.drawable.score_circle_fair);
                scoreInterpretation.setText("Fair Condition");
                scoreInterpretation.setTextColor(Color.parseColor("#FF9800"));
            } else {
                scoreCircleBackground.setBackgroundResource(R.drawable.score_circle_poor);
                scoreInterpretation.setText("Poor Condition");
                scoreInterpretation.setTextColor(Color.parseColor("#F44336"));
            }

            // Score range
            if (report.getScoreRange() != null) {
                scoreRange.setText(String.format("Range: %d - %d",
                    report.getScoreRange().low, report.getScoreRange().high));
            } else {
                scoreRange.setText("Score: " + score);
            }
        } else {
            autoCheckScore.setText("N/A");
            scoreRange.setVisibility(View.GONE);
            scoreInterpretation.setText("Score Not Available");
            scoreInterpretation.setTextColor(Color.parseColor("#757575"));
        }

        // Quick status badges
        quickStatusContainer.removeAllViews();

        if (report.hasCleanTitle()) {
            addStatusBadge("Clean Title", "#4CAF50");
        }

        if (!report.hasAccidents()) {
            addStatusBadge("No Accidents", "#4CAF50");
        }

        if (report.getOdometerRollback() != null && !report.getOdometerRollback()) {
            addStatusBadge("No Rollback", "#4CAF50");
        }
    }

    private void addStatusBadge(String text, String colorHex) {
        TextView badge = new TextView(this);
        badge.setText(text);
        badge.setTextColor(Color.WHITE);
        badge.setTextSize(12);
        badge.setPadding(24, 12, 24, 12);
        badge.setBackgroundColor(Color.parseColor(colorHex));

        // Add rounded corners
        badge.setBackgroundResource(R.drawable.status_badge_success);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(8, 0, 8, 0);
        badge.setLayoutParams(params);

        quickStatusContainer.addView(badge);
    }

    private void displayScoreAnalysis(AutoCheckReport report) {
        // Check if we have any score analysis data
        boolean hasComparison = report.getVehicleComparison() != null && !report.getVehicleComparison().isEmpty();
        boolean hasOutlook = report.getVehicleOutlook() != null && !report.getVehicleOutlook().isEmpty();
        boolean hasIncreasing = report.getIncreasingFactors() != null && !report.getIncreasingFactors().isEmpty();
        boolean hasDecreasing = report.getDecreasingFactors() != null && !report.getDecreasingFactors().isEmpty();

        boolean hasAnyData = hasComparison || hasOutlook || hasIncreasing || hasDecreasing;

        if (!hasAnyData) {
            scoreAnalysisCard.setVisibility(View.GONE);
            return;
        }

        // Show the score analysis card
        scoreAnalysisCard.setVisibility(View.VISIBLE);

        // Vehicle Comparison
        if (hasComparison) {
            comparisonContainer.setVisibility(View.VISIBLE);
            vehicleComparison.setText(report.getVehicleComparison());
        } else {
            comparisonContainer.setVisibility(View.GONE);
        }

        // Vehicle Outlook
        if (hasOutlook) {
            outlookContainer.setVisibility(View.VISIBLE);
            vehicleOutlook.setText(report.getVehicleOutlook());
        } else {
            outlookContainer.setVisibility(View.GONE);
        }

        // Increasing Factors (Positive)
        if (hasIncreasing) {
            increasingFactorsContainer.setVisibility(View.VISIBLE);
            increasingFactorsList.removeAllViews();

            for (String factor : report.getIncreasingFactors()) {
                TextView factorView = new TextView(this);
                factorView.setText("• " + factor);
                factorView.setTextSize(14);
                factorView.setTextColor(Color.parseColor("#FFFFFF"));
                factorView.setPadding(0, 0, 0, dpToPx(8));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                );
                factorView.setLayoutParams(params);
                increasingFactorsList.addView(factorView);
            }
        } else {
            increasingFactorsContainer.setVisibility(View.GONE);
        }

        // Decreasing Factors (Watch Points)
        if (hasDecreasing) {
            decreasingFactorsContainer.setVisibility(View.VISIBLE);
            decreasingFactorsList.removeAllViews();

            for (String factor : report.getDecreasingFactors()) {
                TextView factorView = new TextView(this);
                factorView.setText("• " + factor);
                factorView.setTextSize(14);
                factorView.setTextColor(Color.parseColor("#FFFFFF"));
                factorView.setPadding(0, 0, 0, dpToPx(8));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                );
                factorView.setLayoutParams(params);
                decreasingFactorsList.addView(factorView);
            }
        } else {
            decreasingFactorsContainer.setVisibility(View.GONE);
        }
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private void displayStatsGrid(AutoCheckReport report) {
        // Owners - show count with usage type if available
        if (report.getOwners() != null && report.getOwners() > 0) {
            String ownerText = String.valueOf(report.getOwners());
            // Add usage type if available (e.g., "1 (Lease)")
            if (report.getUsage() != null && !report.getUsage().isEmpty()) {
                ownerText = ownerText + "\n(" + report.getUsage() + ")";
            }
            statOwners.setText(ownerText);
        } else {
            // Default to 1 if not specified (most vehicles have at least 1 owner)
            String ownerText = "1";
            if (report.getUsage() != null && !report.getUsage().isEmpty()) {
                ownerText = "1\n(" + report.getUsage() + ")";
            }
            statOwners.setText(ownerText);
        }

        // Odometer
        if (report.getLastOdometer() != null) {
            statOdometer.setText(String.format("%,d", report.getLastOdometer()));
        } else {
            statOdometer.setText("N/A");
        }

        // Service Records
        if (report.getServiceRecords() != null) {
            statServiceRecords.setText(String.valueOf(report.getServiceRecords()));
        } else {
            statServiceRecords.setText("0");
        }

        // Age (just show years old, not the year itself)
        Integer age = report.getVehicleAge();
        if (age != null) {
            String ageLabel = age == 1 ? "yr" : "yrs";
            statYear.setText(age + " " + ageLabel);
        } else if (report.getYear() != null) {
            // If we can't calculate age, show year as fallback
            statYear.setText(report.getYear());
        } else {
            statYear.setText("N/A");
        }
    }

    private void displaySafetySection(AutoCheckReport report) {
        // Title Brand
        if (report.getTitleBrand() != null) {
            titleBrandValue.setText(report.getTitleBrand().toUpperCase());
            if (report.hasCleanTitle()) {
                titleBrandValue.setBackgroundResource(R.drawable.status_badge_success);
            } else {
                titleBrandValue.setBackgroundResource(R.drawable.status_badge_error);
            }
        } else {
            titleBrandValue.setText("UNKNOWN");
            titleBrandValue.setBackgroundResource(R.drawable.status_badge_warning);
        }

        // Accident/Damage
        if (report.getAccidentDamage() != null) {
            accidentValue.setText(report.getAccidentDamage());
            if (report.hasAccidents()) {
                accidentValue.setTextColor(Color.parseColor("#F44336"));
            } else {
                accidentValue.setTextColor(Color.parseColor("#4CAF50"));
            }
        } else {
            accidentValue.setText("Unknown");
            accidentValue.setTextColor(Color.parseColor("#757575"));
        }

        // Structural Damage
        displayBooleanIndicator(structuralValue, report.getStructuralDamage(), true);

        // Airbag Deployment
        displayBooleanIndicator(airbagValue, report.getAirbagDeployed(), true);

        // Odometer Rollback - explicitly check for false (no rollback)
        Boolean rollback = report.getOdometerRollback();
        if (rollback != null) {
            if (rollback) {
                rollbackValue.setText("✗ Rollback Detected");
                rollbackValue.setTextColor(Color.parseColor("#F44336"));
            } else {
                rollbackValue.setText("✓ No Rollback");
                rollbackValue.setTextColor(Color.parseColor("#4CAF50"));
            }
        } else {
            // If null, default to "No Rollback" since AutoCheck would flag issues
            rollbackValue.setText("✓ No Rollback");
            rollbackValue.setTextColor(Color.parseColor("#4CAF50"));
        }
    }

    private void displayBooleanIndicator(TextView textView, Boolean value, boolean isNegative) {
        if (value == null) {
            textView.setText("Unknown");
            textView.setTextColor(Color.parseColor("#757575"));
        } else if (value) {
            textView.setText(isNegative ? "✗ Yes" : "✓ Yes");
            textView.setTextColor(Color.parseColor(isNegative ? "#F44336" : "#4CAF50"));
        } else {
            textView.setText(isNegative ? "✓ No" : "✗ No");
            textView.setTextColor(Color.parseColor(isNegative ? "#4CAF50" : "#F44336"));
        }
    }

    private void displayVehicleDetails(AutoCheckReport report) {
        StringBuilder details = new StringBuilder();

        if (report.getStyle() != null) {
            details.append("Style: ").append(report.getStyle()).append("\n");
        }

        if (report.getEngine() != null) {
            details.append("Engine: ").append(report.getEngine()).append("\n");
        }

        if (report.getCountry() != null) {
            details.append("Made in: ").append(report.getCountry()).append("\n");
        }

        if (report.getRecalls() != null) {
            details.append("\nRecalls: ").append(report.getRecalls()).append("\n");
        }

        if (details.length() > 0) {
            vehicleDetailsText.setText(details.toString().trim());
            detailsCard.setVisibility(View.VISIBLE);
        } else {
            detailsCard.setVisibility(View.GONE);
        }
    }

    private void displayTimeline(AutoCheckReport report) {
        if (report.getHistoryEvents() == null || report.getHistoryEvents().isEmpty()) {
            timelineCard.setVisibility(View.GONE);
            return;
        }

        timelineCard.setVisibility(View.VISIBLE);
        timelineContainer.removeAllViews();

        int totalEvents = report.getHistoryEvents().size();
        int displayCount = Math.min(15, totalEvents);

        timelineSummary.setText(String.format("%d total events (showing %d most recent)",
            totalEvents, displayCount));

        for (int i = 0; i < displayCount; i++) {
            AutoCheckReport.HistoryEvent event = report.getHistoryEvents().get(i);
            addTimelineEvent(event, i == displayCount - 1);
        }
    }

    private void addTimelineEvent(AutoCheckReport.HistoryEvent event, boolean isLast) {
        // Create event container
        LinearLayout eventLayout = new LinearLayout(this);
        eventLayout.setOrientation(LinearLayout.HORIZONTAL);
        eventLayout.setPadding(0, 0, 0, isLast ? 0 : 16);

        // Timeline indicator (dot + line)
        LinearLayout timelineIndicator = new LinearLayout(this);
        timelineIndicator.setOrientation(LinearLayout.VERTICAL);
        timelineIndicator.setGravity(Gravity.CENTER_HORIZONTAL);
        timelineIndicator.setPadding(0, 6, 20, 0);

        // Dot - larger and more prominent
        View dot = new View(this);
        dot.setBackgroundResource(R.drawable.timeline_dot);
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(16, 16);
        dot.setLayoutParams(dotParams);
        timelineIndicator.addView(dot);

        // Line (if not last) - thicker and more visible
        if (!isLast) {
            View line = new View(this);
            line.setBackgroundResource(R.drawable.timeline_line);
            LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(3,
                ViewGroup.LayoutParams.MATCH_PARENT);
            lineParams.topMargin = 6;
            line.setLayoutParams(lineParams);
            timelineIndicator.addView(line);
        }

        LinearLayout.LayoutParams indicatorParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        );
        timelineIndicator.setLayoutParams(indicatorParams);
        eventLayout.addView(timelineIndicator);

        // Event content with background card
        LinearLayout eventContent = new LinearLayout(this);
        eventContent.setOrientation(LinearLayout.VERTICAL);
        eventContent.setPadding(16, 14, 16, 14);
        eventContent.setBackgroundResource(R.drawable.stat_card_background);

        // Date - larger and more prominent
        TextView dateText = new TextView(this);
        dateText.setText(event.date != null ? event.date : "Unknown Date");
        dateText.setTextSize(15);
        dateText.setTextColor(Color.parseColor("#00ACC1"));
        dateText.setTypeface(null, android.graphics.Typeface.BOLD);
        dateText.setLetterSpacing(0.02f);
        eventContent.addView(dateText);

        // Details - larger and better spaced
        TextView detailsText = new TextView(this);
        detailsText.setText(event.details != null ? event.details : "No details");
        detailsText.setTextSize(15);
        detailsText.setTextColor(Color.parseColor("#212121"));
        detailsText.setPadding(0, 8, 0, 0);
        detailsText.setLineSpacing(4, 1.0f);
        eventContent.addView(detailsText);

        // Location - better visibility
        if (event.location != null && !event.location.isEmpty()) {
            TextView locationText = new TextView(this);
            locationText.setText("📍 " + event.location);
            locationText.setTextSize(13);
            locationText.setTextColor(Color.parseColor("#616161"));
            locationText.setPadding(0, 8, 0, 0);
            eventContent.addView(locationText);
        }

        // Odometer - better visibility
        if (event.odometer != null && !event.odometer.isEmpty()) {
            TextView odometerText = new TextView(this);
            odometerText.setText("🛣 " + event.odometer + " miles");
            odometerText.setTextSize(13);
            odometerText.setTextColor(Color.parseColor("#616161"));
            odometerText.setPadding(0, 4, 0, 0);
            eventContent.addView(odometerText);
        }

        // Source - more readable, not italic
        if (event.source != null && !event.source.isEmpty()) {
            TextView sourceText = new TextView(this);
            sourceText.setText("Source: " + event.source);
            sourceText.setTextSize(12);
            sourceText.setTextColor(Color.parseColor("#757575"));
            sourceText.setPadding(0, 8, 0, 0);
            eventContent.addView(sourceText);
        }

        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1.0f
        );
        eventContent.setLayoutParams(contentParams);
        eventLayout.addView(eventContent);

        timelineContainer.addView(eventLayout);
    }

    private void openPdf() {
        if (currentPdfFilePath == null || currentPdfFilePath.isEmpty()) {
            SnackbarHelper.showSnackbar(this, "PDF not available", SnackbarHelper.MessageType.WARNING);
            return;
        }

        try {
            File pdfFile = new File(currentPdfFilePath);
            if (!pdfFile.exists()) {
                SnackbarHelper.showSnackbar(this, "PDF file not found", SnackbarHelper.MessageType.ERROR);
                return;
            }

            Uri pdfUri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".provider",
                    pdfFile
            );

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(pdfUri, "application/pdf");
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NO_HISTORY);

            try {
                startActivity(intent);
            } catch (android.content.ActivityNotFoundException e) {
                // No PDF viewer installed, show share sheet instead
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("application/pdf");
                shareIntent.putExtra(Intent.EXTRA_STREAM, pdfUri);
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(shareIntent, "Open PDF with..."));
            }
        } catch (Exception e) {
            SnackbarHelper.showSnackbar(this, "Error opening PDF: " + e.getMessage(), SnackbarHelper.MessageType.ERROR);
        }
    }

    /**
     * Show dialog to choose export format (PDF, CSV, or JSON)
     */
    private void showSaveReportDialog() {
        if (currentReport == null) {
            SnackbarHelper.showSnackbar(this, "No report available to export", SnackbarHelper.MessageType.WARNING);
            return;
        }

        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_save_report);
        dialog.setCancelable(true);

        // PDF option - save a copy of the existing PDF
        View pdfOption = dialog.findViewById(R.id.option_export_pdf);
        pdfOption.setOnClickListener(v -> {
            dialog.dismiss();
            savePDFCopy();
        });

        // CSV option
        View csvOption = dialog.findViewById(R.id.option_export_csv);
        csvOption.setOnClickListener(v -> {
            dialog.dismiss();
            exportAsCSV();
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
     * Generate PDF in background after report loads
     * This happens silently so PDF is ready when user wants to save it
     */
    private void generatePdfInBackground(String vin) {
        // Fetch report WITH PDF in background
        autoCheckService.fetchReportWithPdf(vin, new AutoCheckService.AutoCheckPdfCallback() {
            @Override
            public void onSuccess(AutoCheckReport report, String pdfFilePath) {
                // Store PDF file path silently
                currentPdfFilePath = pdfFilePath;

                if (pdfFilePath != null && !pdfFilePath.isEmpty()) {
                    isPdfAvailable = true;
                    invalidateOptionsMenu();

                    // Update cache with PDF
                    saveReportToCache(report);
                }
            }

            @Override
            public void onError(String error) {
                // PDF generation failed - not critical, user can still see the report
                // Don't show error to user since this is a background operation
            }
        });
    }

    /**
     * Save a copy of the PDF report to Documents/OBDroid
     */
    private void savePDFCopy() {
        if (currentReport == null) {
            SnackbarHelper.showSnackbar(this, "No report available", SnackbarHelper.MessageType.WARNING);
            return;
        }

        // Check if PDF is ready
        if (currentPdfFilePath == null || currentPdfFilePath.isEmpty()) {
            SnackbarHelper.showSnackbar(this, "PDF is still being generated, please wait...", SnackbarHelper.MessageType.INFO);
            return;
        }

        Uri savedUri = null;
        File destPdf = null;

        try {
            File sourcePdf = new File(currentPdfFilePath);
            if (!sourcePdf.exists()) {
                SnackbarHelper.showSnackbar(this, "PDF file not found", SnackbarHelper.MessageType.ERROR);
                return;
            }

            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            String fileName = "vehicle_history_" + currentReport.getVin() + "_" + timestamp + ".pdf";

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/OBDroid");

                savedUri = getContentResolver().insert(MediaStore.Files.getContentUri("external"), values);
                if (savedUri == null) {
                    throw new IOException("Failed to create MediaStore entry");
                }

                OutputStream outputStream = getContentResolver().openOutputStream(savedUri);
                if (outputStream == null) {
                    throw new IOException("Failed to open output stream");
                }

                // Copy file
                FileInputStream inputStream = new FileInputStream(sourcePdf);
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                inputStream.close();
                outputStream.close();
            } else {
                File documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
                File obdroidDir = new File(documentsDir, "OBDroid");
                if (!obdroidDir.exists() && !obdroidDir.mkdirs()) {
                    throw new IOException("Unable to create export directory");
                }
                destPdf = new File(obdroidDir, fileName);

                // Copy file
                FileChannel source = new FileInputStream(sourcePdf).getChannel();
                FileChannel destination = new FileOutputStream(destPdf).getChannel();
                destination.transferFrom(source, 0, source.size());
                source.close();
                destination.close();
            }

            SnackbarHelper.showSnackbar(this, "PDF saved: " + fileName, SnackbarHelper.MessageType.SUCCESS);

            // Open the saved PDF
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && savedUri != null) {
                openFile(savedUri, "application/pdf", fileName);
            } else if (destPdf != null) {
                Uri fileUri = FileProvider.getUriForFile(this, getPackageName() + ".provider", destPdf);
                openFile(fileUri, "application/pdf", fileName);
            }
        } catch (Exception e) {
            SnackbarHelper.showSnackbar(this, "Failed to save PDF: " + e.getMessage(), SnackbarHelper.MessageType.ERROR);
        }
    }

    /**
     * Export report as CSV for spreadsheets
     */
    private void exportAsCSV() {
        if (currentReport == null) return;

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String displayTimestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
        String fileName = "vehicle_history_" + currentReport.getVin() + "_" + timestamp + ".csv";

        OutputStreamWriter writer = null;
        Uri savedUri = null;
        File savedFile = null;

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "text/csv");
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/OBDroid");

                savedUri = getContentResolver().insert(MediaStore.Files.getContentUri("external"), values);
                if (savedUri == null) {
                    throw new IOException("Failed to create MediaStore entry");
                }

                OutputStream outputStream = getContentResolver().openOutputStream(savedUri);
                if (outputStream == null) {
                    throw new IOException("Failed to open output stream");
                }
                writer = new OutputStreamWriter(outputStream);
            } else {
                File documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
                File obdroidDir = new File(documentsDir, "OBDroid");
                if (!obdroidDir.exists() && !obdroidDir.mkdirs()) {
                    throw new IOException("Unable to create export directory");
                }
                savedFile = new File(obdroidDir, fileName);
                writer = new OutputStreamWriter(new FileOutputStream(savedFile));
            }

            if (writer == null) {
                throw new IOException("Output stream writer was null");
            }

            // Write header
            writer.write("OBD-Droid Vehicle History Report," + displayTimestamp + "\n\n");

            // Vehicle info
            writer.write("Vehicle Information\n");
            writer.write("VIN," + currentReport.getVin() + "\n");
            if (currentReport.getYear() != null) writer.write("Year," + currentReport.getYear() + "\n");
            if (currentReport.getMake() != null) writer.write("Make," + currentReport.getMake() + "\n");
            if (currentReport.getModel() != null) writer.write("Model," + currentReport.getModel() + "\n");
            if (currentReport.getStyle() != null) writer.write("Style," + currentReport.getStyle() + "\n");

            // Score
            writer.write("\nAutoCheck Score\n");
            if (currentReport.getScore() != null) {
                writer.write("Score," + currentReport.getScore() + "\n");
                if (currentReport.getScoreRange() != null) {
                    writer.write("Range," + currentReport.getScoreRange().low + "-" + currentReport.getScoreRange().high + "\n");
                }
            }

            // Safety section
            writer.write("\nSafety Information\n");
            writer.write("Category,Value\n");
            if (currentReport.getTitleBrand() != null) writer.write("Title Brand," + currentReport.getTitleBrand() + "\n");
            if (currentReport.getAccidentDamage() != null) writer.write("Accident Damage," + currentReport.getAccidentDamage() + "\n");
            writer.write("Total Loss," + (currentReport.getTotalLoss() != null && currentReport.getTotalLoss() ? "Yes" : "No") + "\n");
            writer.write("Structural Damage," + (currentReport.getStructuralDamage() != null && currentReport.getStructuralDamage() ? "Yes" : "No") + "\n");
            writer.write("Airbag Deployed," + (currentReport.getAirbagDeployed() != null && currentReport.getAirbagDeployed() ? "Yes" : "No") + "\n");
            writer.write("Odometer Rollback," + (currentReport.getOdometerRollback() != null && currentReport.getOdometerRollback() ? "Yes" : "No") + "\n");

            // History events
            if (currentReport.getHistoryEvents() != null && !currentReport.getHistoryEvents().isEmpty()) {
                writer.write("\nHistory Events\n");
                writer.write("Date,Location,Odometer,Source,Details\n");
                for (AutoCheckReport.HistoryEvent event : currentReport.getHistoryEvents()) {
                    writer.write(String.format("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"\n",
                        event.date != null ? event.date : "",
                        event.location != null ? event.location.replace("\"", "\"\"") : "",
                        event.odometer != null ? event.odometer : "",
                        event.source != null ? event.source.replace("\"", "\"\"") : "",
                        event.details != null ? event.details.replace("\"", "\"\"") : ""
                    ));
                }
            }

            writer.flush();
            writer.close();

            SnackbarHelper.showSnackbar(this, "CSV saved: " + fileName, SnackbarHelper.MessageType.SUCCESS);

            // Open the saved CSV
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && savedUri != null) {
                openFile(savedUri, "text/csv", fileName);
            } else if (savedFile != null) {
                Uri fileUri = FileProvider.getUriForFile(this, getPackageName() + ".provider", savedFile);
                openFile(fileUri, "text/csv", fileName);
            }

        } catch (Exception e) {
            SnackbarHelper.showSnackbar(this, "Failed to export CSV: " + e.getMessage(), SnackbarHelper.MessageType.ERROR);
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
     * Export report as JSON for APIs and developers
     */
    private void exportAsJSON() {
        if (currentReport == null) return;

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String displayTimestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
        String fileName = "vehicle_history_" + currentReport.getVin() + "_" + timestamp + ".json";

        OutputStreamWriter writer = null;
        Uri savedUri = null;
        File savedFile = null;

        try {
            // Build JSON structure
            JSONObject root = new JSONObject();
            root.put("report_type", "OBD-Droid Vehicle History Report");
            root.put("generated_at", displayTimestamp);
            root.put("timestamp", new Date().getTime());

            JSONObject vehicle = new JSONObject();
            vehicle.put("vin", currentReport.getVin());
            if (currentReport.getYear() != null) vehicle.put("year", currentReport.getYear());
            if (currentReport.getMake() != null) vehicle.put("make", currentReport.getMake());
            if (currentReport.getModel() != null) vehicle.put("model", currentReport.getModel());
            if (currentReport.getStyle() != null) vehicle.put("style", currentReport.getStyle());
            if (currentReport.getEngine() != null) vehicle.put("engine", currentReport.getEngine());
            root.put("vehicle", vehicle);

            if (currentReport.getScore() != null) {
                JSONObject score = new JSONObject();
                score.put("value", currentReport.getScore());
                if (currentReport.getScoreRange() != null) {
                    score.put("range_low", currentReport.getScoreRange().low);
                    score.put("range_high", currentReport.getScoreRange().high);
                }
                root.put("autocheck_score", score);
            }

            JSONObject stats = new JSONObject();
            if (currentReport.getOwners() != null) stats.put("owners", currentReport.getOwners());
            if (currentReport.getLastOdometer() != null) stats.put("last_odometer", currentReport.getLastOdometer());
            if (currentReport.getServiceRecords() != null) stats.put("service_records", currentReport.getServiceRecords());
            if (currentReport.getVehicleAge() != null) stats.put("vehicle_age", currentReport.getVehicleAge());
            root.put("stats", stats);

            JSONObject safety = new JSONObject();
            if (currentReport.getTitleBrand() != null) safety.put("title_brand", currentReport.getTitleBrand());
            if (currentReport.getAccidentDamage() != null) safety.put("accident_damage", currentReport.getAccidentDamage());
            safety.put("total_loss", currentReport.getTotalLoss() != null && currentReport.getTotalLoss());
            safety.put("structural_damage", currentReport.getStructuralDamage() != null && currentReport.getStructuralDamage());
            safety.put("airbag_deployed", currentReport.getAirbagDeployed() != null && currentReport.getAirbagDeployed());
            safety.put("odometer_rollback", currentReport.getOdometerRollback() != null && currentReport.getOdometerRollback());
            root.put("safety", safety);

            if (currentReport.getHistoryEvents() != null && !currentReport.getHistoryEvents().isEmpty()) {
                JSONArray events = new JSONArray();
                for (AutoCheckReport.HistoryEvent event : currentReport.getHistoryEvents()) {
                    JSONObject eventObj = new JSONObject();
                    if (event.date != null) eventObj.put("date", event.date);
                    if (event.location != null) eventObj.put("location", event.location);
                    if (event.odometer != null) eventObj.put("odometer", event.odometer);
                    if (event.source != null) eventObj.put("source", event.source);
                    if (event.details != null) eventObj.put("details", event.details);
                    events.put(eventObj);
                }
                root.put("history_events", events);
            }

            // Save to file
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "application/json");
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/OBDroid");

                savedUri = getContentResolver().insert(MediaStore.Files.getContentUri("external"), values);
                if (savedUri == null) {
                    throw new IOException("Failed to create MediaStore entry");
                }

                OutputStream outputStream = getContentResolver().openOutputStream(savedUri);
                if (outputStream == null) {
                    throw new IOException("Failed to open output stream");
                }
                writer = new OutputStreamWriter(outputStream);
            } else {
                File documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
                File obdroidDir = new File(documentsDir, "OBDroid");
                if (!obdroidDir.exists() && !obdroidDir.mkdirs()) {
                    throw new IOException("Unable to create export directory");
                }
                savedFile = new File(obdroidDir, fileName);
                writer = new OutputStreamWriter(new FileOutputStream(savedFile));
            }

            if (writer == null) {
                throw new IOException("Output stream writer was null");
            }

            writer.write(root.toString(2)); // Pretty print with 2-space indent
            writer.flush();
            writer.close();

            SnackbarHelper.showSnackbar(this, "JSON saved: " + fileName, SnackbarHelper.MessageType.SUCCESS);

            // Open the saved JSON
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && savedUri != null) {
                openFile(savedUri, "application/json", fileName);
            } else if (savedFile != null) {
                Uri fileUri = FileProvider.getUriForFile(this, getPackageName() + ".provider", savedFile);
                openFile(fileUri, "application/json", fileName);
            }

        } catch (Exception e) {
            SnackbarHelper.showSnackbar(this, "Failed to export JSON: " + e.getMessage(), SnackbarHelper.MessageType.ERROR);
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
     * Helper method to open a saved file with the appropriate app
     */
    private void openFile(Uri fileUri, String mimeType, String fileName) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(fileUri, mimeType);
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NO_HISTORY);

            try {
                startActivity(intent);
            } catch (android.content.ActivityNotFoundException e) {
                // No app available to open this file type, show share sheet instead
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType(mimeType);
                shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(shareIntent, "Open " + fileName + " with..."));
            }
        } catch (Exception e) {
            SnackbarHelper.showSnackbar(this, "Saved to Documents/OBDroid", SnackbarHelper.MessageType.SUCCESS);
        }
    }

    private void showError(String error) {
        errorCard.setVisibility(View.VISIBLE);
        errorMessage.setText(error);
        reportContainer.setVisibility(View.GONE);
        isPdfAvailable = false;
        invalidateOptionsMenu();
    }

    /**
     * Save report to SharedPreferences cache keyed by VIN
     */
    private void saveReportToCache(AutoCheckReport report) {
        if (report == null || report.getVin() == null) {
            return;
        }

        try {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            Gson gson = new Gson();
            String json = gson.toJson(report);

            String cacheKey = PREFS_KEY_PREFIX + report.getVin();
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(cacheKey, json);
            editor.putLong(cacheKey + "_time", System.currentTimeMillis());

            // Also cache the PDF file path if available
            if (currentPdfFilePath != null && !currentPdfFilePath.isEmpty()) {
                editor.putString(cacheKey + "_pdf", currentPdfFilePath);
            }

            editor.apply();
        } catch (Exception e) {
            // Silent fail - caching is not critical
            e.printStackTrace();
        }
    }

    /**
     * Load cached report for current VIN if available
     */
    private void loadCachedReport() {
        if (currentVin == null || currentVin.isEmpty()) {
            return;
        }

        try {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            String cacheKey = PREFS_KEY_PREFIX + currentVin;
            String json = prefs.getString(cacheKey, null);

            if (json != null && !json.isEmpty()) {
                Gson gson = new Gson();
                AutoCheckReport report = gson.fromJson(json, AutoCheckReport.class);

                if (report != null) {
                    // Display cached report
                    displayReport(report);

                    // Restore PDF file path if available
                    String cachedPdfPath = prefs.getString(cacheKey + "_pdf", null);
                    if (cachedPdfPath != null && !cachedPdfPath.isEmpty()) {
                        // Check if the PDF file still exists
                        File pdfFile = new File(cachedPdfPath);
                        if (pdfFile.exists()) {
                            currentPdfFilePath = cachedPdfPath;
                            isPdfAvailable = true;
                    invalidateOptionsMenu();
                        } else {
                            // PDF was deleted, clear from cache
                            prefs.edit().remove(cacheKey + "_pdf").apply();
                        }
                    } else {
                        // No cached PDF path - search for existing PDF files for this VIN
                        File pdfDir = new File(getExternalFilesDir(null), "autocheck_reports");
                        if (pdfDir.exists() && pdfDir.isDirectory()) {
                            File[] pdfFiles = pdfDir.listFiles((dir, name) ->
                                name.startsWith("autocheck_" + currentVin) && name.endsWith(".pdf"));

                            if (pdfFiles != null && pdfFiles.length > 0) {
                                // Find most recent PDF
                                File mostRecentPdf = pdfFiles[0];
                                for (File pdf : pdfFiles) {
                                    if (pdf.lastModified() > mostRecentPdf.lastModified()) {
                                        mostRecentPdf = pdf;
                                    }
                                }

                                currentPdfFilePath = mostRecentPdf.getAbsolutePath();
                                isPdfAvailable = true;
                    invalidateOptionsMenu();

                                // Save to cache for next time
                                prefs.edit().putString(cacheKey + "_pdf", currentPdfFilePath).apply();
                            }
                        }
                    }

                    // Silently load cached report - no notification needed
                }
            }
        } catch (Exception e) {
            // Silent fail - if cache load fails, user can generate new report
            e.printStackTrace();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (autoCheckService != null) {
            autoCheckService.shutdown();
        }
    }
}
