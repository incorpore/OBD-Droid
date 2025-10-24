package com.obddroid.vehicle.discovery;

import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Represents a single entry in the discovery log.
 */
final class DiscoveryEvent {

    private final long timestampMs;
    private final String sessionId;
    private final DiscoveryEventType type;
    private final String message;
    private final Integer ecuAddress;
    private final JSONObject data;

    DiscoveryEvent(String sessionId,
                   DiscoveryEventType type,
                   @Nullable String message,
                   @Nullable Integer ecuAddress,
                   @Nullable JSONObject data) {
        this.timestampMs = System.currentTimeMillis();
        this.sessionId = sessionId;
        this.type = type;
        this.message = message;
        this.ecuAddress = ecuAddress;
        this.data = data;
    }

    String toJsonLine() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("timestamp", timestampMs);
        json.put("sessionId", sessionId);
        json.put("type", type.name());
        if (message != null && !message.isEmpty()) {
            json.put("message", message);
        }
        if (ecuAddress != null) {
            json.put("ecuAddress", String.format("0x%X", ecuAddress));
        }
        if (data != null && data.length() > 0) {
            json.put("data", data);
        }
        return json.toString();
    }

    String toLogcatString() {
        StringBuilder builder = new StringBuilder();
        builder.append(type.name());
        if (message != null && !message.isEmpty()) {
            builder.append(" | ").append(message);
        }
        if (ecuAddress != null) {
            builder.append(" (addr=").append(String.format("0x%X", ecuAddress)).append(")");
        }
        return builder.toString();
    }
}
