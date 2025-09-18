package com.obddroid.ecu.gui.androbd;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.obddroid.ecu.gui.androbd.api.nhtsa.VehicleData;
import com.obddroid.ecu.gui.androbd.vehicle.VehicleManager;

/**
 * Footer bar that displays decoded vehicle information
 * Shows manufacturer icon/letter and vehicle name when VIN is decoded
 */
public class VehicleInfoFooter extends LinearLayout
{
    private static final String TAG = "VehicleInfoFooter";

    private TextView manufacturerIcon;
    private TextView vehicleInfo;
    private TextView connectionStatus;
    private View divider;
    private LinearLayout contentLayout;
    private VehicleManager.VehicleChangeListener vehicleListener;
    private boolean isConnected = false;

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
        contentLayout.setPadding(dpToPx(16), dpToPx(10), dpToPx(16), dpToPx(10));
        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        addView(contentLayout, contentParams);

        // Create manufacturer icon container with circular background
        LinearLayout iconContainer = new LinearLayout(getContext());
        iconContainer.setGravity(Gravity.CENTER);
        iconContainer.setBackgroundResource(android.R.drawable.ic_menu_compass);
        iconContainer.getBackground().setTint(Color.parseColor("#2C2C2C"));
        LinearLayout.LayoutParams iconContainerParams = new LinearLayout.LayoutParams(
            dpToPx(40),
            dpToPx(40)
        );
        iconContainerParams.rightMargin = dpToPx(12);

        // Create manufacturer icon text
        manufacturerIcon = new TextView(getContext());
        manufacturerIcon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        manufacturerIcon.setTextColor(Color.parseColor("#00ACC1")); // Cyan accent
        manufacturerIcon.setTypeface(Typeface.DEFAULT_BOLD);
        manufacturerIcon.setGravity(Gravity.CENTER);
        manufacturerIcon.setText("?");
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

        // Create status indicator dot
        View statusDot = new View(getContext());
        statusDot.setBackgroundResource(android.R.drawable.presence_offline);
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(
            dpToPx(8),
            dpToPx(8)
        );
        dotParams.leftMargin = dpToPx(8);
        dotParams.gravity = Gravity.CENTER_VERTICAL;
        contentLayout.addView(statusDot, dotParams);

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
                    manufacturerIcon.setText("?");
                    manufacturerIcon.setTextColor(Color.parseColor("#666666"));
                    vehicleInfo.setText("No Vehicle Connected");
                    connectionStatus.setText("Waiting for OBD connection...");
                    connectionStatus.setTextColor(Color.parseColor("#888888"));
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

    private void updateVehicleDisplay(String vin, VehicleData vehicleData)
    {
        if (vehicleData == null)
        {
            // Show VIN only if no decoded data
            vehicleInfo.setText("VIN: " + vin);
            connectionStatus.setText("Unable to decode vehicle details");
            connectionStatus.setTextColor(Color.parseColor("#888888"));
            return;
        }

        try {
            isConnected = true;

            // Set manufacturer icon (first letter of make)
            String make = vehicleData.make;
            if (make != null && !make.isEmpty())
            {
                manufacturerIcon.setText(make.substring(0, 1).toUpperCase());
                manufacturerIcon.setTextColor(Color.parseColor("#00ACC1")); // Cyan when connected
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

        } catch (Exception e) {
            Log.e(TAG, "Error updating vehicle display", e);
            vehicleInfo.setText("Error reading vehicle data");
            connectionStatus.setText("Please reconnect");
            connectionStatus.setTextColor(Color.parseColor("#F44336")); // Red for error
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