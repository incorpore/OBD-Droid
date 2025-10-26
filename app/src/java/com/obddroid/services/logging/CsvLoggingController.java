package com.obddroid.services.logging;

import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.content.ContextCompat;

/**
 * Entry-point helpers for starting or stopping the CSV logging foreground service.
 */
public final class CsvLoggingController {

    private CsvLoggingController() {
        // utility holder
    }

    public static void toggleLogging(Context context) {
        if (CsvLoggingState.isRecording()) {
            stopLogging(context);
        } else {
            startLogging(context);
        }
    }

    public static void startLogging(Context context) {
        Intent intent = new Intent(context, CsvLoggingService.class);
        intent.setAction(CsvLoggingService.ACTION_START);
        startService(context, intent);
    }

    public static void stopLogging(Context context) {
        Intent intent = new Intent(context, CsvLoggingService.class);
        intent.setAction(CsvLoggingService.ACTION_STOP);
        startService(context, intent);
    }

    private static void startService(Context context, Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(context, intent);
        } else {
            context.startService(intent);
        }
    }
}
