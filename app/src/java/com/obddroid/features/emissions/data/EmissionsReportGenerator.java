package com.obddroid.features.emissions.data;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Generator for emissions report exports
 *
 * Provides data formatting for CSV, JSON exports.
 * Does not handle file I/O - returns formatted strings/objects
 * that can be written by the Activity.
 *
 * This class has no Android file I/O dependencies and can be unit tested.
 */
public class EmissionsReportGenerator {

    /**
     * Generate CSV report content
     *
     * @param monitorDataMap Map of monitors
     * @return CSV content as string
     */
    public static String generateCSV(Map<String, MonitorData> monitorDataMap) {
        StringBuilder csv = new StringBuilder();
        String displayTimestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());

        // Compute summary stats
        EmissionsCalculator.ReadinessStatus status =
            EmissionsCalculator.calculateReadiness(monitorDataMap);

        // Header
        csv.append(String.format(Locale.US,
                "OBD-Droid Emissions Report,%s\n", displayTimestamp));
        csv.append(String.format(Locale.US,
                "Available Monitors,%d\nReady Monitors,%d\nMonitors Needing Drive Cycle,%d\n\n",
                status.availableCount, status.readyCount, status.notReadyCount));
        csv.append("Monitor,Available,Ready,Completions,Conditions,Completion %,IUMPR Quality\n");

        // Monitor data rows
        for (MonitorData monitor : getOrderedMonitorList(monitorDataMap)) {
            if (monitor == null) {
                continue;
            }
            String completionPercent = monitor.getPercentageDisplay();
            String quality = monitor.getIUMPRQuality();
            // Avoid commas disrupting CSV by replacing with semicolons
            if (quality != null) {
                quality = quality.replace(",", ";");
            } else {
                quality = "";
            }

            csv.append(String.format(Locale.US,
                    "\"%s\",%s,%s,%d,%d,%s,%s\n",
                    monitor.name,
                    monitor.isAvailable ? "Yes" : "No",
                    monitor.isReady ? "Yes" : "No",
                    monitor.completions,
                    monitor.conditions,
                    completionPercent,
                    quality));
        }

        return csv.toString();
    }

    /**
     * Generate JSON report content
     *
     * @param monitorDataMap Map of monitors
     * @return JSONObject containing report data
     * @throws JSONException if JSON creation fails
     */
    public static JSONObject generateJSON(Map<String, MonitorData> monitorDataMap) throws JSONException {
        String displayTimestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());

        // Compute summary stats
        EmissionsCalculator.ReadinessStatus status =
            EmissionsCalculator.calculateReadiness(monitorDataMap);

        // Build JSON structure
        JSONObject root = new JSONObject();
        root.put("report_type", "OBD-Droid Emissions Report");
        root.put("generated_at", displayTimestamp);
        root.put("timestamp", new Date().getTime());

        // Summary section
        JSONObject summary = new JSONObject();
        summary.put("available_monitors", status.availableCount);
        summary.put("ready_monitors", status.readyCount);
        summary.put("not_ready_monitors", status.notReadyCount);
        summary.put("is_ready_for_test", status.isReady);
        summary.put("passes_epa_standards", status.isReady);
        root.put("summary", summary);

        // Monitors array
        JSONArray monitorsArray = new JSONArray();
        for (MonitorData monitor : getOrderedMonitorList(monitorDataMap)) {
            if (monitor == null) continue;

            JSONObject monitorObj = new JSONObject();
            monitorObj.put("name", monitor.name);
            monitorObj.put("available", monitor.isAvailable);
            monitorObj.put("ready", monitor.isReady);
            monitorObj.put("completions", monitor.completions);
            monitorObj.put("conditions", monitor.conditions);
            monitorObj.put("completion_percentage", monitor.getPercentageDisplay());

            String quality = monitor.getIUMPRQuality();
            monitorObj.put("iumpr_quality", quality != null ? quality : "");

            monitorsArray.put(monitorObj);
        }
        root.put("monitors", monitorsArray);

        return root;
    }

    /**
     * Get ordered list of monitors for export
     *
     * Order:
     * 1. Continuous monitors (MISFIRE, FUEL, CCM)
     * 2. Non-continuous monitors (CATALYST, EVAP, O2SENSOR, etc.)
     *
     * @param monitorDataMap Map of monitors
     * @return Ordered list of MonitorData objects
     */
    private static List<MonitorData> getOrderedMonitorList(Map<String, MonitorData> monitorDataMap) {
        List<MonitorData> orderedList = new ArrayList<>();

        // Add continuous monitors first
        orderedList.add(monitorDataMap.get("MISFIRE"));
        orderedList.add(monitorDataMap.get("FUEL"));
        orderedList.add(monitorDataMap.get("CCM"));

        // Add non-continuous monitors
        orderedList.add(monitorDataMap.get("CATALYST"));
        orderedList.add(monitorDataMap.get("EVAP"));
        orderedList.add(monitorDataMap.get("O2SENSOR"));
        orderedList.add(monitorDataMap.get("O2HEATER"));
        orderedList.add(monitorDataMap.get("EGR"));
        orderedList.add(monitorDataMap.get("AIR"));

        return orderedList;
    }

    /**
     * Generate filename for export
     *
     * @param extension File extension (e.g., "csv", "json", "pdf")
     * @return Formatted filename with timestamp
     */
    public static String generateFilename(String extension) {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        return "emissions_report_" + timestamp + "." + extension;
    }

    /**
     * Get display timestamp for reports
     *
     * @return Formatted timestamp string
     */
    public static String getDisplayTimestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
    }
}
