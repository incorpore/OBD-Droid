package com.obddroid.features.copilot.agent.tools;

import android.content.Context;
import android.util.Log;

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
