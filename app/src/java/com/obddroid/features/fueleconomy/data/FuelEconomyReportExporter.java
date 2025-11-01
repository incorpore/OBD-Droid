package com.obddroid.features.fueleconomy.data;

import android.content.Context;
import android.text.TextUtils;

import com.obddroid.services.VehicleManager;
import com.obddroid.utils.ReportFileWriter;
import com.obddroid.utils.VehicleData;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Handles exporting fuel economy snapshots to CSV/JSON reports.
 */
public class FuelEconomyReportExporter {

    private static final String MIME_TYPE_CSV = "text/csv";
    private static final String MIME_TYPE_JSON = "application/json";
    private final Context context;

    public FuelEconomyReportExporter(Context context) {
        this.context = context.getApplicationContext();
    }

    public static class Snapshot {
        public String vin;
        public String vehicleName;
        public float instantMpg;
        public float averageMpg;
        public float fuelLevelPercent;
        public float estimatedRangeMiles;
        public float fuelFlowGalPerHour;
        public float throttlePercent;
        public float vehicleSpeedMph;
        public String timeToEmpty;
        public float recentAvgMpg;
        public float mediumAvgMpg;
        public float longTermAvgMpg;
        public boolean veCalibrated;
        public Float tankCapacityGallons;
        public String statusText;
        public String statusDetail;
    }

    public String exportToCsv(Snapshot snapshot) throws IOException {
        Snapshot populated = populateDefaults(snapshot);
        String filename = buildFilename(populated.vin, "csv");

        return ReportFileWriter.write(context, filename, MIME_TYPE_CSV, writer -> {
            writer.write("Generated At,");
            writer.write(csvEscape(currentTimestamp()));
            writer.newLine();

            writer.write("VIN,");
            writer.write(csvEscape(populated.vin));
            writer.newLine();

            writer.write("Vehicle,");
            writer.write(csvEscape(populated.vehicleName));
            writer.newLine();

            writer.write("Fuel Economy Status,");
            writer.write(csvEscape(firstNonEmpty(populated.statusText, "N/A")));
            writer.newLine();

            writer.write("Status Detail,");
            writer.write(csvEscape(firstNonEmpty(populated.statusDetail, "N/A")));
            writer.newLine();

            if (populated.tankCapacityGallons != null) {
                writer.write("Tank Capacity (gal),");
                writer.write(csvEscape(String.format(Locale.US, "%.1f", populated.tankCapacityGallons)));
                writer.newLine();
            }

            writer.write("VE Calibrated,");
            writer.write(populated.veCalibrated ? "Yes" : "No");
            writer.newLine();
            writer.newLine();

            writer.write("Metric,Value");
            writer.newLine();
            writer.write("Instant MPG," + csvEscape(formatFloat(populated.instantMpg)));
            writer.newLine();
            writer.write("Average MPG," + csvEscape(formatFloat(populated.averageMpg)));
            writer.newLine();
            writer.write("Fuel Level (%)," + csvEscape(formatFloat(populated.fuelLevelPercent)));
            writer.newLine();
            writer.write("Estimated Range (mi)," + csvEscape(formatFloat(populated.estimatedRangeMiles)));
            writer.newLine();
            writer.write("Fuel Flow (gal/h)," + csvEscape(formatFloat(populated.fuelFlowGalPerHour)));
            writer.newLine();
            writer.write("Throttle Position (%)," + csvEscape(formatFloat(populated.throttlePercent)));
            writer.newLine();
            writer.write("Vehicle Speed (mph)," + csvEscape(formatFloat(populated.vehicleSpeedMph)));
            writer.newLine();
            writer.write("Time to Empty," + csvEscape(firstNonEmpty(populated.timeToEmpty, "N/A")));
            writer.newLine();
            writer.newLine();

            writer.write("Historical Averages,,");
            writer.newLine();
            writer.write("Recent (0-5 min)," + csvEscape(formatFloat(populated.recentAvgMpg)));
            writer.newLine();
            writer.write("Medium (0-30 min)," + csvEscape(formatFloat(populated.mediumAvgMpg)));
            writer.newLine();
            writer.write("Long Term (0-3 hr)," + csvEscape(formatFloat(populated.longTermAvgMpg)));
            writer.newLine();
        });
    }

