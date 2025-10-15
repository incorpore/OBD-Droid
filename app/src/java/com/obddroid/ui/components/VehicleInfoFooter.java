package com.obddroid.ui.components;

import android.animation.ValueAnimator;
import android.content.Context;
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
import android.widget.ScrollView;
import android.widget.TextView;

import io.github.vindecoder.nhtsa.VehicleData;
import com.obddroid.core.obd.ElmProt;
import com.automotivelogolibrary.AutomotiveLogoLibraryAndroid;
import com.obddroid.vehicle.VehicleManager;
import com.obddroid.core.ecu.EcuDataPv;
import com.obddroid.core.pvs.PvList;
import com.obddroid.core.obd.ObdProt;
import com.obddroid.core.pvs.PvChangeListener;
import com.obddroid.core.pvs.PvChangeEvent;
import java.beans.PropertyChangeEvent;

/**
 * Footer bar that displays decoded vehicle information
 * Shows manufacturer icon/letter and vehicle name when VIN is decoded
 */
public class VehicleInfoFooter extends LinearLayout implements PvChangeListener
{
    private static final String TAG = "VehicleInfoFooter";

    private TextView manufacturerIcon;
    private ImageView manufacturerLogo;
    private LinearLayout iconContainer;
    private TextView vehicleInfo;
    private TextView connectionStatus;
    private View divider;
    private LinearLayout contentLayout;
    private LinearLayout expandedContainer;
    private ScrollView expandedScrollView;
    private LinearLayout expandedContentLayout;
    private LinearLayout tabContainer;
    private TextView vehicleInfoTab;
    private TextView obdDataTab;
    private View tabIndicator;
    private boolean showingVehicleInfo = true;
    private VehicleManager.VehicleChangeListener vehicleListener;
    private boolean isConnected = false;
    private boolean isEcuConnected = false;
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
        setBackgroundColor(Color.parseColor("#212121")); // Match navbar dark grey

        // Set elevation higher than snackbars so they slide from behind
        setElevation(8f);

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
        contentLayout.setPadding(dpToPx(16), dpToPx(4), dpToPx(16), dpToPx(4));
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

        // Create expanded container
        expandedContainer = new LinearLayout(getContext());
        expandedContainer.setOrientation(LinearLayout.VERTICAL);
        expandedContainer.setVisibility(View.GONE);
        expandedContainer.setBackgroundColor(Color.parseColor("#1A1A1A")); // Slightly lighter than footer

        // Create tab container
        tabContainer = new LinearLayout(getContext());
        tabContainer.setOrientation(LinearLayout.HORIZONTAL);
        tabContainer.setBackgroundColor(Color.parseColor("#212121"));
        tabContainer.setPadding(dpToPx(16), dpToPx(8), dpToPx(16), 0);

