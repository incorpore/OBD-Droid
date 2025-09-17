package com.obddroid.ecu.gui.androbd.vehicle;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.obddroid.ecu.gui.androbd.R;
import com.obddroid.ecu.gui.androbd.api.nhtsa.VehicleData;

/**
 * Bottom Vehicle Info Bar Component
 *
 * Shows current connected vehicle information at bottom of app
 * - Manufacturer avatar/icon
 * - Vehicle name
 * - Year and model
 * - Connection status
 *
 * This component is decoupled and can be added to any activity/fragment
 */
public class VehicleInfoBar extends LinearLayout implements VehicleManager.VehicleChangeListener {

    private TextView manufacturerAvatar;
    private TextView vehicleName;
    private TextView vehicleDetails;
    private ProgressBar loadingSpinner;
    private View connectionIndicator;

    private final VehicleManager vehicleManager;

    public VehicleInfoBar(Context context) {
        this(context, null);
    }

    public VehicleInfoBar(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public VehicleInfoBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        vehicleManager = VehicleManager.getInstance();
        setupView();
    }

    private void setupView() {
        // Configure container
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(16, 12, 16, 12);
        setBackgroundColor(Color.parseColor("#1E1E1E")); // Dark background
        setElevation(8);

        // Create manufacturer avatar
        manufacturerAvatar = new TextView(getContext());
        manufacturerAvatar.setLayoutParams(new LayoutParams(
            dpToPx(40), dpToPx(40)
        ));
        manufacturerAvatar.setGravity(Gravity.CENTER);
        manufacturerAvatar.setTextColor(Color.WHITE);
        manufacturerAvatar.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        manufacturerAvatar.setTypeface(manufacturerAvatar.getTypeface(), android.graphics.Typeface.BOLD);

        // Create circular background for avatar
        GradientDrawable avatarBg = new GradientDrawable();
        avatarBg.setShape(GradientDrawable.OVAL);
        avatarBg.setColor(Color.parseColor("#4CAF50")); // Green
        manufacturerAvatar.setBackground(avatarBg);

        // Create text container
        LinearLayout textContainer = new LinearLayout(getContext());
        textContainer.setOrientation(VERTICAL);
        textContainer.setPadding(dpToPx(12), 0, dpToPx(12), 0);
        LayoutParams textParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1);
        textContainer.setLayoutParams(textParams);

        // Vehicle name (main text)
        vehicleName = new TextView(getContext());
        vehicleName.setTextColor(Color.WHITE);
        vehicleName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        vehicleName.setTypeface(vehicleName.getTypeface(), android.graphics.Typeface.BOLD);
        vehicleName.setText("No Vehicle Connected");

        // Vehicle details (sub text)
        vehicleDetails = new TextView(getContext());
        vehicleDetails.setTextColor(Color.parseColor("#AAAAAA")); // Gray
        vehicleDetails.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        vehicleDetails.setVisibility(GONE);

        // Loading spinner
        loadingSpinner = new ProgressBar(getContext());
        loadingSpinner.setLayoutParams(new LayoutParams(
            dpToPx(24), dpToPx(24)
        ));
        loadingSpinner.setVisibility(GONE);

        // Connection indicator
        connectionIndicator = new View(getContext());
        connectionIndicator.setLayoutParams(new LayoutParams(
            dpToPx(8), dpToPx(8)
        ));
        GradientDrawable indicatorBg = new GradientDrawable();
        indicatorBg.setShape(GradientDrawable.OVAL);
        indicatorBg.setColor(Color.parseColor("#F44336")); // Red - disconnected
        connectionIndicator.setBackground(indicatorBg);

        // Add views
        textContainer.addView(vehicleName);
        textContainer.addView(vehicleDetails);

        addView(manufacturerAvatar);
        addView(textContainer);
        addView(loadingSpinner);
        addView(connectionIndicator);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // Register for vehicle updates
        vehicleManager.addListener(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        // Unregister from vehicle updates
        vehicleManager.removeListener(this);
    }

    @Override
    public void onVINChanged(String vin) {
        post(() -> {
            vehicleName.setText("VIN: " + vin);
            vehicleDetails.setText("Decoding...");
            vehicleDetails.setVisibility(VISIBLE);
            setConnected(true);
        });
    }

