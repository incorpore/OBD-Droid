package com.obddroid.features.copilot.agent;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Registry of tools (functions) available to the OBD-Droid AI assistant.
 * Each tool defines a JSON schema that the AI can call to perform actions.
 *
 * Tools registered:
 * - run_full_scan: Execute comprehensive OBD diagnostic scan
 * - get_scan_results: Retrieve latest scan results
 * - clear_fault_codes: Clear diagnostic trouble codes
 * - analyze_dtcs: Run AI diagnostic analysis
 * - export_report: Generate and share scan report
 * - open_screen: Navigate to app screens
 */
public class AgentToolRegistry {

    /**
     * Get all registered tools as AgentApiClient.Tool objects.
     */
    public static List<AgentApiClient.Tool> getAllTools() throws JSONException {
        List<AgentApiClient.Tool> tools = new ArrayList<>();

        // Add file_search for scan history retrieval
        tools.add(createFileSearchTool());

        // Add function tools
        tools.add(createRunFullScanTool());
        tools.add(createGetScanResultsTool());
        tools.add(createClearFaultCodesTool());
        tools.add(createAnalyzeDtcsTool());
        tools.add(createExportReportTool());
        tools.add(createOpenScreenTool());

        return tools;
    }

    // ========== TOOL DEFINITIONS ==========

    private static AgentApiClient.Tool createFileSearchTool() {
        return new AgentApiClient.Tool("file_search", null);
    }

    private static AgentApiClient.Tool createRunFullScanTool() throws JSONException {
        JSONObject function = new JSONObject();
        function.put("name", "run_full_scan");
        function.put("description", "Execute a comprehensive OBD diagnostic scan including DTCs, " +
            "live data, freeze frames, monitors, and vehicle information. " +
            "This takes 30-60 seconds to complete.");

        JSONObject parameters = new JSONObject();
        parameters.put("type", "object");

        JSONObject properties = new JSONObject();
        properties.put("include_live_data", createBooleanProperty(
            "Capture Mode 01 live data PIDs", true));
        properties.put("include_freeze_frames", createBooleanProperty(
            "Capture Mode 02 freeze frame data", true));

        parameters.put("properties", properties);
        function.put("parameters", parameters);

        return new AgentApiClient.Tool("function", function);
    }

    private static AgentApiClient.Tool createGetScanResultsTool() throws JSONException {
        JSONObject function = new JSONObject();
        function.put("name", "get_scan_results");
        function.put("description", "Retrieve the latest scan results or a specific scan by ID. " +
            "Returns scan metadata, stage results, and any fault codes found.");

        JSONObject parameters = new JSONObject();
        parameters.put("type", "object");

        JSONObject properties = new JSONObject();
        properties.put("scan_id", createStringProperty(
            "Optional: specific scan ID to retrieve. If not provided, returns latest scan.", false));

        parameters.put("properties", properties);
        function.put("parameters", parameters);

        return new AgentApiClient.Tool("function", function);
    }

    private static AgentApiClient.Tool createClearFaultCodesTool() throws JSONException {
        JSONObject function = new JSONObject();
        function.put("name", "clear_fault_codes");
        function.put("description", "Clear all diagnostic trouble codes (DTCs) from the vehicle. " +
            "This is a destructive action that requires user confirmation. " +
            "Only use when explicitly requested by the user.");

        JSONObject parameters = new JSONObject();
        parameters.put("type", "object");

        JSONObject properties = new JSONObject();
        properties.put("confirm", createBooleanProperty(
            "User must explicitly confirm this destructive action", true));

        parameters.put("properties", properties);
        parameters.put("required", new JSONArray().put("confirm"));

        function.put("parameters", parameters);

        return new AgentApiClient.Tool("function", function);
    }

    private static AgentApiClient.Tool createAnalyzeDtcsTool() throws JSONException {
        JSONObject function = new JSONObject();
        function.put("name", "analyze_dtcs");
        function.put("description", "Perform AI diagnostic analysis on fault codes with " +
            "repair recommendations, root cause analysis, and verification steps. " +
            "Uses GPT-4 to analyze scan data.");

        JSONObject parameters = new JSONObject();
        parameters.put("type", "object");

        JSONObject properties = new JSONObject();
        properties.put("detail_level", createEnumProperty(
            "Level of detail for analysis",
            new String[]{"quick", "detailed", "comprehensive"},
            "detailed"));

        parameters.put("properties", properties);
        function.put("parameters", parameters);

        return new AgentApiClient.Tool("function", function);
    }

    private static AgentApiClient.Tool createExportReportTool() throws JSONException {
        JSONObject function = new JSONObject();
        function.put("name", "export_report");
        function.put("description", "Generate and share a scan report in the specified format. " +
            "Creates markdown, JSON, or ZIP bundle for sharing via Android share sheet.");

        JSONObject parameters = new JSONObject();
        parameters.put("type", "object");

        JSONObject properties = new JSONObject();
        properties.put("format", createEnumProperty(
            "Report format to generate",
            new String[]{"markdown", "json", "zip"},
            "zip"));
        properties.put("include_ai_analysis", createBooleanProperty(
            "Include AI diagnostic analysis in report", true));

        parameters.put("properties", properties);
        function.put("parameters", parameters);

        return new AgentApiClient.Tool("function", function);
    }

    private static AgentApiClient.Tool createOpenScreenTool() throws JSONException {
        JSONObject function = new JSONObject();
        function.put("name", "open_screen");
        function.put("description", "Navigate to a specific screen in the OBD-Droid app. " +
            "Available screens: emissions, fault_codes, fuel_economy, live_data, settings, " +
            "vehicle_history, copilot.");

        JSONObject parameters = new JSONObject();
        parameters.put("type", "object");

        JSONObject properties = new JSONObject();
        properties.put("screen", createEnumProperty(
            "Screen to navigate to",
            new String[]{"emissions", "fault_codes", "fuel_economy", "live_data",
                "settings", "vehicle_history", "copilot"},
            null));

        parameters.put("properties", properties);
        parameters.put("required", new JSONArray().put("screen"));

        function.put("parameters", parameters);

        return new AgentApiClient.Tool("function", function);
    }

    // ========== SCHEMA HELPERS ==========

    private static JSONObject createBooleanProperty(String description, boolean defaultValue)
            throws JSONException {
        JSONObject property = new JSONObject();
        property.put("type", "boolean");
        property.put("description", description);
        property.put("default", defaultValue);
        return property;
    }

    private static JSONObject createStringProperty(String description, boolean required)
            throws JSONException {
        JSONObject property = new JSONObject();
        property.put("type", "string");
        property.put("description", description);
        return property;
    }

    private static JSONObject createEnumProperty(String description, String[] options, String defaultValue)
            throws JSONException {
        JSONObject property = new JSONObject();
        property.put("type", "string");
        property.put("description", description);

        JSONArray enumArray = new JSONArray();
        for (String option : options) {
            enumArray.put(option);
        }
        property.put("enum", enumArray);

        if (defaultValue != null) {
            property.put("default", defaultValue);
        }

        return property;
    }
}