        // Create Vehicle Info tab
        vehicleInfoTab = new TextView(getContext());
        vehicleInfoTab.setText("VEHICLE INFO");
        vehicleInfoTab.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        vehicleInfoTab.setTypeface(Typeface.DEFAULT_BOLD);
        vehicleInfoTab.setTextColor(Color.parseColor("#00ACC1")); // Selected color
        vehicleInfoTab.setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8));
        vehicleInfoTab.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams vehicleTabParams = new LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1.0f
        );
        vehicleTabParams.rightMargin = dpToPx(8);

        // Create OBD Data tab
        obdDataTab = new TextView(getContext());
        obdDataTab.setText("OBD DATA");
        obdDataTab.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        obdDataTab.setTypeface(Typeface.DEFAULT_BOLD);
        obdDataTab.setTextColor(Color.parseColor("#666666")); // Unselected color
        obdDataTab.setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8));
        obdDataTab.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams obdTabParams = new LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1.0f
        );
        obdTabParams.leftMargin = dpToPx(8);

        // Add tabs to container
        tabContainer.addView(vehicleInfoTab, vehicleTabParams);
        tabContainer.addView(obdDataTab, obdTabParams);

        // Create tab indicator line
        tabIndicator = new View(getContext());
        tabIndicator.setBackgroundColor(Color.parseColor("#00ACC1"));
        LinearLayout.LayoutParams indicatorParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dpToPx(2)
        );

        // Add tab container to expanded container
        expandedContainer.addView(tabContainer);
        expandedContainer.addView(tabIndicator, indicatorParams);

        // Create ScrollView for expanded content
        expandedScrollView = new ScrollView(getContext());
        expandedScrollView.setFillViewport(false);
        expandedScrollView.setBackgroundColor(Color.parseColor("#1A1A1A"));
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dpToPx(400) // Increased height for better viewing
        );

        // Create expanded content layout inside ScrollView
        expandedContentLayout = new LinearLayout(getContext());
        expandedContentLayout.setOrientation(LinearLayout.VERTICAL);
        expandedContentLayout.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(16));

        // Add content layout to ScrollView
        expandedScrollView.addView(expandedContentLayout, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        // Add ScrollView to expanded container
        expandedContainer.addView(expandedScrollView, scrollParams);

        // Add expanded container to main layout
        LinearLayout.LayoutParams expandedParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        addView(expandedContainer, expandedParams);

        // Set up tab click listeners
        vehicleInfoTab.setOnClickListener(v -> switchToVehicleInfo());
        obdDataTab.setOnClickListener(v -> switchToObdData());

        // Add bottom border line
        View bottomDivider = new View(getContext());
        bottomDivider.setBackgroundColor(Color.parseColor("#333333"));
        LinearLayout.LayoutParams bottomDividerParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dpToPx(1)
        );
        addView(bottomDivider, bottomDividerParams);

        // Make content layout clickable to expand/collapse
        contentLayout.setClickable(true);
        contentLayout.setFocusable(true);
        contentLayout.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "Footer clicked!");
                // Allow expanding even without connection to show demo
                // Add subtle press feedback
                v.setAlpha(0.8f);
                v.animate().alpha(1f).setDuration(200).start();
                toggleExpanded();
            }
        });
        Log.d(TAG, "VehicleInfoFooter initialized - click listener set");

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
                        expandedContainer.setVisibility(View.GONE);
                    }

                    // Update display based on ECU connection
                    updateConnectionDisplay();
                });
            }

            @Override
            public void onECUConnectionChanged(ElmProt.STAT state) {
                Log.d(TAG, "ECU connection state changed: " + state);
                post(() -> {
                    isEcuConnected = (state == ElmProt.STAT.ECU_DETECTED ||
                                      state == ElmProt.STAT.CONNECTED);
                    updateConnectionDisplay();
                });
            }

            @Override
            public void onVINRetrievalFailed() {
                Log.d(TAG, "VIN retrieval failed - Mode 9 not supported");
                post(() -> {
                    // Clear connected flag since we don't have VIN
                    isConnected = false;
                    currentVehicleData = null;
                    // Update display without showing repeated notifications
                    updateConnectionDisplay();
                });
            }

            @Override
            public void onDecodingStarted() {
                Log.d(TAG, "VIN decoding started");
                post(() -> {
                    connectionStatus.setText("Decoding vehicle information...");
                    connectionStatus.setTextColor(Color.parseColor("#FFA726")); // Orange for loading
                    if (statusDot != null) {
                        statusDot.getBackground().setTint(Color.parseColor("#FFA726")); // Orange while loading
                    }
                });
            }

            @Override
            public void onDecodingError(String error) {
                Log.e(TAG, "VIN decoding error: " + error);
                post(() -> {
                    isConnected = false;
                    currentVehicleData = null;
                    // Show error message briefly, then revert to connection display
                    connectionStatus.setText(error);
                    connectionStatus.setTextColor(Color.parseColor("#FF5722")); // Red for error
                    if (statusDot != null) {
                        statusDot.getBackground().setTint(Color.parseColor("#FF5722")); // Red for error
                    }
                    // After 3 seconds, update to normal connection display
                    postDelayed(() -> updateConnectionDisplay(), 3000);
                });
            }
        };

        // Register as listener with VehicleManager
        VehicleManager.getInstance().addListener(vehicleListener);

        // Register for Mode 9 data updates
        ObdProt.VidPvs.addPvChangeListener(this);

        // Start with disconnected state - will be updated when ECU connects
        isEcuConnected = false;
    }

    @Override
    public void pvChanged(PvChangeEvent event) {
        // Mode 9 data updated - refresh expanded content if visible
        Log.d(TAG, "Mode 9 data update received: " + event.getKey());
        if (isExpanded && expandedContainer != null && expandedContainer.getVisibility() == View.VISIBLE && !showingVehicleInfo) {
            post(() -> updateExpandedContent());
        }
    }

    public void propertyChange(PropertyChangeEvent evt) {
        // Not used for this implementation
    }

    @Override
    protected void onAttachedToWindow()
    {
        super.onAttachedToWindow();
        // Clear any stale display first to prevent flash
        currentVehicleData = null;
        isConnected = false;

        // Immediately show "No Vehicle Connected" to prevent flash of old data
        updateConnectionDisplay();

        // Re-register when attached
        if (vehicleListener != null) {
            VehicleManager.getInstance().addListener(vehicleListener);
        }
        // Re-register for Mode 9 updates
        ObdProt.VidPvs.addPvChangeListener(this);

        // Get current state (will update if actually connected)
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
        // Unregister from Mode 9 updates
        ObdProt.VidPvs.removePvChangeListener(this);
    }


    private void updateVehicleInfo()
    {
        VehicleManager vm = VehicleManager.getInstance();

        // Check if ECU is connected first
        if (!vm.isECUConnected()) {
            // Not connected, don't show old data
            updateConnectionDisplay();
            return;
        }

        String vin = vm.getCurrentVIN();
        VehicleData data = vm.getCurrentVehicleData();

        if (vin != null && data != null)
        {
            updateVehicleDisplay(vin, data);
        } else {
            // ECU connected but no vehicle data - update display accordingly
            updateConnectionDisplay();
        }
    }

    private void toggleExpanded() {
        isExpanded = !isExpanded;
        Log.d(TAG, "toggleExpanded: isExpanded = " + isExpanded);

        // Animate arrow rotation
        if (expandIndicator != null) {
            ((TextView)expandIndicator).animate()
                .rotation(isExpanded ? 180f : 0f)
                .setDuration(300)
                .start();
        }

        if (isExpanded) {
            Log.d(TAG, "Expanding footer - updating content");
            updateExpandedContent();
            if (expandedContainer != null) {
                expandedContainer.setVisibility(View.VISIBLE);
                expandedContainer.setAlpha(0f);
                expandedContainer.animate()
                    .alpha(1f)
                    .setDuration(300)
                    .start();
                Log.d(TAG, "Expanded container made visible");
            } else {
                Log.e(TAG, "expandedContainer is null!");
            }
        } else {
            Log.d(TAG, "Collapsing footer");
            if (expandedContainer != null) {
                expandedContainer.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            expandedContainer.setVisibility(View.GONE);
                            Log.d(TAG, "Expanded container hidden");
                        }
                    })
                    .start();
            }
        }
    }

    private void switchToVehicleInfo() {
        if (showingVehicleInfo) return;
        showingVehicleInfo = true;

        // Update tab colors
        vehicleInfoTab.setTextColor(Color.parseColor("#00ACC1"));
        obdDataTab.setTextColor(Color.parseColor("#666666"));

        // Update content
        updateExpandedContent();
    }

    private void switchToObdData() {
        if (!showingVehicleInfo) return;
        showingVehicleInfo = false;

        // Update tab colors
        vehicleInfoTab.setTextColor(Color.parseColor("#666666"));
        obdDataTab.setTextColor(Color.parseColor("#00ACC1"));

        // Update content
        updateExpandedContent();
    }

    private void updateExpandedContent() {
        expandedContentLayout.removeAllViews();

        if (showingVehicleInfo) {
            // Show Vehicle Info tab content
            if (currentVehicleData != null) {
                displayVehicleInfo();
            } else {
                // Show demo/placeholder content
                addEmptyStateMessage("Vehicle Information",
                    "Vehicle information will appear here once connected and VIN is decoded");
            }
        } else {
            // Show OBD Data tab content
            displayObdData();
        }
    }

    private void displayVehicleInfo() {
        if (currentVehicleData == null) return;

        // Add VIN section with styled headers and rows
        addStyledSectionHeader("Vehicle Identification");
        addStyledDetailRow("VIN", currentVehicleData.vin);
        addStyledDetailRow("Manufacturer", currentVehicleData.manufacturer);
        if (currentVehicleData.series != null && !currentVehicleData.series.equals("Not Applicable")) {
            addStyledDetailRow("Series", currentVehicleData.series);
        }
        if (currentVehicleData.trim != null && !currentVehicleData.trim.equals("Not Applicable")) {
            addStyledDetailRow("Trim", currentVehicleData.trim);
        }

        // Add Body/Structure section
        addStyledSectionHeader("Body & Structure");
        addStyledDetailRow("Body Class", currentVehicleData.bodyClass);
        addStyledDetailRow("Vehicle Type", currentVehicleData.vehicleType);
        addStyledDetailRow("Doors", currentVehicleData.doors);
        if (currentVehicleData.wheelBase != null && !currentVehicleData.wheelBase.isEmpty()) {
            addStyledDetailRow("Wheelbase", currentVehicleData.wheelBase + " inches");
        }

        // Add Engine section
        addStyledSectionHeader("Engine & Performance");
        if (currentVehicleData.engineModel != null && !currentVehicleData.engineModel.isEmpty()) {
            addStyledDetailRow("Engine Model", currentVehicleData.engineModel);
        }
        if (currentVehicleData.displacementL != null) {
            String displacement = currentVehicleData.displacementL + "L";
            if (currentVehicleData.displacementCC != null) {
                displacement += " (" + currentVehicleData.displacementCC + "cc)";
            }
            addStyledDetailRow("Displacement", displacement);
        }
        addStyledDetailRow("Cylinders", currentVehicleData.engineCylinders);
        addStyledDetailRow("Fuel Type", currentVehicleData.fuelTypePrimary);

        // Add Drivetrain section
        addStyledSectionHeader("Drivetrain");
        addStyledDetailRow("Drive Type", currentVehicleData.driveType);
        addStyledDetailRow("Transmission", currentVehicleData.transmissionStyle);
        if (currentVehicleData.transmissionSpeeds != null && !currentVehicleData.transmissionSpeeds.isEmpty()) {
            addStyledDetailRow("Speeds", currentVehicleData.transmissionSpeeds);
        }

        // Add Manufacturing section
        addStyledSectionHeader("Manufacturing");
        String plantLocation = buildPlantLocation(currentVehicleData);
        if (!plantLocation.isEmpty()) {
            addStyledDetailRow("Plant Location", plantLocation);
        }

        // Add Weight section if available
        if (currentVehicleData.gvwr != null || currentVehicleData.curbWeight != null) {
            addStyledSectionHeader("Weight");
            if (currentVehicleData.curbWeight != null && !currentVehicleData.curbWeight.isEmpty()) {
                addStyledDetailRow("Curb Weight", currentVehicleData.curbWeight + " lbs");
            }
            if (currentVehicleData.gvwr != null && !currentVehicleData.gvwr.isEmpty()) {
                addStyledDetailRow("GVWR", currentVehicleData.gvwr + " lbs");
            }
        }
    }

    private void displayObdData() {
        // Display Mode 9 OBD data with better formatting
        addMode9Section();
    }

    /**
     * Add Mode 9 OBD vehicle information to the expanded view with better formatting
     */
    @SuppressWarnings("deprecation")
    private void addMode9Section() {
        // Get Mode 9 data from VidPvs
        PvList vidPvs = ObdProt.VidPvs;

        if (vidPvs == null || vidPvs.isEmpty()) {
            // No Mode 9 data available
            addEmptyStateMessage("No OBD Mode 9 data available", "Mode 9 data will appear here once retrieved from the vehicle");
            return;
        }

        // Categorize Mode 9 data - show EVERYTHING
        java.util.Map<String, java.util.List<String[]>> categorizedData = new java.util.LinkedHashMap<>();
        categorizedData.put("Vehicle Identification", new java.util.ArrayList<>());
        categorizedData.put("ECU Information", new java.util.ArrayList<>());
        categorizedData.put("Calibration Data", new java.util.ArrayList<>());
        categorizedData.put("Emission Monitors", new java.util.ArrayList<>());
        categorizedData.put("System Counters", new java.util.ArrayList<>());
        categorizedData.put("Protocol Information", new java.util.ArrayList<>());
        categorizedData.put("Other Information", new java.util.ArrayList<>());

        // Process all Mode 9 items
        for (Object key : vidPvs.keySet()) {
            Object value = vidPvs.get(key);
            if (value instanceof EcuDataPv) {
                EcuDataPv pv = (EcuDataPv) value;
                String description = String.valueOf(pv.get(EcuDataPv.FID_DESCRIPT));
                Object dataValue = pv.get(EcuDataPv.FID_VALUE);

                if (description != null && dataValue != null) {
                    // Show ALL Mode 9 data including zeros and VIN

                    String label = formatLabel(description);
                    String displayValue = formatValue(description, dataValue);

                    // Categorize ALL the data - including VIN and message counts
                    if (description.toLowerCase().contains("vehicle identification") ||
                        description.toLowerCase().contains("vin")) {
                        categorizedData.get("Vehicle Identification").add(new String[]{label, displayValue});
                    } else if (description.contains("Message count") ||
                              description.contains("Number of") ||
                              description.contains("counts_") ||
                              description.contains("numitems") ||
                              description.contains("length")) {
                        categorizedData.get("Protocol Information").add(new String[]{label, displayValue});
                    } else if (description.contains("ECU name") || description.contains("ECU")) {
                        categorizedData.get("ECU Information").add(new String[]{label, displayValue});
                    } else if (description.contains("Monitor") || description.contains("COMP") ||
                              description.contains("Catalyst") || description.contains("O2") ||
                              description.contains("EGR") || description.contains("EVAP") ||
                              description.contains("AIR") || description.contains("Exhaust") ||
                              description.contains("Boost") || description.contains("Fuel") ||
                              description.contains("NMHC") || description.contains("NOx") ||
                              description.contains("PM Filter")) {
                        // Skip if it's just ignition counter
                        if (!description.contains("Ignition")) {
                            categorizedData.get("Emission Monitors").add(new String[]{label, displayValue});
                        } else {
                            categorizedData.get("System Counters").add(new String[]{label, displayValue});
                        }
                    } else if (description.contains("Counter") || description.contains("CNTR") ||
                              description.contains("Ignition") || description.contains("OBD Monitoring Conditions")) {
                        categorizedData.get("System Counters").add(new String[]{label, displayValue});
                    } else if (description.contains("Calibration") || description.contains("CVN") ||
                              description.contains("CAL-ID") || description.contains("CAL")) {
                        categorizedData.get("Calibration Data").add(new String[]{label, displayValue});
                    } else {
                        categorizedData.get("Other Information").add(new String[]{label, displayValue});
                    }
                }
            }
        }

        // Display categorized data
        boolean hasAnyData = false;
        for (java.util.Map.Entry<String, java.util.List<String[]>> entry : categorizedData.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                hasAnyData = true;
                addStyledSectionHeader(entry.getKey());
                for (String[] item : entry.getValue()) {
                    addStyledDetailRow(item[0], item[1]);
                }
            }
        }

        if (!hasAnyData) {
            addEmptyStateMessage("No OBD Mode 9 data available", "Mode 9 data will appear here once retrieved from the vehicle");
        }
    }

    private String formatLabel(String description) {
        // Clean up and format ALL labels including VIN and message counts
        if (description.toLowerCase().contains("vehicle identification number")) {
            return "VIN";
        } else if (description.contains("Number of VIN items")) {
            return "VIN Item Count";
        } else if (description.contains("Message count VIN")) {
            return "VIN Message Count";
        } else if (description.contains("Message count CAL-ID")) {
            return "CAL-ID Message Count";
        } else if (description.contains("Number of CAL-ID items")) {
            return "CAL-ID Item Count";
        } else if (description.contains("Message count CVN")) {
            return "CVN Message Count";
        } else if (description.contains("Message count IPT")) {
            return "IPT Message Count";
        } else if (description.contains("ECU name length")) {
            return "ECU Name Length";
        } else if (description.contains("ECU name")) {
            return "ECU Name";
        } else if (description.contains("Calibration identifier")) {
            return description.contains("2") ? "Calibration ID 2" : "Calibration ID";
        } else if (description.contains("Calibration verification")) {
            return "CVN (Calibration Verification)";
        } else if (description.contains("OBDCOMP") || description.contains("OBDCOND")) {
            return "OBD Monitor Conditions";
        } else if (description.contains("IGNCNTR")) {
            return "Ignition Cycles";
        } else if (description.contains("CATCOMP")) {
            String bank = extractBank(description);
            return "Catalyst Monitor Completions" + bank;
        } else if (description.contains("CATCOND")) {
            String bank = extractBank(description);
            return "Catalyst Monitor Conditions" + bank;
        } else if (description.contains("O2SCOMP")) {
            String bank = extractBank(description);
            return description.contains("Secondary") ?
                   "Secondary O2 Completions" + bank : "O2 Monitor Completions" + bank;
        } else if (description.contains("O2SCOND")) {
            String bank = extractBank(description);
            return description.contains("Secondary") ?
                   "Secondary O2 Conditions" + bank : "O2 Monitor Conditions" + bank;
        } else if (description.contains("EGRCOMP")) {
            return "EGR Monitor Completions";
        } else if (description.contains("EGRCOND")) {
            return "EGR Monitor Conditions";
        } else if (description.contains("EVAPCOMP")) {
            return "EVAP Monitor Completions";
        } else if (description.contains("EVAPCOND")) {
            return "EVAP Monitor Conditions";
        } else if (description.contains("AIRCOMP")) {
            return "AIR Monitor Completions";
        } else if (description.contains("AIRCOND")) {
            return "AIR Monitor Conditions";
        } else if (description.contains("Completion Counts")) {
            return description.replace("Completion Counts", "Completions");
        } else if (description.contains("Conditions Encountered Counts")) {
            return description.replace("Conditions Encountered Counts", "Conditions");
        }
        return description;
    }

    private String extractBank(String description) {
        if (description.contains("Bank 1")) return " (Bank 1)";
        if (description.contains("Bank 2")) return " (Bank 2)";
        if (description.contains("Bank 3")) return " (Bank 3)";
        if (description.contains("Bank 4")) return " (Bank 4)";
        return "";
    }

    private String formatValue(String description, Object dataValue) {
        String displayValue = dataValue.toString();

        // Handle empty values
        if (displayValue.isEmpty()) {
            return "(empty)";
        }

        // Format hex values
        if (displayValue.startsWith("0x")) {
            return displayValue.toUpperCase();
        }

        // Special formatting for zeros in certain fields
        if (displayValue.equals("0") || displayValue.equals("0.0")) {
            if (description.contains("Message count") ||
                description.contains("Number of") ||
                description.contains("length")) {
                return "0";  // Show zero as-is for counts
            } else if (description.contains("Monitor")) {
                return "0";  // Show zero for monitor counts
            }
        }

        // Format numeric counters with thousands separator for large numbers
        if (description.contains("Counter") || description.contains("Counts") ||
            description.contains("CNTR") || description.contains("Conditions") ||
            description.contains("Completions")) {
            try {
                double numValue = Double.parseDouble(displayValue);
                if (numValue >= 1000) {
                    return String.format("%,.0f", numValue); // Add thousands separator
                } else {
                    return String.format("%.0f", numValue); // No separator for small numbers
                }
            } catch (NumberFormatException ignored) {
                return displayValue;
            }
        }

        // Format VIN to uppercase
        if (description.toLowerCase().contains("vehicle identification") && displayValue.length() == 17) {
            return displayValue.toUpperCase();
        }

        return displayValue;
    }

    private void addEmptyStateMessage(String title, String subtitle) {
        TextView emptyTitle = new TextView(getContext());
        emptyTitle.setText(title);
        emptyTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        emptyTitle.setTextColor(Color.parseColor("#666666"));
        emptyTitle.setGravity(Gravity.CENTER);
        emptyTitle.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        titleParams.topMargin = dpToPx(48);
        expandedContentLayout.addView(emptyTitle, titleParams);

        TextView emptySubtitle = new TextView(getContext());
        emptySubtitle.setText(subtitle);
        emptySubtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        emptySubtitle.setTextColor(Color.parseColor("#444444"));
        emptySubtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        subtitleParams.topMargin = dpToPx(8);
        subtitleParams.leftMargin = dpToPx(32);
        subtitleParams.rightMargin = dpToPx(32);
        expandedContentLayout.addView(emptySubtitle, subtitleParams);
    }

    private void addStyledSectionHeader(String title) {
        // Add some space before section
        View spacer = new View(getContext());
        LinearLayout.LayoutParams spacerParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dpToPx(8)
        );
        expandedContentLayout.addView(spacer, spacerParams);

        // Add section with modern card style
        LinearLayout sectionCard = new LinearLayout(getContext());
        sectionCard.setOrientation(LinearLayout.VERTICAL);
        sectionCard.setBackgroundColor(Color.parseColor("#252525"));
        sectionCard.setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(4));

        TextView header = new TextView(getContext());
        header.setText(title);
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        header.setTextColor(Color.parseColor("#00ACC1"));
        header.setTypeface(Typeface.DEFAULT_BOLD);
        header.setAllCaps(true);

        sectionCard.addView(header);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.topMargin = dpToPx(8);
        expandedContentLayout.addView(sectionCard, cardParams);
    }

    private void addStyledDetailRow(String label, String value) {
        if (value == null || value.isEmpty() || value.equals("Not Applicable")) {
            return;
        }

        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));
        row.setBackgroundColor(Color.parseColor("#1F1F1F"));

        TextView labelView = new TextView(getContext());
        labelView.setText(label);
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        labelView.setTextColor(Color.parseColor("#999999"));
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1.0f
        );

        TextView valueView = new TextView(getContext());
        valueView.setText(value);
        valueView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        valueView.setTextColor(Color.parseColor("#FFFFFF"));
        valueView.setTypeface(Typeface.DEFAULT_BOLD);
        valueView.setGravity(Gravity.END);
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );

        row.addView(labelView, labelParams);
        row.addView(valueView, valueParams);

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        rowParams.topMargin = dpToPx(1);
        expandedContentLayout.addView(row, rowParams);
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
            expandIndicator.setVisibility(View.VISIBLE); // Keep visible even without decoded data
            return;
        }

        try {
            isConnected = true;
            currentVehicleData = vehicleData;
            expandIndicator.setVisibility(View.VISIBLE);

            // Try to set manufacturer logo first
            String make = vehicleData.make;
            android.graphics.drawable.Drawable logoDrawable = AutomotiveLogoLibraryAndroid.getLogoDrawable(getContext(), make);

            if (logoDrawable != null) {
                // We have a logo - show it
                manufacturerLogo.setImageDrawable(logoDrawable);
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
                // Add background for text icon - slightly lighter than footer for contrast
                iconContainer.setBackgroundColor(Color.parseColor("#2E2E2E"));
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
            String vinSuffix = (vin != null && vin.length() >= 6) ? (" • VIN: " + vin.substring(vin.length() - 6)) : "";

            if (engineDesc != null && !engineDesc.isEmpty())
            {
                connectionStatus.setText(engineDesc + vinSuffix);
            }
            else
            {
                connectionStatus.setText("Connected" + vinSuffix);
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
            expandIndicator.setVisibility(View.VISIBLE); // Keep visible
        }
    }

    private void updateConnectionDisplay() {
        if (isConnected) {
            // If we have VIN/Vehicle data, updateVehicleDisplay handles the display
            return;
        }

        if (isEcuConnected) {
            VehicleManager vm = VehicleManager.getInstance();

            if (vm.hasVINRetrievalFailed()) {
                // ECU connected but VIN retrieval failed (Service 0x09 not supported)
                manufacturerLogo.setVisibility(View.GONE);
                manufacturerIcon.setVisibility(View.VISIBLE);
                manufacturerIcon.setText("OBD");
                manufacturerIcon.setTextColor(Color.parseColor("#FFA726")); // Orange for basic connection
                vehicleInfo.setText("ECU Connected");
                connectionStatus.setText("Vehicle Info not available (Mode 9 not supported)");
                connectionStatus.setTextColor(Color.parseColor("#FFA726")); // Orange
                expandIndicator.setVisibility(View.VISIBLE); // Keep visible for OBD data
                if (statusDot != null) {
                    statusDot.getBackground().setTint(Color.parseColor("#FFA726")); // Orange for partial connection
                }
            } else {
                // ECU connected, waiting for VIN retrieval
                manufacturerLogo.setVisibility(View.GONE);
                manufacturerIcon.setVisibility(View.VISIBLE);
                manufacturerIcon.setText("OBD");
                manufacturerIcon.setTextColor(Color.parseColor("#00ACC1")); // Cyan for loading
                vehicleInfo.setText("ECU Connected");
                connectionStatus.setText("Retrieving vehicle information...");
                connectionStatus.setTextColor(Color.parseColor("#00ACC1")); // Cyan for loading
                expandIndicator.setVisibility(View.VISIBLE); // Keep visible
                if (statusDot != null) {
                    statusDot.getBackground().setTint(Color.parseColor("#00ACC1")); // Cyan for loading
                }
            }
        } else {
            // No ECU connection at all
            manufacturerLogo.setVisibility(View.GONE);
            manufacturerIcon.setVisibility(View.VISIBLE);
            manufacturerIcon.setText("?");
            manufacturerIcon.setTextColor(Color.parseColor("#666666"));
            vehicleInfo.setText("No Vehicle Connected");
            connectionStatus.setText("Waiting for OBD connection...");
            connectionStatus.setTextColor(Color.parseColor("#888888"));
            expandIndicator.setVisibility(View.VISIBLE); // Keep visible for demo mode
            if (statusDot != null) {
                statusDot.getBackground().setTint(Color.parseColor("#888888")); // Gray when disconnected
            }
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