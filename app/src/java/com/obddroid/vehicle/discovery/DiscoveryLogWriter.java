package com.obddroid.vehicle.discovery;

import android.content.Context;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

/**
 * Writes discovery events to newline-delimited JSON on disk.
 */
final class DiscoveryLogWriter implements AutoCloseable {

    private final File logFile;
    private final Writer writer;
    private final Object lock = new Object();

    DiscoveryLogWriter(Context context, String sessionId) throws IOException {
        File baseDir = new File(context.getExternalFilesDir(null), "logs/discovery");
        if (!baseDir.exists() && !baseDir.mkdirs()) {
            throw new IOException("Unable to create discovery log directory: " + baseDir.getAbsolutePath());
        }
        logFile = new File(baseDir, sessionId + ".jsonl");
        writer = new BufferedWriter(new FileWriter(logFile, true));
    }

    File getLogFile() {
        return logFile;
    }

    void writeEvent(DiscoveryEvent event) throws IOException {
        synchronized (lock) {
            try {
                writer.write(event.toJsonLine());
                writer.write('\n');
                writer.flush();
            } catch (Exception ex) {
                if (ex instanceof IOException) {
                    throw (IOException) ex;
                }
                throw new IOException("Failed to encode discovery event", ex);
            }
        }
    }

    @Override
    public void close() throws IOException {
        synchronized (lock) {
            writer.close();
        }
    }
}
