package com.obddroid.features.csvlogging;

import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Serialises {@link CsvData} segments to disk on a background thread.
 */
final class CsvWriterThread extends HandlerThread {

    private static final String TAG = "CsvWriterThread";

    private final List<CsvData> queue;
    private Handler handler;
    private final File path;
    private File outputFile;
    private final SimpleDateFormat timestampFormatter;
    private OutputStreamWriter writer;
    private boolean alreadyLoggedError;

    CsvWriterThread(File path) {
        super("CsvWriterThread");
        this.queue = new ArrayList<>();
        this.path = path;
        this.timestampFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HHmmss'Z'", Locale.US);
        this.alreadyLoggedError = false;
        testWrite();
    }

    private void testWrite() {
        if (path == null) {
            Log.e(TAG, "External files directory unavailable");
            return;
        }
        try {
            File destination = new File(path, "test.txt");
            OutputStreamWriter test = new OutputStreamWriter(new BufferedOutputStream(new FileOutputStream(destination)));
            test.write("Test write\n");
            test.close();
            //noinspection ResultOfMethodCallIgnored
            destination.delete();
        } catch (IOException e) {
            Log.e(TAG, "Error confirming write permission", e);
        }
    }

    @Override
    protected void onLooperPrepared() {
        super.onLooperPrepared();
        handler = new Handler(getLooper());
    }

    void write(CsvData data) {
        synchronized (queue) {
            queue.add(data);
        }
        if (handler != null) {
            handler.post(this::writeOut);
        }
    }

    private OutputStreamWriter openWriter(CsvData segment) throws IOException {
        if (path == null) {
            throw new IOException("No storage directory available");
        }
        Date timestamp = new Date(segment.getStartTime());
        String filename = "obddroid_" + timestampFormatter.format(timestamp) + ".csv";
        File destination = new File(path, filename);
        this.outputFile = destination;
        return new OutputStreamWriter(new BufferedOutputStream(new FileOutputStream(destination)));
    }

    String getFilename() {
        return outputFile != null ? outputFile.getName() : null;
    }

    File getOutputFile() {
        return outputFile;
    }

    boolean isOpen() {
        return writer != null;
    }

    private void writeOut() {
        CsvData segment;
        synchronized (queue) {
            if (queue.isEmpty()) {
                return;
            }
            segment = queue.remove(0);
        }

        if (segment != null && segment.size() > 0) {
            writeOut(segment);

            synchronized (queue) {
                if (!queue.isEmpty() && handler != null) {
                    handler.removeCallbacks(this::writeOut);
                    handler.post(this::writeOut);
                }
            }
        }
    }

    private void writeOut(CsvData segment) {
        try {
            if (writer != null && segment.hasNewColumns()) {
                writer.close();
                writer = null;
            }

            if (writer == null) {
                writer = openWriter(segment);
                segment.writeOutput(writer, true);
            } else {
                segment.writeOutput(writer, false);
            }
            writer.flush();
        } catch (IOException e) {
            if (!alreadyLoggedError) {
                Log.w(TAG, "Error while outputting csv data", e);
            } else {
                Log.w(TAG, "Error while outputting csv data: " + e.getMessage());
            }
            alreadyLoggedError = true;
        }
    }

    void closeAsync() {
        if (handler != null) {
            handler.post(this::attemptClose);
        }
    }

    private void attemptClose() {
        boolean isEmpty;
        synchronized (queue) {
            isEmpty = queue.isEmpty();
        }
        if (isEmpty) {
            closeNow();
        } else if (handler != null) {
            handler.post(this::attemptClose);
        }
    }

    private void closeNow() {
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException e) {
                Log.w(TAG, "Error while finishing writing csv data", e);
            }
            writer = null;
            outputFile = null;
        }
    }

    @Override
    public boolean quitSafely() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            return super.quitSafely();
        } else {
            if (handler != null) {
                handler.post(this::quit);
            }
            return true;
        }
    }
}
