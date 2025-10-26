package com.obddroid.features.copilot.agent.tools;

import android.content.Context;

import org.json.JSONObject;

/**
 * Base interface for Agent API tool implementations.
 * Each tool executes a specific function and returns a result string.
 */
public interface AgentTool {

    /**
     * Get the tool name (must match JSON schema).
     */
    String getName();

    /**
     * Execute the tool with the provided parameters.
     *
     * @param context Android context
     * @param parameters Tool parameters from the AI (JSON object)
     * @return Result string to send back to the AI
     * @throws Exception if tool execution fails
     */
    String execute(Context context, JSONObject parameters) throws Exception;

    /**
     * Check if this tool requires user confirmation before executing.
     * Tools like clear_fault_codes should return true.
     */
    default boolean requiresConfirmation() {
        return false;
    }

    /**
     * Get a human-readable description of what this tool will do with the given parameters.
     * Used for confirmation dialogs.
     */
    default String getConfirmationMessage(JSONObject parameters) {
        return "Execute " + getName() + "?";
    }
}
