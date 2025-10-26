package com.obddroid.features.copilot;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class CoPilotLogger implements AutoCloseable {

    private final BufferedWriter writer;

    private CoPilotLogger(File file) throws IOException {
        this.writer = new BufferedWriter(new FileWriter(file, true));
    }

    static File resolveLogFile(Context context, String sessionId) {
        File baseDir = new File(context.getExternalFilesDir(null), "logs/copilot");
        if (!baseDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            baseDir.mkdirs();
        }
        String fileName = sessionId != null ? sessionId : generateDefaultName();
        return new File(baseDir, fileName + ".jsonl");
    }

    static CoPilotLogger open(Context context, String sessionId) throws IOException {
        return new CoPilotLogger(resolveLogFile(context, sessionId));
    }

    void logEvent(String type, JSONObject payload) {
        try {
            JSONObject json = payload != null ? new JSONObject(payload.toString()) : new JSONObject();
            json.put("type", type);
            json.put("timestamp", System.currentTimeMillis());
            writer.write(json.toString());
            writer.newLine();
            writer.flush();
        } catch (IOException | JSONException e) {
            // Logging failures shouldn't crash the app.
        }
    }

    private static String generateDefaultName() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
        return "copilot_" + sdf.format(new Date());
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }
}
