package com.obddroid.ui.components;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.obddroid.api.nhtsa.VehicleData;
import com.obddroid.utils.CarLogoHelper;
import com.obddroid.vehicle.VehicleManager;

/**
 * Footer bar that displays decoded vehicle information
 * Shows manufacturer icon/letter and vehicle name when VIN is decoded
 */
public class VehicleInfoFooter extends LinearLayout
{
    private static final String TAG = "VehicleInfoFooter";

    private TextView manufacturerIcon;
    private ImageView manufacturerLogo;
    private LinearLayout iconContainer;
    private TextView vehicleInfo;
    private TextView connectionStatus;
    private View divider;
    private LinearLayout contentLayout;
    private LinearLayout expandedContentLayout;
    private VehicleManager.VehicleChangeListener vehicleListener;
    private boolean isConnected = false;
    private boolean isExpanded = false;
    private VehicleData currentVehicleData;
    private View expandIndicator;
    private View statusDot;

    public VehicleInfoFooter(Context context)
    {
        super(context);
        init();
    }

    public VehicleInfoFooter(Context context, AttributeSet attrs)
    {
        super(context, attrs);
        init();
    }

    public VehicleInfoFooter(Context context, AttributeSet attrs, int defStyleAttr)
    {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init()
    {
        // Set up the layout
        setOrientation(LinearLayout.VERTICAL);
        setBackgroundColor(Color.parseColor("#1A1A1A")); // Elegant dark background

        // Add top border line
        divider = new View(getContext());
        divider.setBackgroundColor(Color.parseColor("#333333"));
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dpToPx(1)
        );
        addView(divider, dividerParams);

        // Create content layout
        contentLayout = new LinearLayout(getContext());
        contentLayout.setOrientation(LinearLayout.HORIZONTAL);
        contentLayout.setGravity(Gravity.CENTER_VERTICAL);
        contentLayout.setPadding(dpToPx(16), dpToPx(6), dpToPx(16), dpToPx(6));
        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        addView(contentLayout, contentParams);

        // Create manufacturer icon container
        iconContainer = new LinearLayout(getContext());
        iconContainer.setGravity(Gravity.CENTER);
        // No background - transparent for PNG logos
        iconContainer.setBackground(null);
        LinearLayout.LayoutParams iconContainerParams = new LinearLayout.LayoutParams(
            dpToPx(72),
            dpToPx(72)
        );
        iconContainerParams.rightMargin = dpToPx(12);

        // Create manufacturer logo ImageView
        manufacturerLogo = new ImageView(getContext());
        manufacturerLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        manufacturerLogo.setPadding(0, 0, 0, 0);
        manufacturerLogo.setBackground(null);
        manufacturerLogo.setVisibility(View.GONE);

        // Create manufacturer icon text (fallback when no logo)
        manufacturerIcon = new TextView(getContext());
        manufacturerIcon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        manufacturerIcon.setTextColor(Color.parseColor("#00ACC1")); // Cyan accent
        manufacturerIcon.setTypeface(Typeface.DEFAULT_BOLD);
        manufacturerIcon.setGravity(Gravity.CENTER);
        manufacturerIcon.setText("?");

        // Add both to container (we'll show one at a time)
        iconContainer.addView(manufacturerLogo, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        ));
        iconContainer.addView(manufacturerIcon, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        ));

        // Create info container
        LinearLayout infoContainer = new LinearLayout(getContext());
        infoContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1.0f
        );

        // Create vehicle info text
        vehicleInfo = new TextView(getContext());
        vehicleInfo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        vehicleInfo.setTextColor(Color.parseColor("#FFFFFF"));
        vehicleInfo.setTypeface(Typeface.DEFAULT_BOLD);
        vehicleInfo.setText("No Vehicle Connected");
        vehicleInfo.setSingleLine(true);
        vehicleInfo.setEllipsize(android.text.TextUtils.TruncateAt.END);

        // Create connection status text
        connectionStatus = new TextView(getContext());
        connectionStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        connectionStatus.setTextColor(Color.parseColor("#888888"));
        connectionStatus.setText("Waiting for OBD connection...");
        connectionStatus.setSingleLine(true);

        // Add to info container
        infoContainer.addView(vehicleInfo);
        infoContainer.addView(connectionStatus);

        // Add all to content layout
        contentLayout.addView(iconContainer, iconContainerParams);
        contentLayout.addView(infoContainer, infoParams);

        // Create expand indicator (arrow)
        expandIndicator = new TextView(getContext());
        ((TextView)expandIndicator).setText("▼");
        ((TextView)expandIndicator).setTextColor(Color.parseColor("#888888"));
        ((TextView)expandIndicator).setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        LinearLayout.LayoutParams expandParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        expandParams.leftMargin = dpToPx(8);
        expandParams.gravity = Gravity.CENTER_VERTICAL;
        contentLayout.addView(expandIndicator, expandParams);

        // Create status indicator dot (green circle for connectivity)
        statusDot = new View(getContext());
        statusDot.setBackgroundResource(android.R.drawable.presence_offline);
        statusDot.getBackground().setTint(Color.parseColor("#888888")); // Gray when disconnected
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(
            dpToPx(8),
            dpToPx(8)
        );
        dotParams.leftMargin = dpToPx(8);
        dotParams.gravity = Gravity.CENTER_VERTICAL;
        contentLayout.addView(statusDot, dotParams);

        // Create expanded content layout (initially hidden)
        expandedContentLayout = new LinearLayout(getContext());
        expandedContentLayout.setOrientation(LinearLayout.VERTICAL);
        expandedContentLayout.setPadding(dpToPx(16), 0, dpToPx(16), dpToPx(12));
        expandedContentLayout.setVisibility(View.GONE);
        LinearLayout.LayoutParams expandedParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        addView(expandedContentLayout, expandedParams);

        // Make content layout clickable to expand/collapse
        contentLayout.setClickable(true);
        contentLayout.setFocusable(true);
        contentLayout.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isConnected && currentVehicleData != null) {
                    // Add subtle press feedback
                    v.setAlpha(0.8f);
                    v.animate().alpha(1f).setDuration(200).start();
                    toggleExpanded();
                }
            }
        });

        // Always visible
        setVisibility(View.VISIBLE);

        // Create vehicle change listener
        vehicleListener = new VehicleManager.SimpleVehicleChangeListener() {
            @Override
            public void onVINChanged(String vin) {
                Log.d(TAG, "VIN changed: " + vin);
                post(() -> {
                    isConnected = true;
                    vehicleInfo.setText("VIN: " + vin);
                    connectionStatus.setText("Decoding vehicle information...");
                    connectionStatus.setTextColor(Color.parseColor("#FFA726")); // Orange for loading
                    if (statusDot != null) {
                        statusDot.getBackground().setTint(Color.parseColor("#FFA726")); // Orange while loading
                    }
                });
            }

            @Override
            public void onVehicleDecoded(VehicleData vehicleData) {
                Log.d(TAG, "Vehicle decoded: " + vehicleData.getDisplayName());
                post(() -> updateVehicleDisplay(VehicleManager.getInstance().getCurrentVIN(), vehicleData));
            }

            @Override
            public void onVehicleDisconnected() {
                Log.d(TAG, "Vehicle disconnected");
                post(() -> {
                    isConnected = false;
                    currentVehicleData = null;

                    // Collapse if expanded
                    if (isExpanded) {
                        isExpanded = false;
                        ((TextView)expandIndicator).setRotation(0f);
                        expandedContentLayout.setVisibility(View.GONE);
                    }

                    // Reset logo/icon
                    manufacturerLogo.setVisibility(View.GONE);
                    manufacturerIcon.setVisibility(View.VISIBLE);
                    manufacturerIcon.setText("?");
                    manufacturerIcon.setTextColor(Color.parseColor("#666666"));
                    vehicleInfo.setText("No Vehicle Connected");
                    connectionStatus.setText("Waiting for OBD connection...");
                    connectionStatus.setTextColor(Color.parseColor("#888888"));
                    expandIndicator.setVisibility(View.GONE);
                    if (statusDot != null) {
                        statusDot.getBackground().setTint(Color.parseColor("#888888")); // Gray when disconnected
                    }
                });
            }
        };

        // Register as listener with VehicleManager
        VehicleManager.getInstance().addListener(vehicleListener);
    }

    @Override
    protected void onAttachedToWindow()
    {
        super.onAttachedToWindow();
        // Re-register when attached
        if (vehicleListener != null) {
            VehicleManager.getInstance().addListener(vehicleListener);
        }
        // Get current state
        updateVehicleInfo();
    }

    @Override
    protected void onDetachedFromWindow()
    {
        super.onDetachedFromWindow();
        // Unregister when detached
        if (vehicleListener != null) {
            VehicleManager.getInstance().removeListener(vehicleListener);
        }
    }


    private void updateVehicleInfo()
    {
        VehicleManager vm = VehicleManager.getInstance();
        String vin = vm.getCurrentVIN();
        VehicleData data = vm.getCurrentVehicleData();

        if (vin != null && data != null)
        {
            updateVehicleDisplay(vin, data);
        }
    }

    private void toggleExpanded() {
        isExpanded = !isExpanded;

        // Animate arrow rotation
        ((TextView)expandIndicator).animate()
            .rotation(isExpanded ? 180f : 0f)
            .setDuration(300)
            .start();

        if (isExpanded) {
            updateExpandedContent();
            expandedContentLayout.setVisibility(View.VISIBLE);
            expandedContentLayout.setAlpha(0f);
            expandedContentLayout.animate()
                .alpha(1f)
                .setDuration(300)
                .start();
        } else {
            expandedContentLayout.animate()
                .alpha(0f)
                .setDuration(300)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        expandedContentLayout.setVisibility(View.GONE);
                    }
                })
                .start();
        }
    }

    private void updateExpandedContent() {
        if (currentVehicleData == null) return;

        expandedContentLayout.removeAllViews();

        // Add VIN section
        addSectionHeader("Vehicle Identification");
        addDetailRow("VIN", currentVehicleData.vin);
        addDetailRow("Manufacturer", currentVehicleData.manufacturer);
        if (currentVehicleData.series != null && !currentVehicleData.series.equals("Not Applicable")) {
            addDetailRow("Series", currentVehicleData.series);
        }
        if (currentVehicleData.trim != null && !currentVehicleData.trim.equals("Not Applicable")) {
            addDetailRow("Trim", currentVehicleData.trim);
        }

        // Add Body/Structure section
        addSectionHeader("Body & Structure");
        addDetailRow("Body Class", currentVehicleData.bodyClass);
        addDetailRow("Vehicle Type", currentVehicleData.vehicleType);
        addDetailRow("Doors", currentVehicleData.doors);
        if (currentVehicleData.wheelBase != null && !currentVehicleData.wheelBase.isEmpty()) {
            addDetailRow("Wheelbase", currentVehicleData.wheelBase + " inches");
        }

        // Add Engine section
        addSectionHeader("Engine & Performance");
        if (currentVehicleData.engineModel != null && !currentVehicleData.engineModel.isEmpty()) {
            addDetailRow("Engine Model", currentVehicleData.engineModel);
        }
        if (currentVehicleData.displacementL != null) {
            String displacement = currentVehicleData.displacementL + "L";
            if (currentVehicleData.displacementCC != null) {
                displacement += " (" + currentVehicleData.displacementCC + "cc)";
            }
            addDetailRow("Displacement", displacement);
        }
        addDetailRow("Cylinders", currentVehicleData.engineCylinders);
        addDetailRow("Fuel Type", currentVehicleData.fuelTypePrimary);

        // Add Drivetrain section
        addSectionHeader("Drivetrain");
        addDetailRow("Drive Type", currentVehicleData.driveType);
        addDetailRow("Transmission", currentVehicleData.transmissionStyle);
        if (currentVehicleData.transmissionSpeeds != null && !currentVehicleData.transmissionSpeeds.isEmpty()) {
            addDetailRow("Speeds", currentVehicleData.transmissionSpeeds);
        }

        // Add Manufacturing section
        addSectionHeader("Manufacturing");
        String plantLocation = buildPlantLocation(currentVehicleData);
        if (!plantLocation.isEmpty()) {
            addDetailRow("Plant Location", plantLocation);
        }

        // Add Weight section if available
        if (currentVehicleData.gvwr != null || currentVehicleData.curbWeight != null) {
            addSectionHeader("Weight");
            if (currentVehicleData.curbWeight != null && !currentVehicleData.curbWeight.isEmpty()) {
                addDetailRow("Curb Weight", currentVehicleData.curbWeight + " lbs");
            }
            if (currentVehicleData.gvwr != null && !currentVehicleData.gvwr.isEmpty()) {
                addDetailRow("GVWR", currentVehicleData.gvwr + " lbs");
            }
        }
    }

    private String buildPlantLocation(VehicleData data) {
        StringBuilder location = new StringBuilder();
        if (data.plantCity != null && !data.plantCity.isEmpty()) {
            location.append(data.plantCity);
        }
        if (data.plantState != null && !data.plantState.isEmpty()) {
            if (location.length() > 0) location.append(", ");
            location.append(data.plantState);
        }
        if (data.plantCountry != null && !data.plantCountry.isEmpty()) {
            if (location.length() > 0) location.append(", ");
            location.append(data.plantCountry);
        }
        return location.toString();
    }

    private void addSectionHeader(String title) {
        TextView header = new TextView(getContext());
        header.setText(title);
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        header.setTextColor(Color.parseColor("#00ACC1"));
        header.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = dpToPx(12);
        params.bottomMargin = dpToPx(4);
        expandedContentLayout.addView(header, params);
    }

    private void addDetailRow(String label, String value) {
        if (value == null || value.isEmpty() || value.equals("Not Applicable")) {
            return;
        }

        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        rowParams.topMargin = dpToPx(2);

        TextView labelView = new TextView(getContext());
        labelView.setText(label + ":");
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        labelView.setTextColor(Color.parseColor("#888888"));
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
            dpToPx(110),
            LinearLayout.LayoutParams.WRAP_CONTENT
        );

        TextView valueView = new TextView(getContext());
        valueView.setText(value);
        valueView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        valueView.setTextColor(Color.parseColor("#FFFFFF"));
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1.0f
        );

        row.addView(labelView, labelParams);
        row.addView(valueView, valueParams);
        expandedContentLayout.addView(row, rowParams);
    }

    private void updateVehicleDisplay(String vin, VehicleData vehicleData)
    {
        if (vehicleData == null)
        {
            // Show VIN only if no decoded data
            vehicleInfo.setText("VIN: " + vin);
            connectionStatus.setText("Unable to decode vehicle details");
            connectionStatus.setTextColor(Color.parseColor("#888888"));
            expandIndicator.setVisibility(View.GONE);
            return;
        }

        try {
            isConnected = true;
            currentVehicleData = vehicleData;
            expandIndicator.setVisibility(View.VISIBLE);

            // Try to set manufacturer logo first
            String make = vehicleData.make;
            Bitmap logoBitmap = CarLogoHelper.getLogoBitmap(getContext(), make);

            if (logoBitmap != null) {
                // We have a logo - show it
                manufacturerLogo.setImageBitmap(logoBitmap);
                manufacturerLogo.setVisibility(View.VISIBLE);
                manufacturerIcon.setVisibility(View.GONE);
                // Keep no background for PNG logos
                iconContainer.setBackground(null);
            } else if (make != null && !make.isEmpty()) {
                // No logo - show first letter as fallback
                manufacturerIcon.setText(make.substring(0, 1).toUpperCase());
                manufacturerIcon.setTextColor(Color.parseColor("#00ACC1")); // Cyan when connected
                manufacturerIcon.setVisibility(View.VISIBLE);
                manufacturerLogo.setVisibility(View.GONE);
                // Add background for text icon
                iconContainer.setBackgroundColor(Color.parseColor("#2C2C2C"));
            }

            // Build vehicle info text
            StringBuilder info = new StringBuilder();

            // Add make
            if (vehicleData.make != null && !vehicleData.make.isEmpty())
            {
                info.append(vehicleData.make);
            }

            // Add model
            if (vehicleData.model != null && !vehicleData.model.isEmpty())
            {
                if (info.length() > 0) info.append(" ");
                info.append(vehicleData.model);
            }

            // Add year first for better readability
            if (vehicleData.modelYear != null && !vehicleData.modelYear.isEmpty())
            {
                info.insert(0, vehicleData.modelYear + " ");
            }

            vehicleInfo.setText(info.toString());
            vehicleInfo.setTextColor(Color.parseColor("#FFFFFF"));

            // Update status with engine info or VIN
            String engineDesc = vehicleData.getEngineDescription();
            if (engineDesc != null && !engineDesc.isEmpty())
            {
                connectionStatus.setText(engineDesc + " • VIN: " + vin.substring(vin.length() - 6));
            }
            else
            {
                connectionStatus.setText("Connected • VIN: " + vin.substring(vin.length() - 6));
            }
            connectionStatus.setTextColor(Color.parseColor("#4CAF50")); // Green when connected

            // Update status dot to green when connected
            if (statusDot != null) {
                statusDot.getBackground().setTint(Color.parseColor("#4CAF50")); // Green when connected
            }

            // Update expanded content if currently expanded
            if (isExpanded) {
                updateExpandedContent();
            }

        } catch (Exception e) {
            Log.e(TAG, "Error updating vehicle display", e);
            vehicleInfo.setText("Error reading vehicle data");
            connectionStatus.setText("Please reconnect");
            connectionStatus.setTextColor(Color.parseColor("#F44336")); // Red for error
            expandIndicator.setVisibility(View.GONE);
        }
    }

    private int dpToPx(int dp)
    {
        return (int) TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            getResources().getDisplayMetrics()
        );
    }
}