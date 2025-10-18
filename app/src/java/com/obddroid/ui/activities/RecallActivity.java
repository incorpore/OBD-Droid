package com.obddroid.ui.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.obddroid.R;
import com.obddroid.ui.components.VehicleInfoFooter;
import com.obddroid.utils.SnackbarHelper;
import com.obddroid.vehicle.VehicleManager;

/**
 * Screen for directing users to NHTSA safety recalls website.
 */
public class RecallActivity extends AppCompatActivity {

    private TextInputLayout vinInputLayout;
    private TextInputEditText vinInput;
    private Button searchButton;
    private TextView statusText;
    private VehicleInfoFooter vehicleInfoFooter;
    private View footerOverlay;

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

        bindViews();
        setupFooterOverlay();
        prefillVin();

        // Show message about NHTSA API discontinuation
        statusText.setText("NHTSA has discontinued their public recalls API.\n\n" +
                "Please visit the official NHTSA website to check for safety recalls:\n\n" +
                "https://www.nhtsa.gov/recalls");
        statusText.setVisibility(View.VISIBLE);

        searchButton.setOnClickListener(v -> openNHTSAWebsite());
    }

    private void bindViews() {
        vinInputLayout = findViewById(R.id.recalls_vin_input_layout);
        vinInput = findViewById(R.id.recalls_vin_input);
        searchButton = findViewById(R.id.recalls_search_button);
        statusText = findViewById(R.id.recalls_status_text);
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

    private void openNHTSAWebsite() {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.nhtsa.gov/recalls"));
            startActivity(intent);
        } catch (Exception e) {
            SnackbarHelper.showError(this, "Unable to open website");
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
