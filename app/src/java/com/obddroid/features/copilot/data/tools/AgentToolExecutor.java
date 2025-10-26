package com.obddroid.features.copilot.data.tools;

import android.content.Context;
import android.util.Log;

import com.obddroid.features.copilot.data.AgentApiClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dispatcher for Agent API tool calls.
 * Routes tool requests to appropriate handlers and returns results.
 *
 * Thread-safe and supports async execution.
 */
public class AgentToolExecutor {

    private static final String TAG = "AgentToolExecutor";

    private final Map<String, AgentTool> tools;
    private final Context context;

    public AgentToolExecutor(Context context) {
        this.context = context.getApplicationContext();
        this.tools = new HashMap<>();
        registerTools();
    }

    /**
     * Execute tool calls from a Run's required_action.
     * Returns list of tool outputs to submit back to the API.
     */
    public List<ToolCallResult> executeToolCalls(JSONObject requiredAction) {
        List<ToolCallResult> results = new ArrayList<>();

        try {
            JSONObject submitToolOutputs = requiredAction.getJSONObject("submit_tool_outputs");
            JSONArray toolCalls = submitToolOutputs.getJSONArray("tool_calls");

            for (int i = 0; i < toolCalls.length(); i++) {
                JSONObject toolCall = toolCalls.getJSONObject(i);
                String toolCallId = toolCall.getString("id");
                JSONObject function = toolCall.getJSONObject("function");
                String toolName = function.getString("name");
                String argumentsJson = function.getString("arguments");

                Log.i(TAG, "Executing tool: " + toolName + " (call ID: " + toolCallId + ")");

                try {
                    JSONObject parameters = new JSONObject(argumentsJson);
                    String output = executeTool(toolName, parameters);
                    results.add(new ToolCallResult(toolCallId, output, true));

                    Log.i(TAG, "Tool " + toolName + " succeeded: " + output.substring(0,
                        Math.min(100, output.length())));

                } catch (Exception e) {
                    String error = "Tool execution failed: " + e.getMessage();
                    results.add(new ToolCallResult(toolCallId, error, false));

                    Log.e(TAG, "Tool " + toolName + " failed", e);
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to parse tool calls from required_action", e);
        }

        return results;
    }

    /**
     * Execute a single tool by name with parameters.
     */
    public String executeTool(String toolName, JSONObject parameters) throws Exception {
        AgentTool tool = tools.get(toolName);

        if (tool == null) {
            throw new Exception("Unknown tool: " + toolName + ". Available tools: " + tools.keySet());
        }

        // Check if tool requires confirmation
        if (tool.requiresConfirmation()) {
            boolean confirmed = parameters.optBoolean("confirm", false);
            if (!confirmed) {
                throw new Exception("User confirmation required. " +
                    tool.getConfirmationMessage(parameters));
            }
        }

        return tool.execute(context, parameters);
    }

    /**
     * Check if a tool requires user confirmation.
     */
    public boolean toolRequiresConfirmation(String toolName) {
        AgentTool tool = tools.get(toolName);
        return tool != null && tool.requiresConfirmation();
    }

    /**
     * Get confirmation message for a tool.
     */
    public String getConfirmationMessage(String toolName, JSONObject parameters) {
        AgentTool tool = tools.get(toolName);
        if (tool != null) {
            return tool.getConfirmationMessage(parameters);
        }
        return "Execute " + toolName + "?";
    }

    private void registerTools() {
        registerTool(new RunFullScanTool());
        registerTool(new GetScanResultsTool());
        registerTool(new ClearFaultCodesTool());
        registerTool(new AnalyzeDtcsTool());
        registerTool(new ExportReportTool());
        registerTool(new OpenScreenTool());

        Log.i(TAG, "Registered " + tools.size() + " tools: " + tools.keySet());
    }

    private void registerTool(AgentTool tool) {
        tools.put(tool.getName(), tool);
    }

    /**
     * Get tool schemas for Assistant registration.
     * Returns list of Tool objects with JSON schemas for the Agent API.
     */
    public List<AgentApiClient.Tool> getToolSchemas() {
        List<AgentApiClient.Tool> toolList = new ArrayList<>();

        try {
            // file_search - Enable retrieval of uploaded scan reports
            toolList.add(new AgentApiClient.Tool("file_search", null));

            // run_full_scan
            JSONObject runFullScanFunc = new JSONObject();
            runFullScanFunc.put("name", "run_full_scan");
            runFullScanFunc.put("description", "Execute comprehensive OBD diagnostic scan including DTCs, live data, freeze frames, and monitors");
            runFullScanFunc.put("parameters", new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject())
                .put("required", new JSONArray())
            );
            toolList.add(new AgentApiClient.Tool("function", runFullScanFunc));

            // get_scan_results
            JSONObject getScanResultsFunc = new JSONObject();
            getScanResultsFunc.put("name", "get_scan_results");
            getScanResultsFunc.put("description", "Retrieve latest scan results or specific scan by ID");
            getScanResultsFunc.put("parameters", new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject())
                .put("required", new JSONArray())
            );
            toolList.add(new AgentApiClient.Tool("function", getScanResultsFunc));

            // clear_fault_codes
            JSONObject clearFaultCodesFunc = new JSONObject();
            clearFaultCodesFunc.put("name", "clear_fault_codes");
            clearFaultCodesFunc.put("description", "Clear diagnostic trouble codes (requires user confirmation)");
            JSONObject clearParams = new JSONObject();
            clearParams.put("type", "object");
            clearParams.put("properties", new JSONObject().put("confirm", new JSONObject()
                .put("type", "boolean")
                .put("description", "User must confirm destructive action")
            ));
            clearParams.put("required", new JSONArray().put("confirm"));
            clearFaultCodesFunc.put("parameters", clearParams);
            toolList.add(new AgentApiClient.Tool("function", clearFaultCodesFunc));

            // analyze_dtcs
            JSONObject analyzeDtcsFunc = new JSONObject();
            analyzeDtcsFunc.put("name", "analyze_dtcs");
            analyzeDtcsFunc.put("description", "Perform AI diagnostic analysis on fault codes with detailed repair recommendations");
            analyzeDtcsFunc.put("parameters", new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject())
                .put("required", new JSONArray())
            );
            toolList.add(new AgentApiClient.Tool("function", analyzeDtcsFunc));

            // export_report
            JSONObject exportReportFunc = new JSONObject();
            exportReportFunc.put("name", "export_report");
            exportReportFunc.put("description", "Generate and share scan report in various formats");
            JSONObject exportParams = new JSONObject();
            exportParams.put("type", "object");
            exportParams.put("properties", new JSONObject()
                .put("format", new JSONObject()
                    .put("type", "string")
                    .put("enum", new JSONArray().put("markdown").put("json").put("zip"))
                    .put("description", "Report format"))
            );
            exportParams.put("required", new JSONArray());
            exportReportFunc.put("parameters", exportParams);
            toolList.add(new AgentApiClient.Tool("function", exportReportFunc));

            // open_screen
            JSONObject openScreenFunc = new JSONObject();
            openScreenFunc.put("name", "open_screen");
            openScreenFunc.put("description", "Navigate to specific app screens");
            JSONObject openScreenParams = new JSONObject();
            openScreenParams.put("type", "object");
            openScreenParams.put("properties", new JSONObject()
                .put("screen", new JSONObject()
                    .put("type", "string")
                    .put("enum", new JSONArray()
                        .put("main")
                        .put("scan")
                        .put("fault_codes")
                        .put("live_data")
                        .put("settings"))
                    .put("description", "Screen to open"))
            );
            openScreenParams.put("required", new JSONArray().put("screen"));
            openScreenFunc.put("parameters", openScreenParams);
            toolList.add(new AgentApiClient.Tool("function", openScreenFunc));

        } catch (Exception e) {
            Log.e(TAG, "Failed to build tool schemas", e);
        }

        return toolList;
    }

    /**
     * Result of a tool call execution.
     */
    public static class ToolCallResult {
        public final String toolCallId;
        public final String output;
        public final boolean success;

        public ToolCallResult(String toolCallId, String output, boolean success) {
            this.toolCallId = toolCallId;
            this.output = output;
            this.success = success;
        }
    }
}