    public String exportToJson(Snapshot snapshot) throws IOException, JSONException {
        Snapshot populated = populateDefaults(snapshot);
        String filename = buildFilename(populated.vin, "json");

        JSONObject root = new JSONObject();
        root.put("generatedAt", currentTimestampIso());
        root.put("vin", populated.vin);
        root.put("vehicle", populated.vehicleName);
        root.put("status", firstNonEmpty(populated.statusText, "N/A"));
        root.put("statusDetail", firstNonEmpty(populated.statusDetail, "N/A"));
        root.put("veCalibrated", populated.veCalibrated);
        if (populated.tankCapacityGallons != null) {
            root.put("tankCapacityGallons", populated.tankCapacityGallons);
        }

        JSONObject metrics = new JSONObject();
        putNumber(metrics, "instantMpg", populated.instantMpg);
        putNumber(metrics, "averageMpg", populated.averageMpg);
        putNumber(metrics, "fuelLevelPercent", populated.fuelLevelPercent);
        putNumber(metrics, "estimatedRangeMiles", populated.estimatedRangeMiles);
        putNumber(metrics, "fuelFlowGallonsPerHour", populated.fuelFlowGalPerHour);
        putNumber(metrics, "throttlePositionPercent", populated.throttlePercent);
        putNumber(metrics, "vehicleSpeedMph", populated.vehicleSpeedMph);
        metrics.put("timeToEmpty", firstNonEmpty(populated.timeToEmpty, "N/A"));
        root.put("metrics", metrics);

        JSONObject history = new JSONObject();
        putNumber(history, "recentMpg", populated.recentAvgMpg);
        putNumber(history, "mediumMpg", populated.mediumAvgMpg);
        putNumber(history, "longTermMpg", populated.longTermAvgMpg);
        root.put("historicalAverages", history);

        return ReportFileWriter.writeText(context, filename, MIME_TYPE_JSON, root.toString(2));
    }

    private static Snapshot populateDefaults(Snapshot snapshot) {
        Snapshot copy = snapshot != null ? snapshot : new Snapshot();
        if (TextUtils.isEmpty(copy.vin)) {
            VehicleManager vm = VehicleManager.getInstance();
            copy.vin = firstNonEmpty(vm.getCurrentVIN(), "UNKNOWN");
        }
        if (TextUtils.isEmpty(copy.vehicleName)) {
            VehicleData data = VehicleManager.getInstance().getCurrentVehicleData();
            copy.vehicleName = data != null && !TextUtils.isEmpty(data.getDisplayName())
                ? data.getDisplayName() : "Unknown Vehicle";
        }
        return copy;
    }

    private static void putNumber(JSONObject object, String key, float value) throws JSONException {
        if (!Float.isNaN(value) && !Float.isInfinite(value)) {
            object.put(key, round(value));
        }
    }

    private static float round(float value) {
        return Math.round(value * 100f) / 100f;
    }

    private static String buildFilename(String vin, String extension) {
        String normalizedVin = TextUtils.isEmpty(vin) ? "unknown" : vin;
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        return "fuel_economy_" + normalizedVin + "_" + timestamp + "." + extension;
    }

    private static String csvEscape(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

    private static String firstNonEmpty(String value, String fallback) {
        return TextUtils.isEmpty(value) ? fallback : value;
    }

    private static String formatFloat(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return "N/A";
        }
        return String.format(Locale.US, "%.2f", value);
    }

    private static String currentTimestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
    }

    private static String currentTimestampIso() {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(new Date());
    }
}
