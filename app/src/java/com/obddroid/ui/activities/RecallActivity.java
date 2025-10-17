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

import java.util.List;
import java.util.Locale;

import io.github.nhtsarecalls.RecallRecord;
import io.github.nhtsarecalls.android.NHTSARecallClientAndroid;

/**
 * Screen for querying and displaying NHTSA safety recalls.
 */
public class RecallActivity extends AppCompatActivity {

    private static final String NHTSA_CAMPAIGN_URL = "https://www.nhtsa.gov/recalls?nhtsaId=%s";

    private TextInputLayout vinInputLayout;
    private TextInputEditText vinInput;
    private Button searchButton;
    private ProgressBar loadingIndicator;
    private TextView statusText;
    private LinearLayout resultsContainer;
    private VehicleInfoFooter vehicleInfoFooter;
    private View footerOverlay;

    private NHTSARecallClientAndroid recallClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recalls);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.safety_recalls);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        recallClient = new NHTSARecallClientAndroid();

        bindViews();
        setupFooterOverlay();
        prefillVin();

        statusText.setText(R.string.recalls_intro);
        statusText.setVisibility(View.VISIBLE);

        searchButton.setOnClickListener(v -> fetchRecalls());
    }

    private void bindViews() {
        vinInputLayout = findViewById(R.id.recalls_vin_input_layout);
        vinInput = findViewById(R.id.recalls_vin_input);
        searchButton = findViewById(R.id.recalls_search_button);
        loadingIndicator = findViewById(R.id.recalls_loading_indicator);
        statusText = findViewById(R.id.recalls_status_text);
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

    private void fetchRecalls() {
        if (vinInputLayout != null) {
            vinInputLayout.setError(null);
        }

        String vin = "";
        if (vinInput != null && vinInput.getText() != null) {
            vin = vinInput.getText().toString().trim().toUpperCase(Locale.US);
        }

        if (vin.length() != 17) {
            if (vinInputLayout != null) {
                vinInputLayout.setError(getString(R.string.recalls_invalid_vin));
            }
            SnackbarHelper.showWarning(this, getString(R.string.recalls_invalid_vin));
            return;
        }

        resultsContainer.removeAllViews();
        showLoading(true, getString(R.string.recalls_loading));

        recallClient.getRecallsByVin(vin, new NHTSARecallClientAndroid.RecallCallback() {
            @Override
            public void onSuccess(List<RecallRecord> recalls) {
                showLoading(false, null);
                renderResults(recalls);
            }

            @Override
            public void onError(Throwable throwable) {
                showLoading(false, null);
                showError(getString(R.string.recalls_error_generic));
            }
        });
    }

    private void showLoading(boolean loading, String message) {
        loadingIndicator.setVisibility(loading ? View.VISIBLE : View.GONE);
        searchButton.setEnabled(!loading);
        if (!TextUtils.isEmpty(message)) {
            statusText.setText(message);
            statusText.setVisibility(View.VISIBLE);
        } else if (loading) {
            statusText.setVisibility(View.VISIBLE);
        }
    }

    private void renderResults(List<RecallRecord> records) {
        if (records == null || records.isEmpty()) {
            resultsContainer.setVisibility(View.GONE);
            statusText.setText(R.string.recalls_empty_state);
            statusText.setVisibility(View.VISIBLE);
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        resultsContainer.setVisibility(View.VISIBLE);
        statusText.setVisibility(View.GONE);

        for (RecallRecord record : records) {
            View itemView = inflater.inflate(R.layout.item_recall, resultsContainer, false);
            bindRecallRecord(itemView, record);
            resultsContainer.addView(itemView);
        }
    }

    private void bindRecallRecord(View itemView, RecallRecord record) {
        TextView campaignTitle = itemView.findViewById(R.id.recall_campaign_title);
        TextView componentView = itemView.findViewById(R.id.recall_component);
        TextView makeModelView = itemView.findViewById(R.id.recall_make_model);
        TextView reportDateView = itemView.findViewById(R.id.recall_report_date);
        TextView summaryHeading = itemView.findViewById(R.id.recall_summary_heading);
        TextView summaryView = itemView.findViewById(R.id.recall_summary);
        TextView remedyHeading = itemView.findViewById(R.id.recall_remedy_heading);
        TextView remedyView = itemView.findViewById(R.id.recall_remedy);
        TextView openLinkView = itemView.findViewById(R.id.recall_open_link);

        String campaignNumber = firstNonEmpty(record.getNhtsaCampaignNumber(), record.getRecallNumber());
        if (!TextUtils.isEmpty(campaignNumber)) {
            campaignTitle.setText(getString(R.string.recalls_campaign_label, campaignNumber));
        } else if (!TextUtils.isEmpty(record.getManufacturer())) {
            campaignTitle.setText(record.getManufacturer());
        } else {
            campaignTitle.setText(getString(R.string.safety_recalls));
        }

        String component = record.getComponent();
        if (!TextUtils.isEmpty(component)) {
            componentView.setText(getString(R.string.recalls_component_label, component));
            componentView.setVisibility(View.VISIBLE);
        } else {
            componentView.setVisibility(View.GONE);
        }

        String makeModel = buildMakeModel(record);
        if (!TextUtils.isEmpty(makeModel)) {
            makeModelView.setText(makeModel);
            makeModelView.setVisibility(View.VISIBLE);
        } else {
            makeModelView.setVisibility(View.GONE);
        }

        String reportDate = record.getReportReceivedDate();
        if (!TextUtils.isEmpty(reportDate)) {
            reportDateView.setText(getString(R.string.recalls_report_date, reportDate));
            reportDateView.setVisibility(View.VISIBLE);
        } else {
            reportDateView.setVisibility(View.GONE);
        }

        String summary = record.getSummary();
        summaryView.setText(!TextUtils.isEmpty(summary) ? summary : getString(R.string.recalls_not_available));
        summaryHeading.setVisibility(View.VISIBLE);
        summaryView.setVisibility(View.VISIBLE);

        String remedy = record.getRemedy();
        remedyView.setText(!TextUtils.isEmpty(remedy) ? remedy : getString(R.string.recalls_not_available));
        remedyHeading.setVisibility(View.VISIBLE);
        remedyView.setVisibility(View.VISIBLE);

        if (!TextUtils.isEmpty(campaignNumber)) {
            String url = String.format(Locale.US, NHTSA_CAMPAIGN_URL, campaignNumber);
            openLinkView.setVisibility(View.VISIBLE);
            itemView.setOnClickListener(v -> openCampaignUrl(url));
        } else {
            openLinkView.setVisibility(View.GONE);
            itemView.setOnClickListener(null);
        }
    }

    private String buildMakeModel(RecallRecord record) {
        StringBuilder builder = new StringBuilder();
        if (!TextUtils.isEmpty(record.getModelYear())) {
            builder.append(record.getModelYear()).append(' ');
        }
        if (!TextUtils.isEmpty(record.getMake())) {
            builder.append(record.getMake()).append(' ');
        }
        if (!TextUtils.isEmpty(record.getModel())) {
            builder.append(record.getModel());
        }
        String result = builder.toString().trim();
        return result.isEmpty() ? null : result;
    }

    private void openCampaignUrl(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (Exception e) {
            SnackbarHelper.showError(this, getString(R.string.recalls_error_generic));
        }
    }

    private void showError(String message) {
        resultsContainer.setVisibility(View.GONE);
        statusText.setText(message);
        statusText.setVisibility(View.VISIBLE);
        SnackbarHelper.showError(this, message);
    }

    private String firstNonEmpty(String first, String second) {
        if (!TextUtils.isEmpty(first)) {
            return first;
        }
        if (!TextUtils.isEmpty(second)) {
            return second;
        }
        return null;
    }

    @Override
    protected void onDestroy() {
        if (recallClient != null) {
            recallClient.shutdown();
        }
        super.onDestroy();
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
