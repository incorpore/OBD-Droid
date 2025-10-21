package com.obddroid.ui.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.obddroid.R;
import com.obddroid.ui.components.VehicleInfoFooter;
import com.obddroid.utils.SnackbarHelper;
import com.obddroid.vehicle.VehicleManager;

import io.github.vindecoder.android.VINDecoderAndroid;
import io.github.vindecoder.nhtsa.RecallRecord;
import io.github.vindecoder.nhtsa.VehicleData;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Screen for searching and displaying NHTSA safety recalls.
 * Now integrated with the nhtsa-vin-decoder library for real-time recall lookups.
 */
public class RecallActivity extends AppCompatActivity {

    private TextInputLayout vinInputLayout;
    private TextInputEditText vinInput;
    private Button searchButton;
    private TextView statusText;
    private ProgressBar loadingIndicator;
    private LinearLayout resultsContainer;
    private VehicleInfoFooter vehicleInfoFooter;
    private View footerOverlay;

    private VINDecoderAndroid vinDecoder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recalls);

        // Set navigation bar to black to match footer
        if (getWindow() != null) {
            getWindow().setNavigationBarColor(0xFF000000); // Black
        }

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.safety_recalls);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // Initialize VIN decoder
        vinDecoder = new VINDecoderAndroid(this);

        bindViews();
        setupFooterOverlay();
        prefillVin();

        // Update status text to show API is now available
        statusText.setText("Enter a VIN to check for safety recalls");
        statusText.setVisibility(View.VISIBLE);

        searchButton.setOnClickListener(v -> performRecallSearch());
    }

    private void bindViews() {
        vinInputLayout = findViewById(R.id.recalls_vin_input_layout);
        vinInput = findViewById(R.id.recalls_vin_input);
        searchButton = findViewById(R.id.recalls_search_button);
        statusText = findViewById(R.id.recalls_status_text);
        loadingIndicator = findViewById(R.id.recalls_loading_indicator);
        resultsContainer = findViewById(R.id.recalls_results_container);
        vehicleInfoFooter = findViewById(R.id.vehicle_footer);
        footerOverlay = findViewById(R.id.footer_overlay);
    }

    private void setupFooterOverlay() {
        if (vehicleInfoFooter != null && footerOverlay != null) {
            vehicleInfoFooter.setOverlayView(footerOverlay);
        }
    }

    private void prefillVin() {
        try {
            VehicleManager vm = VehicleManager.getInstance();
            String vin = vm.getCurrentVIN();
            if (!TextUtils.isEmpty(vin) && vinInput != null) {
                vinInput.setText(vin);
                vinInput.setSelection(vin.length());
            }
        } catch (Exception ignored) {
            // VehicleManager may not be initialized yet
        }
    }

    private void performRecallSearch() {
        String vin = vinInput.getText() != null ? vinInput.getText().toString().trim() : "";

        if (TextUtils.isEmpty(vin)) {
            SnackbarHelper.showError(this, "Please enter a VIN");
            return;
        }

        if (vin.length() != 17) {
            SnackbarHelper.showError(this, "VIN must be 17 characters");
            return;
        }

        // Show loading state
        searchButton.setEnabled(false);
        loadingIndicator.setVisibility(View.VISIBLE);
        statusText.setText("Searching for recalls...");
        resultsContainer.removeAllViews();
        resultsContainer.setVisibility(View.GONE);

        // Decode VIN with recalls
        vinDecoder.decodeWithRecalls(vin, new VINDecoderAndroid.DecodeCallback() {
            @Override
            public void onSuccess(VehicleData vehicleData) {
                runOnUiThread(() -> {
                    searchButton.setEnabled(true);
                    loadingIndicator.setVisibility(View.GONE);

                    if (vehicleData.getRecalls() != null && !vehicleData.getRecalls().isEmpty()) {
                        displayRecalls(vehicleData.getRecalls(), vehicleData);
                    } else {
                        statusText.setText("No recalls found for this vehicle");
                        resultsContainer.setVisibility(View.GONE);
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    searchButton.setEnabled(true);
                    loadingIndicator.setVisibility(View.GONE);
                    statusText.setText("Error searching recalls: " + error);
                    resultsContainer.setVisibility(View.GONE);
                    SnackbarHelper.showError(RecallActivity.this, "Failed to search recalls");
                });
            }
        });
    }

    private void displayRecalls(List<RecallRecord> recalls, VehicleData vehicleData) {
        statusText.setText(String.format(Locale.US,
            "Found %d recall%s for %s %s %s",
            recalls.size(),
            recalls.size() == 1 ? "" : "s",
            vehicleData.getModelYear() != null ? vehicleData.getModelYear() : "",
            vehicleData.getMake() != null ? vehicleData.getMake() : "",
            vehicleData.getModel() != null ? vehicleData.getModel() : ""));

        resultsContainer.removeAllViews();

        for (RecallRecord recall : recalls) {
            View recallView = createRecallView(recall);
            resultsContainer.addView(recallView);
        }

        resultsContainer.setVisibility(View.VISIBLE);
    }

    private View createRecallView(RecallRecord recall) {
        View view = LayoutInflater.from(this).inflate(R.layout.item_recall, resultsContainer, false);

        TextView campaignTitle = view.findViewById(R.id.recall_campaign_title);
        TextView component = view.findViewById(R.id.recall_component);
        TextView makeModel = view.findViewById(R.id.recall_make_model);
        TextView reportDate = view.findViewById(R.id.recall_report_date);
        TextView summary = view.findViewById(R.id.recall_summary);
        TextView remedy = view.findViewById(R.id.recall_remedy);
        TextView openLink = view.findViewById(R.id.recall_open_link);

        // Set campaign title
        String campaignNumber = recall.getNhtsaCampaignNumber();
        if (!TextUtils.isEmpty(campaignNumber)) {
            campaignTitle.setText("Campaign #" + campaignNumber);
        }

        // Set component
        if (!TextUtils.isEmpty(recall.getComponent())) {
            component.setText("Component: " + recall.getComponent());
            component.setVisibility(View.VISIBLE);
        } else {
            component.setVisibility(View.GONE);
        }

        // Set make/model
        String makeModelText = String.format("%s %s %s",
            recall.getModelYear() != null ? recall.getModelYear() : "",
            recall.getMake() != null ? recall.getMake() : "",
            recall.getModel() != null ? recall.getModel() : "").trim();
        if (!TextUtils.isEmpty(makeModelText)) {
            makeModel.setText(makeModelText);
            makeModel.setVisibility(View.VISIBLE);
        } else {
            makeModel.setVisibility(View.GONE);
        }

        // Set report date
        if (!TextUtils.isEmpty(recall.getReportReceivedDate())) {
            try {
                String formattedDate = formatDate(recall.getReportReceivedDate());
                reportDate.setText("Reported: " + formattedDate);
                reportDate.setVisibility(View.VISIBLE);
            } catch (Exception e) {
                reportDate.setVisibility(View.GONE);
            }
        } else {
            reportDate.setVisibility(View.GONE);
        }

        // Set summary
        if (!TextUtils.isEmpty(recall.getSummary())) {
            summary.setText(recall.getSummary());
            summary.setVisibility(View.VISIBLE);
            view.findViewById(R.id.recall_summary_heading).setVisibility(View.VISIBLE);
        } else {
            summary.setVisibility(View.GONE);
            view.findViewById(R.id.recall_summary_heading).setVisibility(View.GONE);
        }

        // Set remedy
        if (!TextUtils.isEmpty(recall.getRemedy())) {
            remedy.setText(recall.getRemedy());
            remedy.setVisibility(View.VISIBLE);
            view.findViewById(R.id.recall_remedy_heading).setVisibility(View.VISIBLE);
        } else {
            remedy.setVisibility(View.GONE);
            view.findViewById(R.id.recall_remedy_heading).setVisibility(View.GONE);
        }

        // Set click to open in browser
        openLink.setOnClickListener(v -> {
            String url = "https://www.nhtsa.gov/recalls?nhtsaId=" + campaignNumber;
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(intent);
            } catch (Exception e) {
                SnackbarHelper.showError(this, "Unable to open recall details");
            }
        });

        return view;
    }

    private String formatDate(String dateString) {
        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.US);
            SimpleDateFormat outputFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
            Date date = inputFormat.parse(dateString);
            return outputFormat.format(date);
        } catch (ParseException e) {
            // Try another format
            try {
                SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                SimpleDateFormat outputFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
                Date date = inputFormat.parse(dateString);
                return outputFormat.format(date);
            } catch (ParseException e2) {
                return dateString;
            }
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
