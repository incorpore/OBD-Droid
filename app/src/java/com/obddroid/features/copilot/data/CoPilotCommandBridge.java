package com.obddroid.features.copilot.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Dispatches CoPilot-triggered commands into application actions.
 * For now most commands are stubs but the bridge centralises permission checks.
 */
final class CoPilotCommandBridge {

    enum Command {
        RUN_FULL_SCAN("run_full_scan"),
        OPEN_EMISSIONS("open_emissions"),
        SHARE_LATEST_REPORT("share_latest_report"),
        CLEAR_FAULT_CODES("clear_fault_codes"),
        HELP("help");

        private final String id;

        Command(String id) {
            this.id = id;
        }

        String id() {
            return id;
        }

        static @Nullable Command fromId(String id) {
            for (Command command : values()) {
                if (command.id.equalsIgnoreCase(id)) {
                    return command;
                }
            }
            return null;
        }
    }

    static final class CommandResult {
        private final boolean success;
        private final String message;

        CommandResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }
    }

    private static final List<Command> AVAILABLE_COMMANDS = Collections.unmodifiableList(
        Arrays.asList(Command.RUN_FULL_SCAN,
            Command.OPEN_EMISSIONS,
            Command.SHARE_LATEST_REPORT,
            Command.CLEAR_FAULT_CODES,
            Command.HELP));

    List<Command> getAvailableCommands() {
        return AVAILABLE_COMMANDS;
    }

    CommandResult execute(Context context, String commandId, @Nullable JSONObject args) {
        Command command = Command.fromId(commandId);
        if (command == null) {
            return new CommandResult(false, "Unknown command: " + commandId);
        }

        switch (command) {
            case RUN_FULL_SCAN:
                // Actual orchestration will be wired later; for now just acknowledge.
                return new CommandResult(false,
                    "Full scan orchestration not yet wired. Use the dashboard action instead.");
            case OPEN_EMISSIONS:
                return new CommandResult(false,
                    "Emissions screen shortcut will be enabled in a future revision.");
            case SHARE_LATEST_REPORT:
                return new CommandResult(false,
                    "Report sharing is not yet available via CoPilot commands.");
            case CLEAR_FAULT_CODES:
                return new CommandResult(false,
                    "Fault code clearing requires explicit user confirmation – use the dashboard for now.");
            case HELP:
            default:
                return new CommandResult(true,
                    "Available commands: " + getCommandListAsString());
        }
    }

    private String getCommandListAsString() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < AVAILABLE_COMMANDS.size(); i++) {
            builder.append(AVAILABLE_COMMANDS.get(i).id());
            if (i < AVAILABLE_COMMANDS.size() - 1) {
                builder.append(", ");
            }
        }
        return builder.toString();
    }

    JSONObject describeCommands() {
        JSONObject root = new JSONObject();
        for (Command command : AVAILABLE_COMMANDS) {
            try {
                JSONObject meta = new JSONObject();
                switch (command) {
                    case RUN_FULL_SCAN:
                        meta.put("description", "Start an unattended full diagnostic scan.");
                        break;
                    case OPEN_EMISSIONS:
                        meta.put("description", "Navigate to the emissions readiness screen.");
                        break;
                    case SHARE_LATEST_REPORT:
                        meta.put("description", "Share the most recent scan report.");
                        break;
                    case CLEAR_FAULT_CODES:
                        meta.put("description", "Clear stored diagnostic trouble codes (requires confirmation).");
                        break;
                    case HELP:
                    default:
                        meta.put("description", "List available commands and usage guidance.");
                        break;
                }
                root.put(command.id(), meta);
            } catch (JSONException ignored) {
            }
        }
        return root;
    }
}
