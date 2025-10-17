package com.obddroid.ui.activities;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.obddroid.R;
import com.obddroid.services.AutoCheckService;
import com.obddroid.vehicle.AutoCheckReport;
import com.obddroid.vehicle.VehicleManager;
import com.obddroid.ui.components.VehicleInfoFooter;

/**
 * Activity for checking vehicle history via AutoCheck API
 */
public class AutoCheckActivity extends AppCompatActivity {

    private EditText vinInput;
    private Button checkHistoryButton;
    private ProgressBar loadingSpinner;
    private TextView loadingText;
    private CardView errorCard;
    private TextView errorMessage;
    private LinearLayout reportContainer;
    private TextView vehicleName;
    private TextView vehicleVin;
    private TextView autoCheckScore;
    private TextView reportDetails;
    private VehicleInfoFooter vehicleInfoFooter;

    private AutoCheckService autoCheckService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_autocheck);

        // Initialize service
        autoCheckService = new AutoCheckService(this);

        // Find views
        vinInput = findViewById(R.id.vin_input);
        checkHistoryButton = findViewById(R.id.check_history_button);
        loadingSpinner = findViewById(R.id.loading_spinner);
        loadingText = findViewById(R.id.loading_text);
        errorCard = findViewById(R.id.error_card);
        errorMessage = findViewById(R.id.error_message);
        reportContainer = findViewById(R.id.report_container);
        vehicleName = findViewById(R.id.vehicle_name);
        vehicleVin = findViewById(R.id.vehicle_vin);
        autoCheckScore = findViewById(R.id.autocheck_score);
        reportDetails = findViewById(R.id.report_details);

        // Wire up footer overlay
        setupFooterOverlay();

        // Pre-fill VIN if available
        prefillVIN();

        // Set button click listener
        checkHistoryButton.setOnClickListener(v -> fetchVehicleHistory());

        // Check API health on startup
        checkApiHealth();
    }

    /**
     * Wire up footer overlay to close footer when clicking outside
     */
    private void setupFooterOverlay() {
        vehicleInfoFooter = findViewById(R.id.vehicle_footer);
        View overlay = findViewById(R.id.footer_overlay);

        if (vehicleInfoFooter != null && overlay != null) {
            vehicleInfoFooter.setOverlayView(overlay);
        }
    }

    /**
     * Pre-fill VIN from VehicleManager if available
     */
    private void prefillVIN() {
        try {
            VehicleManager vehicleManager = VehicleManager.getInstance();
            String vin = vehicleManager.getCurrentVIN();

            if (vin != null && !vin.isEmpty()) {
                vinInput.setText(vin);
            }
        } catch (Exception e) {
            // Silently ignore if VIN not available
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
        String vin = vinInput.getText().toString().trim().toUpperCase();

        // Validate VIN
        if (TextUtils.isEmpty(vin)) {
            Toast.makeText(this, "Please enter a VIN", Toast.LENGTH_SHORT).show();
            return;
        }

        if (vin.length() != 17) {
            Toast.makeText(this, "VIN must be exactly 17 characters", Toast.LENGTH_SHORT).show();
            return;
        }

        // Hide previous results and errors
        errorCard.setVisibility(View.GONE);
        reportContainer.setVisibility(View.GONE);

        // Show loading
        checkHistoryButton.setEnabled(false);
        loadingSpinner.setVisibility(View.VISIBLE);
        loadingText.setVisibility(View.VISIBLE);

        // Fetch report
        autoCheckService.fetchReport(vin, new AutoCheckService.AutoCheckCallback() {
            @Override
            public void onSuccess(AutoCheckReport report) {
                // Hide loading
                loadingSpinner.setVisibility(View.GONE);
                loadingText.setVisibility(View.GONE);
                checkHistoryButton.setEnabled(true);

                // Display report
                displayReport(report);
            }

            @Override
            public void onError(String error) {
                // Hide loading
                loadingSpinner.setVisibility(View.GONE);
                loadingText.setVisibility(View.GONE);
                checkHistoryButton.setEnabled(true);

                // Show error
                showError(error);
            }
        });
    }

    private void displayReport(AutoCheckReport report) {
        // Hide error, show report
        errorCard.setVisibility(View.GONE);
        reportContainer.setVisibility(View.VISIBLE);

        // Vehicle info
        vehicleName.setText(report.getVehicleName());
        vehicleVin.setText("VIN: " + report.getVin());

        // AutoCheck score
        if (report.getScore() != null) {
            autoCheckScore.setText(report.getScoreSummary());

            // Color code the score
            int score = report.getScore();
            if (score >= 80) {
                autoCheckScore.setTextColor(0xFF4CAF50); // Green
            } else if (score >= 60) {
                autoCheckScore.setTextColor(0xFFFFC107); // Yellow
            } else {
                autoCheckScore.setTextColor(0xFFF44336); // Red
            }
        } else {
            autoCheckScore.setText("N/A");
        }

        // Build details text
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

        details.append("\n");

        if (report.getOwners() != null) {
            details.append("Owners: ").append(report.getOwners()).append("\n");
        }

        if (report.getLastOdometer() != null) {
            details.append("Last Odometer: ").append(String.format("%,d", report.getLastOdometer())).append(" miles\n");
        }

        if (report.getTitleBrand() != null) {
            details.append("Title: ").append(report.getTitleBrand()).append("\n");
        }

        if (report.getAccidentDamage() != null) {
            details.append("Accidents: ").append(report.getAccidentDamage()).append("\n");
        }

        if (report.getRecalls() != null) {
            details.append("Recalls: ").append(report.getRecalls()).append("\n");
        }

        if (report.getServiceRecords() != null) {
            details.append("Service Records: ").append(report.getServiceRecords()).append("\n");
        }

        // Warning flags
        details.append("\n--- Safety Indicators ---\n");

        if (report.getStructuralDamage() != null && report.getStructuralDamage()) {
            details.append("⚠️  Structural Damage Reported\n");
        }

        if (report.getAirbagDeployed() != null && report.getAirbagDeployed()) {
            details.append("⚠️  Airbag Deployed\n");
        }

        if (report.getOdometerRollback() != null && report.getOdometerRollback()) {
            details.append("⚠️  Odometer Rollback\n");
        }

        if (!report.hasCleanTitle()) {
            details.append("⚠️  Title Issue\n");
        }

        if (!report.hasAccidents() && report.hasCleanTitle()) {
            details.append("✅ No major issues found\n");
        }

        reportDetails.setText(details.toString());

        Toast.makeText(this, "Vehicle history loaded successfully!", Toast.LENGTH_SHORT).show();
    }

    private void showError(String error) {
        errorCard.setVisibility(View.VISIBLE);
        errorMessage.setText(error);
        reportContainer.setVisibility(View.GONE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (autoCheckService != null) {
            autoCheckService.shutdown();
        }
    }
}
