package com.obddroid.ui.activities;

import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.obddroid.R;
import com.obddroid.services.VehicleManager;
import com.obddroid.ui.components.VehicleInfoFooter;
import com.obddroid.utils.VehicleData;

/**
 * Activity for displaying comprehensive decoded VIN information.
 * Shows all available NHTSA vPIC fields in an organized, scrollable view.
 */
public class VehicleInfoActivity extends AppCompatActivity {

    private static final String TAG = "VehicleInfoActivity";

    // UI Components
    private LinearLayout contentContainer;
    private View emptyState;
    private View footerOverlay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vehicle_info);

        // Set navigation bar to black to match footer
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setNavigationBarColor(0xFF000000);
        }

        // Set up action bar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Vehicle Information");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // Initialize views
        contentContainer = findViewById(R.id.vehicle_info_content);
        emptyState = findViewById(R.id.empty_state);
        footerOverlay = findViewById(R.id.footer_overlay);

        // Load and display vehicle data
        loadVehicleData();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Load vehicle data from VehicleManager and populate the UI
     */
    private void loadVehicleData() {
        try {
            VehicleManager vm = VehicleManager.getInstance();
            VehicleData vehicleData = vm.getCurrentVehicleData();
            String vin = vm.getCurrentVIN();

            if (vehicleData == null || TextUtils.isEmpty(vin)) {
                showEmptyState();
                return;
            }

            // Hide empty state and show content
            if (emptyState != null) emptyState.setVisibility(View.GONE);
            if (contentContainer != null) contentContainer.setVisibility(View.VISIBLE);

            // Populate all sections
            populateVehicleIdentification(vin, vehicleData);
            populateBodyStructure(vehicleData);
            populateEngine(vehicleData);
            populateDrivetrain(vehicleData);
            populateDimensions(vehicleData);
            populateSafety(vehicleData);
            populateAdvancedFeatures(vehicleData);
            populateManufacturing(vehicleData);
            populateWeight(vehicleData);
            populatePricing(vehicleData);

        } catch (Exception e) {
            Log.e(TAG, "Error loading vehicle data", e);
            showEmptyState();
        }
    }

    private void showEmptyState() {
        if (emptyState != null) emptyState.setVisibility(View.VISIBLE);
        if (contentContainer != null) contentContainer.setVisibility(View.GONE);
    }

    private void populateVehicleIdentification(String vin, VehicleData data) {
        addSection("Vehicle Identification");
        addDetailRow("VIN", vin);
        if (!TextUtils.isEmpty(data.manufacturer)) addDetailRow("Manufacturer", data.manufacturer);
        if (!TextUtils.isEmpty(data.model)) addDetailRow("Model", data.model);
        if (!TextUtils.isEmpty(data.modelYear)) addDetailRow("Year", data.modelYear);
        if (!TextUtils.isEmpty(data.series)) addDetailRow("Series", data.series);
        if (!TextUtils.isEmpty(data.trim)) addDetailRow("Trim", data.trim);
    }

    private void populateBodyStructure(VehicleData data) {
        boolean hasData = !TextUtils.isEmpty(data.bodyClass) ||
                         !TextUtils.isEmpty(data.vehicleType) ||
                         !TextUtils.isEmpty(data.doors) ||
                         !TextUtils.isEmpty(data.wheelBase);

        if (!hasData) return;

        addSection("Body & Structure");
        if (!TextUtils.isEmpty(data.bodyClass)) addDetailRow("Body Class", data.bodyClass);
        if (!TextUtils.isEmpty(data.vehicleType)) addDetailRow("Vehicle Type", data.vehicleType);
        if (!TextUtils.isEmpty(data.doors)) addDetailRow("Doors", data.doors);
        if (!TextUtils.isEmpty(data.wheelBase)) addDetailRow("Wheelbase", data.wheelBase + " inches");
    }

    private void populateEngine(VehicleData data) {
        boolean hasData = !TextUtils.isEmpty(data.engineModel) ||
                         !TextUtils.isEmpty(data.displacementL) ||
                         !TextUtils.isEmpty(data.engineCylinders);

        if (!hasData) return;

        addSection("Engine & Performance");
        if (!TextUtils.isEmpty(data.engineModel)) addDetailRow("Engine Model", data.engineModel);
        if (!TextUtils.isEmpty(data.displacementL)) {
            String displacement = data.displacementL;
            if (!TextUtils.isEmpty(data.displacementCC)) {
                displacement += " (" + data.displacementCC + " cc)";
            }
            addDetailRow("Displacement", displacement);
        }
        if (!TextUtils.isEmpty(data.engineCylinders)) addDetailRow("Cylinders", data.engineCylinders);
        if (!TextUtils.isEmpty(data.engineBrakeHp)) addDetailRow("Horsepower", data.engineBrakeHp + " hp");
        if (!TextUtils.isEmpty(data.topSpeed)) addDetailRow("Top Speed", data.topSpeed + " mph");
        if (!TextUtils.isEmpty(data.fuelTypePrimary)) addDetailRow("Fuel Type", data.fuelTypePrimary);
        if (!TextUtils.isEmpty(data.fuelDeliveryType)) addDetailRow("Fuel Injection", data.fuelDeliveryType);
        if (!TextUtils.isEmpty(data.valveTrainDesign)) addDetailRow("Valvetrain", data.valveTrainDesign);
        if (!TextUtils.isEmpty(data.coolingType)) addDetailRow("Cooling", data.coolingType);
        if (!TextUtils.isEmpty(data.engineManufacturer)) addDetailRow("Engine Mfr", data.engineManufacturer);
    }

    private void populateDrivetrain(VehicleData data) {
        boolean hasData = !TextUtils.isEmpty(data.transmissionStyle) ||
                         !TextUtils.isEmpty(data.driveType);

        if (!hasData) return;

        addSection("Drivetrain");
        if (!TextUtils.isEmpty(data.transmissionStyle)) addDetailRow("Transmission", data.transmissionStyle);
        if (!TextUtils.isEmpty(data.transmissionSpeeds)) addDetailRow("Speeds", data.transmissionSpeeds);
        if (!TextUtils.isEmpty(data.driveType)) addDetailRow("Drive Type", data.driveType);
        if (!TextUtils.isEmpty(data.axles)) addDetailRow("Axles", data.axles);
        if (!TextUtils.isEmpty(data.steeringLocation)) addDetailRow("Steering", data.steeringLocation);
    }

    private void populateDimensions(VehicleData data) {
        boolean hasData = !TextUtils.isEmpty(data.numberOfSeats) ||
                         !TextUtils.isEmpty(data.wheelSizeFront);

        if (!hasData) return;

        addSection("Dimensions");
        if (!TextUtils.isEmpty(data.numberOfSeats)) addDetailRow("Seats", data.numberOfSeats);
        if (!TextUtils.isEmpty(data.numberOfSeatRows)) addDetailRow("Seat Rows", data.numberOfSeatRows);
        if (!TextUtils.isEmpty(data.wheelSizeFront)) {
            String wheelSize = data.wheelSizeFront + "\"";
            if (!TextUtils.isEmpty(data.wheelSizeRear) &&
                !data.wheelSizeRear.equals(data.wheelSizeFront)) {
                wheelSize += " / " + data.wheelSizeRear + "\"";
            }
            addDetailRow("Wheel Size", wheelSize);
        }
    }

    private void populateSafety(VehicleData data) {
        boolean hasData = !TextUtils.isEmpty(data.abs) ||
                         !TextUtils.isEmpty(data.backupCamera);

        if (!hasData) return;

        addSection("Safety Features");
        if (!TextUtils.isEmpty(data.abs)) addDetailRow("ABS", data.abs);
        if (!TextUtils.isEmpty(data.esc)) addDetailRow("Stability Control", data.esc);
        if (!TextUtils.isEmpty(data.tractionControl)) addDetailRow("Traction Control", data.tractionControl);
        if (!TextUtils.isEmpty(data.backupCamera)) addDetailRow("Backup Camera", data.backupCamera);
        if (!TextUtils.isEmpty(data.frontAirBagLocations)) addDetailRow("Front Airbags", data.frontAirBagLocations);
        if (!TextUtils.isEmpty(data.sideAirBagLocations)) addDetailRow("Side Airbags", data.sideAirBagLocations);
        if (!TextUtils.isEmpty(data.tpmsType)) addDetailRow("TPMS", data.tpmsType);
        if (!TextUtils.isEmpty(data.daytimeRunningLight)) addDetailRow("Daytime Running Lights", data.daytimeRunningLight);
    }

    private void populateAdvancedFeatures(VehicleData data) {
        boolean hasData = !TextUtils.isEmpty(data.adaptiveCruiseControl) ||
                         !TextUtils.isEmpty(data.blindSpotWarning);

        if (!hasData) return;

        addSection("Advanced Features");
        if (!TextUtils.isEmpty(data.adaptiveCruiseControl)) addDetailRow("Adaptive Cruise Control", data.adaptiveCruiseControl);
        if (!TextUtils.isEmpty(data.forwardCollisionWarning)) addDetailRow("Collision Warning", data.forwardCollisionWarning);
        if (!TextUtils.isEmpty(data.blindSpotWarning)) addDetailRow("Blind Spot Warning", data.blindSpotWarning);
        if (!TextUtils.isEmpty(data.laneDepartureWarning)) addDetailRow("Lane Departure Warning", data.laneDepartureWarning);
        if (!TextUtils.isEmpty(data.laneKeepingAssistance)) addDetailRow("Lane Keeping Assist", data.laneKeepingAssistance);
        if (!TextUtils.isEmpty(data.parkingAssist)) addDetailRow("Parking Assist", data.parkingAssist);
        if (!TextUtils.isEmpty(data.keylessIgnition)) addDetailRow("Keyless Ignition", data.keylessIgnition);
    }

    private void populateManufacturing(VehicleData data) {
        addSection("Manufacturing");

        // Build plant location string
        StringBuilder plantLocation = new StringBuilder();
        if (!TextUtils.isEmpty(data.plantCity)) {
            plantLocation.append(data.plantCity.toUpperCase());
        }
        if (!TextUtils.isEmpty(data.plantState)) {
            if (plantLocation.length() > 0) plantLocation.append(", ");
            plantLocation.append(data.plantState.toUpperCase());
        }
        if (!TextUtils.isEmpty(data.plantCountry)) {
            if (plantLocation.length() > 0) plantLocation.append(", ");
            plantLocation.append(data.plantCountry.toUpperCase());
            if ("UNITED STATES (USA)".equalsIgnoreCase(data.plantCountry) || "USA".equalsIgnoreCase(data.plantCountry)) {
                plantLocation.append(" (USA)");
            }
        }

        if (plantLocation.length() > 0) {
            addDetailRow("Plant Location", plantLocation.toString());
        }
    }

    private void populateWeight(VehicleData data) {
        boolean hasData = !TextUtils.isEmpty(data.curbWeight) ||
                         !TextUtils.isEmpty(data.gvwr);

        if (!hasData) return;

        addSection("Weight");
        if (!TextUtils.isEmpty(data.curbWeight)) addDetailRow("Curb Weight", data.curbWeight + " lbs");
        if (!TextUtils.isEmpty(data.gvwr)) addDetailRow("GVWR", data.gvwr);
    }

    private void populatePricing(VehicleData data) {
        if (TextUtils.isEmpty(data.basePrice)) return;

        addSection("Pricing");
        addDetailRow("Base MSRP", "$" + data.basePrice);
    }

    /**
     * Add a section header to the content container
     */
    private void addSection(String title) {
        TextView sectionHeader = new TextView(this);
        sectionHeader.setText(title);
        sectionHeader.setTextColor(0xFF64B5F6);  // Light blue
        sectionHeader.setTextSize(18);
        sectionHeader.setTypeface(null, android.graphics.Typeface.BOLD);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, dpToPx(24), 0, dpToPx(12));
        sectionHeader.setLayoutParams(params);

        contentContainer.addView(sectionHeader);
    }

    /**
     * Add a detail row (label + value) to the content container
     */
    private void addDetailRow(String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        rowParams.setMargins(0, dpToPx(8), 0, dpToPx(8));
        row.setLayoutParams(rowParams);

        // Label
        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextColor(0xFF9E9E9E);  // Gray
        labelView.setTextSize(14);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1.0f
        );
        labelView.setLayoutParams(labelParams);

        // Value
        TextView valueView = new TextView(this);
        valueView.setText(value);
        valueView.setTextColor(0xFFFFFFFF);  // White
        valueView.setTextSize(14);
        valueView.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1.0f
        );
        valueView.setLayoutParams(valueParams);

        row.addView(labelView);
        row.addView(valueView);
        contentContainer.addView(row);
    }

    /**
     * Convert dp to pixels
     */
    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
