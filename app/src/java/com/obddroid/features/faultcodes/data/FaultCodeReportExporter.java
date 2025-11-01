package com.obddroid.features.faultcodes.data;

import android.content.Context;
import android.text.TextUtils;

import com.obddroid.services.FaultCodeService;
import com.obddroid.services.VehicleManager;
import com.obddroid.utils.ReportFileWriter;
import com.obddroid.utils.VehicleData;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Handles exporting fault code scan results to CSV/JSON reports.
 */
public class FaultCodeReportExporter {

    private static final String MIME_TYPE_CSV = "text/csv";
    private static final String MIME_TYPE_JSON = "application/json";
    private final Context context;

    public FaultCodeReportExporter(Context context) {
        this.context = context.getApplicationContext();
    }

    public String exportToCsv(List<FaultCodeService.FaultCodeInfo> codes,
                              String milHeader,
                              String milSubtitle) throws IOException {
        FaultCodeSummary summary = buildSummary(codes);
        String filename = buildFilename(summary.vin, "csv");

        return ReportFileWriter.write(context, filename, MIME_TYPE_CSV, writer -> {
            // Metadata section
            writer.write("Generated At,");
            writer.write(csvEscape(summary.generatedAt));
            writer.newLine();

            writer.write("VIN,");
            writer.write(csvEscape(summary.vin));
            writer.newLine();

            writer.write("Vehicle,");
            writer.write(csvEscape(summary.vehicleName));
            writer.newLine();

            writer.write("MIL Status,");
            writer.write(csvEscape(firstNonEmpty(milHeader, "N/A")));
            writer.newLine();

            writer.write("Summary,");
            writer.write(csvEscape(buildSummaryLine(summary, milSubtitle)));
            writer.newLine();
            writer.newLine();

            // Data header
            writer.write("Code,Description,Status,Type,Freeze Frame");
            writer.newLine();

            if (codes != null) {
                for (FaultCodeService.FaultCodeInfo code : codes) {
                    writer.write(csvEscape(code.code));
                    writer.write(",");
                    writer.write(csvEscape(code.description));
                    writer.write(",");
                    writer.write(csvEscape(code.isPending ? "Pending" :
                            code.type == FaultCodeService.CodeType.PERMANENT ? "Permanent" : "Confirmed"));
                    writer.write(",");
                    writer.write(csvEscape(code.type.name()));
                    writer.write(",");
                    writer.write(csvEscape(code.hasFreeze ? "Yes" : "No"));
                    writer.newLine();
                }
            }
        });
    }

    public String exportToJson(List<FaultCodeService.FaultCodeInfo> codes,
                               String milHeader,
                               String milSubtitle) throws IOException, JSONException {
        FaultCodeSummary summary = buildSummary(codes);
        String filename = buildFilename(summary.vin, "json");

        JSONObject root = new JSONObject();
        root.put("generatedAt", summary.generatedAt);
        root.put("vin", summary.vin);
        root.put("vehicle", summary.vehicleName);
        root.put("milStatus", firstNonEmpty(milHeader, "N/A"));
        root.put("statusDetails", firstNonEmpty(milSubtitle, "N/A"));
        root.put("totalCodes", summary.total);
        root.put("confirmed", summary.confirmed);
        root.put("pending", summary.pending);
        root.put("permanent", summary.permanent);

        JSONArray codesArray = new JSONArray();
        if (codes != null) {
            for (FaultCodeService.FaultCodeInfo code : codes) {
                JSONObject item = new JSONObject();
                item.put("code", code.code);
                item.put("description", firstNonEmpty(code.description, "N/A"));
                item.put("status", code.isPending ? "Pending" :
                        code.type == FaultCodeService.CodeType.PERMANENT ? "Permanent" : "Confirmed");
                item.put("type", code.type.name());
                item.put("hasFreezeFrame", code.hasFreeze);
                item.put("dtcNumber", code.dtcNumber);
                codesArray.put(item);
            }
        }
        root.put("codes", codesArray);

        String jsonString = root.toString(2);
        return ReportFileWriter.writeText(context, filename, MIME_TYPE_JSON, jsonString);
    }

    private FaultCodeSummary buildSummary(List<FaultCodeService.FaultCodeInfo> codes) {
        VehicleManager vm = VehicleManager.getInstance();
        VehicleData vehicleData = vm.getCurrentVehicleData();
        String vin = firstNonEmpty(vm.getCurrentVIN(), "UNKNOWN");
        String vehicleName = vehicleData != null && !TextUtils.isEmpty(vehicleData.getDisplayName())
                ? vehicleData.getDisplayName()
                : "Unknown Vehicle";

        int confirmed = 0;
        int pending = 0;
        int permanent = 0;
        if (codes != null) {
            for (FaultCodeService.FaultCodeInfo info : codes) {
                if (info.type == FaultCodeService.CodeType.PERMANENT) {
                    permanent++;
                } else if (info.isPending || info.type == FaultCodeService.CodeType.PENDING) {
                    pending++;
                } else {
                    confirmed++;
                }
            }
        }

        return new FaultCodeSummary(
                vin,
                vehicleName,
                confirmed + pending + permanent,
                confirmed,
                pending,
                permanent,
                new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date())
        );
    }

    private static String buildSummaryLine(FaultCodeSummary summary, String subtitle) {
        StringBuilder sb = new StringBuilder();
        sb.append("Confirmed: ").append(summary.confirmed)
          .append(" | Pending: ").append(summary.pending)
          .append(" | Permanent: ").append(summary.permanent);
        if (!TextUtils.isEmpty(subtitle)) {
            sb.append(" | ").append(subtitle);
        }
        return sb.toString();
    }

    private static String buildFilename(String vin, String extension) {
        String normalizedVin = TextUtils.isEmpty(vin) ? "unknown" : vin;
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        return "fault_codes_" + normalizedVin + "_" + timestamp + "." + extension;
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

    private static class FaultCodeSummary {
        final String vin;
        final String vehicleName;
        final int total;
        final int confirmed;
        final int pending;
        final int permanent;
        final String generatedAt;

        FaultCodeSummary(String vin,
                         String vehicleName,
                         int total,
                         int confirmed,
                         int pending,
                         int permanent,
                         String generatedAt) {
            this.vin = vin;
            this.vehicleName = vehicleName;
            this.total = total;
            this.confirmed = confirmed;
            this.pending = pending;
            this.permanent = permanent;
            this.generatedAt = generatedAt;
        }
    }
}
