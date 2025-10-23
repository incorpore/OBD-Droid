package com.obddroid.features.csvlogging;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.FileProvider;

import com.obddroid.R;
import com.obddroid.core.ecu.EcuDataPv;
import com.obddroid.core.obd.ObdProt;
import com.obddroid.core.pvs.ProcessVariables.PvChange;
import com.obddroid.core.pvs.ProcessVariables.PvChangeEvent;
import com.obddroid.core.pvs.ProcessVariables.PvChangeListener;
import com.obddroid.core.pvs.ProcessVariables.PvChangeType;
import com.obddroid.core.pvs.ProcessVariables.TypedPvChangeListener;
import com.obddroid.ui.activities.MainActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Foreground service that records live PID updates into CSV files.
 */
public class CsvLoggingService extends Service implements TypedPvChangeListener {

    public static final String ACTION_START = "com.obddroid.action.CSV_LOGGING_START";
    public static final String ACTION_STOP = "com.obddroid.action.CSV_LOGGING_STOP";
    public static final String ACTION_STATUS_BROADCAST = "com.obddroid.action.CSV_LOGGING_STATUS";
    public static final String EXTRA_STATUS = "status";
    public static final String EXTRA_FILE_NAME = "file_name";

    public static final int STATUS_STARTED = 1;
    public static final int STATUS_STOPPED = 2;
    public static final int STATUS_AUTO_PAUSED = 3;
    public static final int STATUS_ERROR = 4;

    private static final String CHANNEL_ID = "csv_logging";
    private static final int NOTIFICATION_ID = 3433;
    private static final int MIN_ROW_TIMEOUT_MS = 100;
    private static final int MAX_SEGMENT_TIMEOUT_MS = 10_000;
    private static final int AUTOMATIC_PAUSE_MS = 60_000;

    private final Object lock = new Object();
    private final Map<EcuDataPv, PvChangeListener> pvListeners = new ConcurrentHashMap<>();
    private Handler handler;
    private CsvWriterThread writer;
    private CsvData segment;
    private long lastSaved;
    private long lastWritten;