    @Override
    public void onVehicleDecoded(VehicleData vehicleData) {
        post(() -> {
            // Update manufacturer avatar
            String initial = vehicleManager.getManufacturerInitial();
            manufacturerAvatar.setText(initial);

            // Set avatar color based on manufacturer
            GradientDrawable avatarBg = (GradientDrawable) manufacturerAvatar.getBackground();
            avatarBg.setColor(getManufacturerColor(vehicleData));

            // Update vehicle name
            String displayName = vehicleData.getDisplayName();
            if (displayName != null && !displayName.isEmpty()) {
                vehicleName.setText(displayName);
            } else {
                vehicleName.setText(vehicleData.vin);
            }

            // Update details
            StringBuilder details = new StringBuilder();
            if (vehicleData.bodyClass != null && !vehicleData.bodyClass.equals("Not Applicable")) {
                details.append(vehicleData.bodyClass);
            }
            String engineDesc = vehicleData.getEngineDescription();
            if (engineDesc != null && !engineDesc.isEmpty()) {
                if (details.length() > 0) details.append(" • ");
                details.append(engineDesc);
            }

            if (details.length() > 0) {
                vehicleDetails.setText(details.toString());
                vehicleDetails.setVisibility(VISIBLE);
            } else {
                vehicleDetails.setVisibility(GONE);
            }

            loadingSpinner.setVisibility(GONE);
            setConnected(true);
        });
    }

    @Override
    public void onVehicleDisconnected() {
        post(() -> {
            manufacturerAvatar.setText("?");
            vehicleName.setText("No Vehicle Connected");
            vehicleDetails.setVisibility(GONE);
            loadingSpinner.setVisibility(GONE);
            setConnected(false);
        });
    }

    @Override
    public void onDecodingStarted() {
        post(() -> {
            loadingSpinner.setVisibility(VISIBLE);
        });
    }

    @Override
    public void onDecodingError(String error) {
        post(() -> {
            vehicleDetails.setText("Unable to decode VIN");
            vehicleDetails.setVisibility(VISIBLE);
            loadingSpinner.setVisibility(GONE);
        });
    }

    private void setConnected(boolean connected) {
        GradientDrawable indicatorBg = (GradientDrawable) connectionIndicator.getBackground();
        indicatorBg.setColor(connected ?
            Color.parseColor("#4CAF50") :  // Green - connected
            Color.parseColor("#F44336"));  // Red - disconnected
    }

    private int getManufacturerColor(VehicleData vehicleData) {
        String manufacturer = vehicleData.make != null ? vehicleData.make : vehicleData.manufacturer;
        if (manufacturer == null) return Color.parseColor("#757575");

        manufacturer = manufacturer.toUpperCase();

        // Brand colors
        if (manufacturer.contains("MERCEDES")) return Color.parseColor("#00897B"); // Teal
        if (manufacturer.contains("BMW")) return Color.parseColor("#1565C0");     // Blue
        if (manufacturer.contains("AUDI")) return Color.parseColor("#E53935");    // Red
        if (manufacturer.contains("VOLKSWAGEN") || manufacturer.contains("VW")) return Color.parseColor("#1E88E5"); // Light Blue
        if (manufacturer.contains("TOYOTA")) return Color.parseColor("#D32F2F");  // Dark Red
        if (manufacturer.contains("HONDA")) return Color.parseColor("#E53935");   // Red
        if (manufacturer.contains("FORD")) return Color.parseColor("#1976D2");    // Blue
        if (manufacturer.contains("CHEVROLET") || manufacturer.contains("GM")) return Color.parseColor("#FFC107"); // Amber
        if (manufacturer.contains("NISSAN")) return Color.parseColor("#424242");  // Dark Gray
        if (manufacturer.contains("TESLA")) return Color.parseColor("#B71C1C");   // Deep Red
        if (manufacturer.contains("PORSCHE")) return Color.parseColor("#616161"); // Gray
        if (manufacturer.contains("FERRARI")) return Color.parseColor("#F44336"); // Red
        if (manufacturer.contains("LAMBORGHINI")) return Color.parseColor("#FF6F00"); // Orange
        if (manufacturer.contains("MAZDA")) return Color.parseColor("#7B1FA2");   // Purple

        // Default color
        return Color.parseColor("#607D8B"); // Blue Gray
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp,
            getContext().getResources().getDisplayMetrics()
        );
    }
}