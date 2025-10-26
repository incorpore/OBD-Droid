package com.obddroid.features.copilot.agent.tools;

import android.content.Context;

import com.obddroid.obd.ObdProt;
import com.obddroid.services.CommService;

import org.json.JSONObject;

/**
 * Tool handler for clear_fault_codes.
 * Clears all diagnostic trouble codes from the vehicle.
 * REQUIRES USER CONFIRMATION.
 */
public class ClearFaultCodesTool implements AgentTool {

    @Override
    public String getName() {
        return "clear_fault_codes";
    }

    @Override
    public String execute(Context context, JSONObject parameters) throws Exception {
        boolean confirm = parameters.optBoolean("confirm", false);

        if (!confirm) {
            throw new Exception("User confirmation required to clear fault codes. " +
                "This is a destructive action that will erase diagnostic history.");
        }

        ObdProt elm = CommService.elm;
        if (elm == null) {
            throw new Exception("No active OBD connection. Please connect to a vehicle first.");
        }

        // TODO: Execute Mode 04: Clear DTCs
        // For now, return placeholder since direct ELM access needs refactoring
        return "Clear fault codes feature is being implemented. " +
            "This will execute OBD Mode 04 to clear all DTCs and reset MIL. " +
            "Please use the Fault Codes screen for now.";
    }

    @Override
    public boolean requiresConfirmation() {
        return true;
    }

    @Override
    public String getConfirmationMessage(JSONObject parameters) {
        return "Clear all fault codes? This will erase diagnostic trouble code history and " +
            "may turn off the check engine light. This action cannot be undone.";
    }
}