    private final Runnable automaticTimer = new Runnable() {
        @Override
        public void run() {
            boolean shouldAutoPause = false;
            synchronized (lock) {
                if (!CsvLoggingState.isRecording()) {
                    return;
                }
                long now = System.currentTimeMillis();
                if (segment != null && segment.hasPendingData() && now - lastSaved >= MIN_ROW_TIMEOUT_MS) {
                    segment.saveRow();
                    CsvLoggingState.incrementDataRow();
                    lastSaved = now;
                }
                if (segment != null && segment.size() > 0 && now - lastWritten >= MAX_SEGMENT_TIMEOUT_MS) {
                    writeSegmentLocked();
                }
                updateActiveNotificationLocked();
                if (now - lastWritten >= AUTOMATIC_PAUSE_MS) {
                    shouldAutoPause = true;
                }
            }
            if (shouldAutoPause) {
                stopRecordingInternal(true);
            } else if (handler != null) {
                handler.postDelayed(this, 1000);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
    }

    @Override
    public void onDestroy() {
        stopRecordingInternal(false);
        super.onDestroy();
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_STOP.equals(action)) {
            stopRecordingInternal(false);
            return START_NOT_STICKY;
        }
        if (ACTION_START.equals(action)) {
            startRecording();
            return START_STICKY;
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void pvChanged(PvChange change) {
        if (!CsvLoggingState.isRecording()) {
            return;
        }

        PvChangeType primary = change.getPrimaryType();
        if (primary == PvChangeType.ADDED || primary == PvChangeType.MODIFIED) {
            Object candidate = change.getValue();
            if (!(candidate instanceof EcuDataPv)) {
                Object key = change.getKey();
                candidate = key != null ? ObdProt.PidPvs.get(key) : null;
            }
            if (candidate instanceof EcuDataPv) {
                attachToPv((EcuDataPv) candidate);
                synchronized (lock) {
                    refreshColumnsLocked();
                }
            }
        } else if (primary == PvChangeType.CLEARED || primary == PvChangeType.REMOVED) {
            synchronized (lock) {
                detachAllPvsLocked();
                segment = new CsvData();
            }
        }
    }

    private void startRecording() {
        synchronized (lock) {
            if (CsvLoggingState.isRecording()) {
                updateActiveNotificationLocked();
                return;
            }

            File directory = getExternalFilesDir(null);
            if (directory == null) {
                sendStatusBroadcast(STATUS_ERROR, null);
                stopSelf();
                return;
            }

            ensureNotificationChannel();

            segment = new CsvData();
            writer = new CsvWriterThread(directory);
            writer.start();
            CsvLoggingState.resetCounters();
            CsvLoggingState.setLastFileName(null);
            CsvLoggingState.setRecording(true);
            lastSaved = System.currentTimeMillis();
            lastWritten = lastSaved;

            attachExistingPvsLocked();
            ObdProt.PidPvs.addPvChangeListener(this, PvChangeEvent.PV_ALLEVENTS);

            Notification notification = createActiveNotification();
            startForeground(NOTIFICATION_ID, notification);
            handler.postDelayed(automaticTimer, 1000);
        }

        sendStatusBroadcast(STATUS_STARTED, null);
    }

    private void stopRecordingInternal(boolean dueToInactivity) {
        CsvWriterThread writerToClose;
        String fileName = null;
        File outputFile = null;

        synchronized (lock) {
            if (!CsvLoggingState.isRecording()) {
                return;
            }

            if (handler != null) {
                handler.removeCallbacks(automaticTimer);
            }

            if (segment != null && segment.hasPendingData()) {
                segment.saveRow();
                CsvLoggingState.incrementDataRow();
            }
            writeSegmentLocked();

            ObdProt.PidPvs.removePvChangeListener(this);
            detachAllPvsLocked();

            writerToClose = writer;
            segment = new CsvData();
            writer = null;

            CsvLoggingState.setRecording(false);
        }

        if (writerToClose != null) {
            outputFile = writerToClose.getOutputFile();
            if (outputFile != null) {
                fileName = outputFile.getName();
                CsvLoggingState.setLastFileName(fileName);
            }
            writerToClose.closeAsync();
            writerToClose.quitSafely();
        }

        stopForeground(true);

        if (outputFile != null && outputFile.exists()) {
            showFinishedNotification(outputFile, dueToInactivity);
        }

        if (dueToInactivity) {
            sendStatusBroadcast(STATUS_AUTO_PAUSED, fileName);
        } else {
            sendStatusBroadcast(STATUS_STOPPED, fileName);
        }

        stopSelf();
    }

    private void attachExistingPvsLocked() {
        Collection collection = ObdProt.PidPvs.values();
        if (collection == null) {
            return;
        }
        for (Object value : collection) {
            if (value instanceof EcuDataPv) {
                attachToPv((EcuDataPv) value);
            }
        }
        refreshColumnsLocked();
    }

    private void attachToPv(EcuDataPv pv) {
        if (pv == null || pvListeners.containsKey(pv)) {
            return;
        }
        PvChangeListener listener = event -> {
            if (!CsvLoggingState.isRecording()) {
                return;
            }
            if (EcuDataPv.FIELDS[EcuDataPv.FID_VALUE].equals(event.getKey())) {
                handleDataSample(pv, event.getValue(), event.getTime());
            }
        };
        pv.addPvChangeListener(listener, PvChangeEvent.PV_MODIFIED);
        pvListeners.put(pv, listener);
    }

    private void detachAllPvsLocked() {
        for (Map.Entry<EcuDataPv, PvChangeListener> entry : pvListeners.entrySet()) {
            entry.getKey().removePvChangeListener(entry.getValue());
        }
        pvListeners.clear();
    }

    private void handleDataSample(EcuDataPv pv, Object value, long timestamp) {
        String mnemonic = resolveMnemonic(pv);
        String formattedValue = formatValue(pv, value);
        synchronized (lock) {
            if (segment == null) {
                segment = new CsvData();
            }
            segment.setData(mnemonic, formattedValue);
            CsvLoggingState.incrementDataPoint();
            long now = timestamp != 0 ? timestamp : System.currentTimeMillis();
            if (now - lastSaved >= MIN_ROW_TIMEOUT_MS) {
                segment.saveRow();
                CsvLoggingState.incrementDataRow();
                lastSaved = now;
            }
            if (segment.size() >= 1000) {
                writeSegmentLocked();
            }
        }
    }

    private void writeSegmentLocked() {
        if (writer == null || segment == null || segment.size() == 0) {
            return;
        }
        CsvData current = segment;
        segment = new CsvData(current);
        writer.write(current);
        lastWritten = System.currentTimeMillis();
    }

    private void refreshColumnsLocked() {
        if (segment == null) {
            segment = new CsvData();
        }
        Collection collection = ObdProt.PidPvs.values();
        if (collection == null || collection.isEmpty()) {
            return;
        }
        Set<String> ordered = new LinkedHashSet<>();
        for (Object value : collection) {
            if (value instanceof EcuDataPv) {
                ordered.add(resolveMnemonic((EcuDataPv) value));
            }
        }
        if (!ordered.isEmpty()) {
            segment.setColumns(new ArrayList<>(ordered));
        }
    }

    private String resolveMnemonic(EcuDataPv pv) {
        Object mnemonicObj = pv.get(EcuDataPv.FID_MNEMONIC);
        if (mnemonicObj instanceof String && !TextUtils.isEmpty((String) mnemonicObj)) {
            return (String) mnemonicObj;
        }
        Object descriptor = pv.get(EcuDataPv.FID_DESCRIPT);
        if (descriptor instanceof String && !TextUtils.isEmpty((String) descriptor)) {
            return (String) descriptor;
        }
        Object pid = pv.get(EcuDataPv.FID_PID);
        return "PID_" + pid;
    }

    private String formatValue(EcuDataPv pv, Object value) {
        if (value == null) {
            return "";
        }
        return String.valueOf(value);
    }

    private void ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.csv_logging_title),
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription(getString(R.string.csv_logging_channel_description));
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createActiveNotification() {
        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
            this,
            100,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent stopIntent = new Intent(this, CsvLoggingService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPendingIntent = PendingIntent.getService(
            this,
            101,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String text = getString(
            R.string.csv_logging_notification_text,
            CsvLoggingState.getDataPoints(),
            CsvLoggingState.getDataRows()
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.csv_logging_notification_title))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_csv_24)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setShowWhen(false)
            .addAction(
                R.drawable.ic_save_24,
                getString(R.string.csv_logging_stop),
                stopPendingIntent
            )
            .build();
    }

    private void updateActiveNotificationLocked() {
        Notification notification = createActiveNotification();
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification);
    }

    private void showFinishedNotification(File file, boolean dueToInactivity) {
        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
            this,
            200,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Uri uri = FileProvider.getUriForFile(
            this,
            getPackageName() + ".provider",
            file
        );
        Intent shareIntent = new Intent(Intent.ACTION_SEND)
            .setType("text/csv")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        PendingIntent sharePendingIntent = PendingIntent.getActivity(
            this,
            201,
            Intent.createChooser(shareIntent, getString(R.string.csv_logging_share)),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String contentText = getString(R.string.csv_logging_finished_text, file.getName());
        if (dueToInactivity) {
            contentText = getString(R.string.csv_logging_auto_paused, file.getName());
        }

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.csv_logging_notification_title))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_csv_24)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .addAction(
                R.drawable.ic_share_24,
                getString(R.string.csv_logging_share),
                sharePendingIntent
            )
            .build();

        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification);
    }

    private void sendStatusBroadcast(int status, @Nullable String fileName) {
        Intent broadcast = new Intent(ACTION_STATUS_BROADCAST);
        broadcast.setPackage(getPackageName());
        broadcast.putExtra(EXTRA_STATUS, status);
        if (fileName != null) {
            broadcast.putExtra(EXTRA_FILE_NAME, fileName);
        }
        sendBroadcast(broadcast);
    }
}
