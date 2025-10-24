package com.obddroid.copilot;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class CoPilotSession {

    private final String sessionId;
    private final List<CoPilotMessage> history = new ArrayList<>();
    private final File logFile;
    private final JSONObject metadata;

    CoPilotSession(Context context, String sessionId, JSONObject metadata) {
        this.sessionId = sessionId;
        this.metadata = metadata != null ? metadata : new JSONObject();
        this.logFile = CoPilotLogger.resolveLogFile(context, sessionId);
    }

    String getSessionId() {
        return sessionId;
    }

    List<CoPilotMessage> getHistorySnapshot() {
        return Collections.unmodifiableList(new ArrayList<>(history));
    }

    void appendMessage(CoPilotMessage message) {
        history.add(message);
    }

    File getLogFile() {
        return logFile;
    }

    JSONObject getMetadata() {
        try {
            return new JSONObject(metadata.toString());
        } catch (JSONException e) {
            return new JSONObject();
        }
    }
}
