package com.obddroid.ui.activities;

import android.app.Dialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import io.github.vindecoder.android.VINDecoderAndroid;
import io.github.vindecoder.nhtsa.RecallRecord;
import io.github.vindecoder.nhtsa.VehicleData;

/**
 * Screen for searching and displaying NHTSA safety recalls.
 * Now integrated with the nhtsa-vin-decoder library for real-time recall lookups.
 */
public class RecallActivity extends AppCompatActivity {

    // VIN input fields are hidden - using connected vehicle VIN only
    // private TextInputLayout vinInputLayout;
    // private TextInputEditText vinInput;
    // private Button searchButton;
    private View loadingCard;
    private TextView statusText;
    private ProgressBar loadingIndicator;
    private LinearLayout resultsContainer;
    private View emptyState;
    private View errorCard;
    private TextView errorMessage;
    private VehicleInfoFooter vehicleInfoFooter;
    private View footerOverlay;
    private androidx.coordinatorlayout.widget.CoordinatorLayout coordinatorLayout;

    // Dynamic hero card
    private androidx.cardview.widget.CardView heroCard;
    private View heroInfoState;
    private View heroResultsState;
    private TextView heroRecallCount;
    private TextView heroVehicleText;

    private VINDecoderAndroid vinDecoder;
    private final List<RecallRecord> currentRecalls = new ArrayList<>();
    private VehicleData currentVehicleData;

    private static final String EXPORT_DIRECTORY = Environment.DIRECTORY_DOCUMENTS + "/OBDroid";

    // SharedPreferences for caching
    private static final String PREFS_NAME = "RecallActivityPrefs";
    private static final String PREF_LAST_VIN = "last_vin";
    private static final String PREF_CACHED_RECALLS = "cached_recalls";
    private static final String PREF_CACHED_VEHICLE_DATA = "cached_vehicle_data";
    private static final String PREF_CACHE_TIMESTAMP = "cache_timestamp";

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
        Log.d("RecallActivity", "Initializing VINDecoderAndroid");
        try {
            vinDecoder = new VINDecoderAndroid(this);
            Log.d("RecallActivity", "VINDecoderAndroid initialized successfully");
        } catch (Exception e) {
            Log.e("RecallActivity", "Failed to initialize VINDecoderAndroid", e);
        }

        bindViews();
        setupFooterOverlay();

        // Try to load cached data for the current VIN
        loadCachedData();

        // Hide loading and error states initially
        if (loadingCard != null) loadingCard.setVisibility(View.GONE);
        if (errorCard != null) errorCard.setVisibility(View.GONE);

