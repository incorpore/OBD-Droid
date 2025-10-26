package com.obddroid.features.copilot.data.tools;

import android.content.Context;

import com.obddroid.services.CommService;
import com.obddroid.services.FaultCodeService;

import org.json.JSONObject;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Tool handler for clear_fault_codes.
 * Clears all diagnostic trouble codes from the vehicle using OBD Mode 04.
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

        if (CommService.elm == null) {
            throw new Exception("No active OBD connection. Please connect to a vehicle first.");
        }

        // Use FaultCodeService to clear codes (Mode 04)
        FaultCodeService faultCodeService = new FaultCodeService();

        try {
            CompletableFuture<Boolean> clearFuture = faultCodeService.clearFaultCodes();
            Boolean success = clearFuture.get(5, TimeUnit.SECONDS);

            if (success) {
                return "✓ Fault codes cleared successfully!\n\n" +
                    "The following actions were performed:\n" +
                    "• OBD Mode 04 executed (clear DTCs and reset MIL)\n" +
                    "• Confirmed/stored codes removed\n" +
                    "• Pending codes removed\n" +
                    "• Malfunction Indicator Lamp (check engine light) reset\n\n" +
                    "Important Notes:\n" +
                    "• The vehicle's ECU will need to run self-tests to confirm repairs\n" +
                    "• If the underlying issue is not fixed, codes will return\n" +
                    "• Some monitors may show 'Not Ready' status until drive cycle completes\n" +
                    "• You can verify the clear was successful by running a new scan";
            } else {
                return "⚠ Failed to clear fault codes.\n\n" +
                    "The vehicle did not acknowledge the clear command. Possible reasons:\n" +
                    "• Vehicle may not support Mode 04\n" +
                    "• Communication timeout\n" +
                    "• ECU may be busy or not responding\n\n" +
                    "You can try again or use the Fault Codes screen for manual clearing.";
            }
        } catch (TimeoutException e) {
            throw new Exception("Timeout waiting for vehicle response. The clear command may not have completed. " +
                "Please verify codes were cleared by running a new scan.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Exception("Clear operation was interrupted. Please try again.");
        } catch (Exception e) {
            throw new Exception("Error clearing fault codes: " + e.getMessage());
        }
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
