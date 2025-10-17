package com.obddroid.ui.activities;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.FileProvider;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.obddroid.R;
import com.obddroid.services.AutoCheckService;
import com.obddroid.vehicle.AutoCheckReport;
import com.obddroid.vehicle.VehicleManager;
import com.obddroid.ui.components.VehicleInfoFooter;

import java.io.File;

/**
 * World-Class AutoCheck Vehicle History Activity
 * Features sophisticated data visualization and premium UI
 */
public class AutoCheckActivity extends AppCompatActivity {

    // Input views
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

    // PDF button
    private FloatingActionButton pdfFab;
    private String currentPdfFilePath;

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
        pdfFab.setOnClickListener(v -> openPdf());

        // Check API health on startup
        checkApiHealth();
    }

    private void findViews() {
        // Input views
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

        // PDF button
        pdfFab = findViewById(R.id.pdf_fab);
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
            Toast.makeText(this, "No VIN available", Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentVin.length() != 17) {
            Toast.makeText(this, "Invalid VIN length", Toast.LENGTH_SHORT).show();
            return;
        }

        String vin = currentVin.toUpperCase();

        // Hide previous results and errors
        errorCard.setVisibility(View.GONE);
        reportContainer.setVisibility(View.GONE);
        pdfFab.setVisibility(View.GONE);

        // Show loading
        checkHistoryButton.setEnabled(false);
        loadingContainer.setVisibility(View.VISIBLE);

        // Fetch report with PDF
        autoCheckService.fetchReportWithPdf(vin, new AutoCheckService.AutoCheckPdfCallback() {
            @Override
            public void onSuccess(AutoCheckReport report, String pdfFilePath) {
                // Hide loading
                loadingContainer.setVisibility(View.GONE);
                checkHistoryButton.setEnabled(true);

                // Store PDF file path
                currentPdfFilePath = pdfFilePath;

                // Display report with world-class visualization
                displayReport(report);

                // Show PDF button if PDF was generated
                if (pdfFilePath != null && !pdfFilePath.isEmpty()) {
                    pdfFab.setVisibility(View.VISIBLE);
                    Toast.makeText(AutoCheckActivity.this,
                        "Report loaded! PDF available",
                        Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(AutoCheckActivity.this,
                        "Report loaded successfully!",
                        Toast.LENGTH_SHORT).show();
                }
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
        // Hide error, show report
        errorCard.setVisibility(View.GONE);
        reportContainer.setVisibility(View.VISIBLE);

        // ═══════════════════════════════════════
        // HERO SCORE SECTION
        // ═══════════════════════════════════════
        displayHeroSection(report);

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
            Toast.makeText(this, "PDF not available", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            File pdfFile = new File(currentPdfFilePath);
            if (!pdfFile.exists()) {
                Toast.makeText(this, "PDF file not found", Toast.LENGTH_SHORT).show();
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
            Toast.makeText(this, "Error opening PDF: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showError(String error) {
        errorCard.setVisibility(View.VISIBLE);
        errorMessage.setText(error);
        reportContainer.setVisibility(View.GONE);
        pdfFab.setVisibility(View.GONE);
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