        // Automatically search for recalls using connected vehicle VIN
        autoSearchRecalls();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.recall_menu, menu);
        return true;
    }

    private void bindViews() {
        coordinatorLayout = findViewById(R.id.coordinator_layout);
        // VIN input fields are hidden - using connected vehicle VIN only
        // vinInputLayout = findViewById(R.id.recalls_vin_input_layout);
        // vinInput = findViewById(R.id.recalls_vin_input);
        // searchButton = findViewById(R.id.recalls_search_button);
        loadingCard = findViewById(R.id.recalls_loading_card);
        statusText = findViewById(R.id.recalls_status_text);
        loadingIndicator = findViewById(R.id.recalls_loading_indicator);
        resultsContainer = findViewById(R.id.recalls_results_container);
        emptyState = findViewById(R.id.recalls_empty_state);
        errorCard = findViewById(R.id.recalls_error_card);
        errorMessage = findViewById(R.id.recalls_error_message);
        vehicleInfoFooter = findViewById(R.id.vehicle_footer);
        footerOverlay = findViewById(R.id.footer_overlay);

        // Hero card views
        heroCard = findViewById(R.id.recalls_hero_card);
        heroInfoState = findViewById(R.id.hero_info_state);
        heroResultsState = findViewById(R.id.hero_results_state);
        heroRecallCount = findViewById(R.id.hero_recall_count);
        heroVehicleText = findViewById(R.id.hero_vehicle_text);
    }

    private void setupFooterOverlay() {
        if (vehicleInfoFooter != null && footerOverlay != null) {
            vehicleInfoFooter.setOverlayView(footerOverlay);
        }
    }

    private void autoSearchRecalls() {
        try {
            VehicleManager vm = VehicleManager.getInstance();
            String vin = vm.getCurrentVIN();
            if (!TextUtils.isEmpty(vin)) {
                Log.d("RecallActivity", "Auto-searching recalls for VIN: " + vin);
                performRecallSearchForVin(vin);
            } else {
                Log.d("RecallActivity", "No VIN available from connected vehicle");
                // Show message that no vehicle is connected
                if (heroInfoState != null) {
                    // Find the first TextView in hero_info_state
                    for (int i = 0; i < ((LinearLayout)heroInfoState).getChildCount(); i++) {
                        View child = ((LinearLayout)heroInfoState).getChildAt(i);
                        if (child instanceof TextView) {
                            TextView infoText = (TextView) child;
                            infoText.setText("Connect to a vehicle to check for safety recalls");
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e("RecallActivity", "Error getting VIN from VehicleManager", e);
        }
    }

    /**
     * Show a snackbar properly positioned with CoordinatorLayout and Material Components
     */
    private void showSnackbar(String message, SnackbarHelper.MessageType type) {
        if (coordinatorLayout == null) {
            // Fallback to regular SnackbarHelper
            SnackbarHelper.showSnackbar(this, message, type);
            return;
        }

        com.google.android.material.snackbar.Snackbar snackbar =
            com.google.android.material.snackbar.Snackbar.make(coordinatorLayout, message,
                com.google.android.material.snackbar.Snackbar.LENGTH_LONG);

        // Style the snackbar - use consistent gray color like other pages
        View snackbarView = snackbar.getView();
        snackbarView.setBackgroundColor(android.graphics.Color.parseColor("#757575"));

        // Set text color to white
        TextView textView = snackbarView.findViewById(com.google.android.material.R.id.snackbar_text);
        if (textView != null) {
            textView.setTextColor(android.graphics.Color.WHITE);
        }

        // Set bottom margin to appear directly above footer (56dp is footer height)
        androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams params =
            (androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams) snackbarView.getLayoutParams();
        params.setMargins(0, 0, 0, (int) (56 * getResources().getDisplayMetrics().density));
        snackbarView.setLayoutParams(params);

        snackbar.show();
    }

    private void performRecallSearchForVin(String vin) {
        Log.d("RecallActivity", "performRecallSearchForVin called with VIN: " + vin);

        if (TextUtils.isEmpty(vin)) {
            Log.e("RecallActivity", "VIN is empty");
            showSnackbar("No VIN available from connected vehicle", SnackbarHelper.MessageType.ERROR);
            return;
        }

        if (vin.length() != 17) {
            Log.e("RecallActivity", "Invalid VIN length: " + vin.length());
            showSnackbar("Invalid VIN from connected vehicle", SnackbarHelper.MessageType.ERROR);
            return;
        }

        Log.d("RecallActivity", "Showing loading state");
        // Show loading state
        if (loadingCard != null) loadingCard.setVisibility(View.VISIBLE);
        if (statusText != null) statusText.setText("Searching for recalls...");
        currentRecalls.clear();
        currentVehicleData = null;
        resultsContainer.removeAllViews();
        resultsContainer.setVisibility(View.GONE);
        if (emptyState != null) emptyState.setVisibility(View.GONE);
        if (errorCard != null) errorCard.setVisibility(View.GONE);

        // Decode VIN with recalls
        Log.d("RecallActivity", "Calling vinDecoder.decodeWithRecalls()");
        try {
            vinDecoder.decodeWithRecalls(vin, new VINDecoderAndroid.DecodeCallback() {
                @Override
                public void onSuccess(VehicleData vehicleData) {
                    Log.d("RecallActivity", "decodeWithRecalls onSuccess called");
                    Log.d("RecallActivity", "VehicleData: " + (vehicleData != null ?
                        "Make=" + vehicleData.getMake() + ", Model=" + vehicleData.getModel() : "null"));

                    runOnUiThread(() -> {
                        if (loadingCard != null) loadingCard.setVisibility(View.GONE);
                        currentVehicleData = vehicleData;
                        currentRecalls.clear();

                        if (vehicleData != null && vehicleData.getRecalls() != null) {
                            Log.d("RecallActivity", "Recalls found: " + vehicleData.getRecalls().size());
                            if (!vehicleData.getRecalls().isEmpty()) {
                                currentRecalls.addAll(vehicleData.getRecalls());
                                displayRecalls(vehicleData.getRecalls(), vehicleData);
                                // Save to cache
                                saveCachedData(vin, vehicleData.getRecalls(), vehicleData);
                                showSnackbar(vehicleData.getRecalls().size() + " recall" +
                                    (vehicleData.getRecalls().size() == 1 ? "" : "s") + " found",
                                    SnackbarHelper.MessageType.WARNING);
                            } else {
                                // Show empty state with success message
                                updateHeroCard(null, null, false);
                                resultsContainer.setVisibility(View.GONE);
                                if (emptyState != null) emptyState.setVisibility(View.VISIBLE);
                                if (errorCard != null) errorCard.setVisibility(View.GONE);
                                // Save empty results to cache
                                saveCachedData(vin, new ArrayList<>(), vehicleData);
                                showSnackbar("No recalls found - vehicle is safe!",
                                    SnackbarHelper.MessageType.SUCCESS);
                            }
                        } else {
                            Log.d("RecallActivity", "No recalls in response");
                            // Show empty state with success message
                            updateHeroCard(null, null, false);
                            resultsContainer.setVisibility(View.GONE);
                            if (emptyState != null) emptyState.setVisibility(View.VISIBLE);
                            if (errorCard != null) errorCard.setVisibility(View.GONE);
                            showSnackbar("No recalls found - vehicle is safe!",
                                SnackbarHelper.MessageType.SUCCESS);
                        }
                    });
                }

                @Override
                public void onError(String error) {
                    Log.e("RecallActivity", "decodeWithRecalls onError: " + error);
                    runOnUiThread(() -> {
                        if (loadingCard != null) loadingCard.setVisibility(View.GONE);
                        currentRecalls.clear();
                        currentVehicleData = null;
                        updateHeroCard(null, null, false);
                        if (errorCard != null) {
                            errorCard.setVisibility(View.VISIBLE);
                            if (errorMessage != null) {
                                errorMessage.setText(error != null ? error : "Failed to search recalls. Please check vehicle connection.");
                            }
                        }
                        resultsContainer.setVisibility(View.GONE);
                        if (emptyState != null) emptyState.setVisibility(View.GONE);
                    });
                }
            });
        } catch (Exception e) {
            Log.e("RecallActivity", "Exception calling decodeWithRecalls", e);
            if (loadingCard != null) loadingCard.setVisibility(View.GONE);
            currentRecalls.clear();
            currentVehicleData = null;
            updateHeroCard(null, null, false);
            if (errorCard != null) {
                errorCard.setVisibility(View.VISIBLE);
                if (errorMessage != null) {
                    errorMessage.setText(e.getMessage() != null ? e.getMessage() : "An unexpected error occurred");
                }
            }
            resultsContainer.setVisibility(View.GONE);
            if (emptyState != null) emptyState.setVisibility(View.GONE);
        }
    }

    private void displayRecalls(List<RecallRecord> recalls, VehicleData vehicleData) {
        // Hide loading, empty, and error states when showing results
        if (loadingCard != null) loadingCard.setVisibility(View.GONE);
        if (emptyState != null) emptyState.setVisibility(View.GONE);
        if (errorCard != null) errorCard.setVisibility(View.GONE);

        // Transform hero card to show results
        updateHeroCard(recalls, vehicleData, true);

        resultsContainer.removeAllViews();

        // Add individual recall cards
        for (RecallRecord recall : recalls) {
            View recallView = createRecallView(recall);
            resultsContainer.addView(recallView);
        }

        resultsContainer.setVisibility(View.VISIBLE);
    }

    private void updateHeroCard(List<RecallRecord> recalls, VehicleData vehicleData, boolean hasRecalls) {
        Log.d("RecallActivity", "updateHeroCard called - hasRecalls=" + hasRecalls +
            ", heroCard=" + (heroCard != null) +
            ", heroInfoState=" + (heroInfoState != null) +
            ", heroResultsState=" + (heroResultsState != null));

        if (heroCard == null) {
            Log.e("RecallActivity", "heroCard is null!");
            return;
        }

        if (hasRecalls && recalls != null && !recalls.isEmpty()) {
            // Switch to results state - orange warning
            Log.d("RecallActivity", "Switching to results state with " + recalls.size() + " recalls");
            if (heroInfoState != null) {
                heroInfoState.setVisibility(View.GONE);
            } else {
                Log.e("RecallActivity", "heroInfoState is null!");
            }

            if (heroResultsState != null) {
                heroResultsState.setVisibility(View.VISIBLE);
            } else {
                Log.e("RecallActivity", "heroResultsState is null!");
            }

            // Change card color to warning orange
            heroCard.setCardBackgroundColor(getResources().getColor(R.color.fault_warning, null));

            // Set recall count
            if (heroRecallCount != null) {
                String text = String.format(Locale.US, "%d Recall%s Detected",
                    recalls.size(), recalls.size() == 1 ? "" : "s");
                heroRecallCount.setText(text);
                Log.d("RecallActivity", "Set recall count text: " + text);
            } else {
                Log.e("RecallActivity", "heroRecallCount is null!");
            }

            // Set vehicle info
            if (heroVehicleText != null && vehicleData != null) {
                String vehicle = String.format(Locale.US, "%s %s %s",
                    vehicleData.getModelYear() != null ? vehicleData.getModelYear() : "",
                    vehicleData.getMake() != null ? vehicleData.getMake() : "",
                    vehicleData.getModel() != null ? vehicleData.getModel() : "").trim();
                heroVehicleText.setText(vehicle);
                Log.d("RecallActivity", "Set vehicle text: " + vehicle);
            } else {
                Log.e("RecallActivity", "heroVehicleText=" + (heroVehicleText != null) + ", vehicleData=" + (vehicleData != null));
            }
        } else {
            // Switch to info state - green
            Log.d("RecallActivity", "Switching to info state");
            if (heroInfoState != null) heroInfoState.setVisibility(View.VISIBLE);
            if (heroResultsState != null) heroResultsState.setVisibility(View.GONE);

            // Change card color back to success green
            heroCard.setCardBackgroundColor(getResources().getColor(R.color.fault_success, null));
        }
    }

    private void showSaveReportDialog() {
        if (currentRecalls.isEmpty()) {
            showSnackbar("Search for recalls before saving a report",
                SnackbarHelper.MessageType.INFO);
            return;
        }

        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_export_ecu);
        dialog.setCancelable(true);

        TextView dialogTitle = dialog.findViewById(R.id.dialog_title);
        if (dialogTitle != null) {
            dialogTitle.setText("Save Report");
        }

        View csvOption = dialog.findViewById(R.id.option_export_csv);
        if (csvOption != null) {
            csvOption.setOnClickListener(v -> {
                dialog.dismiss();
                exportRecallsToCSV();
            });
        }

        View jsonOption = dialog.findViewById(R.id.option_export_json);
        if (jsonOption != null) {
            jsonOption.setOnClickListener(v -> {
                dialog.dismiss();
                exportRecallsToJSON();
            });
        }

        View cancelButton = dialog.findViewById(R.id.btn_cancel);
        if (cancelButton != null) {
            cancelButton.setOnClickListener(v -> dialog.dismiss());
        }

        dialog.show();
    }

    private void exportRecallsToCSV() {
        if (currentRecalls.isEmpty()) {
            showSnackbar("No recall data available to export.",
                SnackbarHelper.MessageType.INFO);
            return;
        }

        String filename = buildExportFilename("csv");

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "text/csv");
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, EXPORT_DIRECTORY);

                Uri uri = getContentResolver().insert(MediaStore.Files.getContentUri("external"), values);
                if (uri == null) {
                    throw new IOException("Unable to create export file");
                }

                try (OutputStream outputStream = getContentResolver().openOutputStream(uri);
                     OutputStreamWriter osWriter = new OutputStreamWriter(outputStream);
                     BufferedWriter writer = new BufferedWriter(osWriter)) {
                    writeCsvContent(writer);
                }
            } else {
                File documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
                File obdroidDir = new File(documentsDir, "OBDroid");
                if (!obdroidDir.exists() && !obdroidDir.mkdirs()) {
                    throw new IOException("Unable to create export directory");
                }

                File csvFile = new File(obdroidDir, filename);
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(csvFile))) {
                    writeCsvContent(writer);
                }
            }

            showSnackbar("Recall report saved: " + filename,
                SnackbarHelper.MessageType.SUCCESS);
        } catch (IOException e) {
            showSnackbar("Failed to export CSV: " + e.getMessage(),
                SnackbarHelper.MessageType.ERROR);
        }
    }

    private void exportRecallsToJSON() {
        if (currentRecalls.isEmpty()) {
            showSnackbar("No recall data available to export.",
                SnackbarHelper.MessageType.INFO);
            return;
        }

        String filename = buildExportFilename("json");

        try {
            JSONObject root = new JSONObject();
            root.put("generatedAt", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(new Date()));
            root.put("vin", getCurrentVin());
            root.put("vehicle", getVehicleDisplayName());
            root.put("recallCount", currentRecalls.size());

            JSONArray recallsArray = new JSONArray();
            for (RecallRecord recall : currentRecalls) {
                JSONObject item = new JSONObject();
                putIfNotEmpty(item, "campaignNumber", recall.getNhtsaCampaignNumber());
                putIfNotEmpty(item, "actionNumber", recall.getNhtsaActionNumber());
                putIfNotEmpty(item, "manufacturer", recall.getManufacturer());
                putIfNotEmpty(item, "component", recall.getComponent());
                putIfNotEmpty(item, "modelYear", recall.getModelYear());
                putIfNotEmpty(item, "make", recall.getMake());
                putIfNotEmpty(item, "model", recall.getModel());
                putIfNotEmpty(item, "reportReceivedDate", recall.getReportReceivedDate());
                String formattedDate = formatDateSafe(recall.getReportReceivedDate());
                if (!TextUtils.isEmpty(formattedDate) && !formattedDate.equals(recall.getReportReceivedDate())) {
                    item.put("reportDateFormatted", formattedDate);
                }
                putIfNotEmpty(item, "summary", recall.getSummary());
                putIfNotEmpty(item, "remedy", recall.getRemedy());
                putIfNotEmpty(item, "consequence", recall.getConsequence());
                putIfNotEmpty(item, "notes", recall.getNotes());
                putIfNotEmpty(item, "mfrRecallNumber", recall.getMfrRecallNumber());
                putIfNotNull(item, "overTheAirUpdate", recall.getOverTheAirUpdate());
                putIfNotNull(item, "parkIt", recall.getParkIt());
                putIfNotNull(item, "parkOutside", recall.getParkOutside());
                recallsArray.put(item);
            }
            root.put("recalls", recallsArray);

            String jsonString = root.toString(2);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "application/json");
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, EXPORT_DIRECTORY);

                Uri uri = getContentResolver().insert(MediaStore.Files.getContentUri("external"), values);
                if (uri == null) {
                    throw new IOException("Unable to create export file");
                }

                try (OutputStream outputStream = getContentResolver().openOutputStream(uri);
                     OutputStreamWriter osWriter = new OutputStreamWriter(outputStream);
                     BufferedWriter writer = new BufferedWriter(osWriter)) {
                    writer.write(jsonString);
                }
            } else {
                File documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
                File obdroidDir = new File(documentsDir, "OBDroid");
                if (!obdroidDir.exists() && !obdroidDir.mkdirs()) {
                    throw new IOException("Unable to create export directory");
                }

                File jsonFile = new File(obdroidDir, filename);
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(jsonFile))) {
                    writer.write(jsonString);
                }
            }

            showSnackbar("Recall report saved: " + filename,
                SnackbarHelper.MessageType.SUCCESS);
        } catch (IOException | JSONException e) {
            showSnackbar("Failed to export JSON: " + e.getMessage(),
                SnackbarHelper.MessageType.ERROR);
        }
    }

    private void writeCsvContent(BufferedWriter writer) throws IOException {
        writer.write("Campaign #,Component,Model Year,Make,Model,Report Date,Summary,Remedy,Consequence,Notes\n");
        for (RecallRecord recall : currentRecalls) {
            writer.write(csvEscape(recall.getNhtsaCampaignNumber()));
            writer.write(",");
            writer.write(csvEscape(recall.getComponent()));
            writer.write(",");
            writer.write(csvEscape(recall.getModelYear()));
            writer.write(",");
            writer.write(csvEscape(recall.getMake()));
            writer.write(",");
            writer.write(csvEscape(recall.getModel()));
            writer.write(",");
            writer.write(csvEscape(recall.getReportReceivedDate()));
            writer.write(",");
            writer.write(csvEscape(recall.getSummary()));
            writer.write(",");
            writer.write(csvEscape(recall.getRemedy()));
            writer.write(",");
            writer.write(csvEscape(recall.getConsequence()));
            writer.write(",");
            writer.write(csvEscape(recall.getNotes()));
            writer.write("\n");
        }
    }

    private String buildExportFilename(String extension) {
        String vin = getCurrentVin();
        String vinSuffix = "vehicle";
        if (!TextUtils.isEmpty(vin)) {
            if (vin.length() >= 6) {
                vinSuffix = vin.substring(vin.length() - 6).toUpperCase(Locale.US);
            } else {
                vinSuffix = vin.toUpperCase(Locale.US);
            }
        }

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        return vinSuffix + "-safety-recalls-" + timestamp + "." + extension;
    }

    private String getCurrentVin() {
        // Get VIN from connected vehicle
        try {
            VehicleManager vm = VehicleManager.getInstance();
            String vin = vm.getCurrentVIN();
            if (!TextUtils.isEmpty(vin)) {
                return vin.toUpperCase(Locale.US);
            }
        } catch (Exception ignored) {
            // VehicleManager may not be available
        }

        if (currentVehicleData != null && !TextUtils.isEmpty(currentVehicleData.getVin())) {
            return currentVehicleData.getVin().toUpperCase(Locale.US);
        }

        try {
            String vin = VehicleManager.getInstance().getCurrentVIN();
            if (!TextUtils.isEmpty(vin)) {
                return vin.toUpperCase(Locale.US);
            }
        } catch (Exception ignored) {
            // VehicleManager may not be initialized yet
        }

        return "";
    }

    private String getVehicleDisplayName() {
        if (currentVehicleData != null) {
            String displayName = currentVehicleData.getDisplayName();
            if (!TextUtils.isEmpty(displayName)) {
                return displayName;
            }
        }
        return "";
    }

    private void putIfNotEmpty(JSONObject target, String key, String value) throws JSONException {
        if (!TextUtils.isEmpty(value)) {
            target.put(key, value);
        }
    }

    private void putIfNotNull(JSONObject target, String key, Boolean value) throws JSONException {
        if (value != null) {
            target.put(key, value);
        }
    }

    private String csvEscape(String value) {
        if (value == null) {
            return "";
        }
        String sanitized = value.replace("\r", " ").replace("\n", " ").trim();
        if (sanitized.contains(",") || sanitized.contains("\"")) {
            return "\"" + sanitized.replace("\"", "\"\"") + "\"";
        }
        return sanitized;
    }

    private String formatDateSafe(String dateString) {
        if (TextUtils.isEmpty(dateString)) {
            return "";
        }
        return formatDate(dateString);
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

        // Set component - clean up formatting
        if (!TextUtils.isEmpty(recall.getComponent())) {
            String componentText = recall.getComponent();
            // Replace colon separator with dash for better readability
            componentText = componentText.replace(":", " - ");
            component.setText("Component: " + componentText);
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
                showSnackbar("Unable to open recall details",
                    SnackbarHelper.MessageType.ERROR);
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
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            return true;
        } else if (id == R.id.action_refresh_recalls) {
            String vin = getCurrentVin();
            if (!TextUtils.isEmpty(vin) && vin.length() == 17) {
                showSnackbar("Refreshing recall data...",
                    SnackbarHelper.MessageType.INFO);
                performRecallSearchForVin(vin);
            } else {
                showSnackbar("No valid VIN from connected vehicle",
                    SnackbarHelper.MessageType.WARNING);
            }
            return true;
        } else if (id == R.id.action_save_report) {
            showSaveReportDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // Cache management methods
    private void saveCachedData(String vin, List<RecallRecord> recalls, VehicleData vehicleData) {
        if (TextUtils.isEmpty(vin)) return;

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        try {
            // Save VIN
            editor.putString(PREF_LAST_VIN, vin);

            // Save recall data as JSON
            JSONArray recallsArray = new JSONArray();
            for (RecallRecord recall : recalls) {
                JSONObject recallObj = new JSONObject();
                // Using getNhtsaCampaignNumber() method
                recallObj.put("campaignNumber", recall.getNhtsaCampaignNumber());
                recallObj.put("component", recall.getComponent());
                recallObj.put("summary", recall.getSummary());
                recallObj.put("remedy", recall.getRemedy());
                recallObj.put("reportReceivedDate", recall.getReportReceivedDate());
                // Skip URL if method doesn't exist
                recallsArray.put(recallObj);
            }
            editor.putString(PREF_CACHED_RECALLS, recallsArray.toString());

            // Save vehicle data as JSON
            if (vehicleData != null) {
                JSONObject vehicleObj = new JSONObject();
                if (vehicleData.getMake() != null) vehicleObj.put("make", vehicleData.getMake());
                if (vehicleData.getModel() != null) vehicleObj.put("model", vehicleData.getModel());
                if (vehicleData.getModelYear() != null) vehicleObj.put("modelYear", vehicleData.getModelYear());
                editor.putString(PREF_CACHED_VEHICLE_DATA, vehicleObj.toString());
            }

            // Save timestamp
            editor.putLong(PREF_CACHE_TIMESTAMP, System.currentTimeMillis());
            editor.apply();

            Log.d("RecallActivity", "Cached " + recalls.size() + " recalls for VIN: " + vin);
        } catch (JSONException e) {
            Log.e("RecallActivity", "Error saving cached data", e);
        }
    }

    private void loadCachedData() {
        String currentVin = getCurrentVin();
        if (TextUtils.isEmpty(currentVin) || currentVin.length() != 17) return;

        // Check if we already have data loaded for this VIN
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String cachedVin = prefs.getString(PREF_LAST_VIN, "");

        // If VIN matches and we have data in memory, display it
        if (currentVin.equalsIgnoreCase(cachedVin) &&
            currentVehicleData != null &&
            !currentRecalls.isEmpty()) {

            Log.d("RecallActivity", "Displaying cached recalls for VIN: " + currentVin);
            displayRecalls(currentRecalls, currentVehicleData);
            showSnackbar("Showing previous results (tap Refresh for latest)",
                SnackbarHelper.MessageType.INFO);
        } else if (currentVin.equalsIgnoreCase(cachedVin)) {
            // Check cache timestamp to show a hint
            long cacheTimestamp = prefs.getLong(PREF_CACHE_TIMESTAMP, 0);
            if (cacheTimestamp > 0) {
                long ageMinutes = (System.currentTimeMillis() - cacheTimestamp) / (60 * 1000);
                if (ageMinutes < 60) {
                    Log.d("RecallActivity", "Previous search found no recalls " + ageMinutes + " minutes ago");
                }
            }
        }
    }
}
