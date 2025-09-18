package com.obddroid.utils;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.obddroid.R;
import com.obddroid.activities.MainActivity;

/**
 * Helper class for managing notifications and notification channels
 * Handles compatibility with Android O+ notification channels and Android 13+ permission requirements
 */
public class NotificationHelper {

    // Notification channel IDs
    private static final String CHANNEL_ID_CONNECTION = "obd_connection";
    private static final String CHANNEL_ID_DATA = "obd_data";
    private static final String CHANNEL_ID_ERROR = "obd_error";

    // Notification IDs for different notification types
    public static final int NOTIFICATION_ID_CONNECTION = 1001;
    public static final int NOTIFICATION_ID_DATA_LOGGING = 1002;
    public static final int NOTIFICATION_ID_ERROR = 1003;

    // Request code for notification permission
    public static final int REQUEST_NOTIFICATION_PERMISSION = 2001;

    /**
     * Create notification channels for Android O+
     * Must be called on app startup
     *
     * @param context Application context
     */
    public static void createNotificationChannels(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

            if (notificationManager != null) {
                // Connection status channel
                NotificationChannel connectionChannel = new NotificationChannel(
                    CHANNEL_ID_CONNECTION,
                    "Connection Status",
                    NotificationManager.IMPORTANCE_LOW
                );
                connectionChannel.setDescription("Shows OBD adapter connection status");
                connectionChannel.setShowBadge(false);
                notificationManager.createNotificationChannel(connectionChannel);

                // Data logging channel
                NotificationChannel dataChannel = new NotificationChannel(
                    CHANNEL_ID_DATA,
                    "Data Logging",
                    NotificationManager.IMPORTANCE_LOW
                );
                dataChannel.setDescription("Shows data logging status and progress");
                dataChannel.setShowBadge(false);
                notificationManager.createNotificationChannel(dataChannel);

                // Error notification channel
                NotificationChannel errorChannel = new NotificationChannel(
                    CHANNEL_ID_ERROR,
                    "Errors",
                    NotificationManager.IMPORTANCE_DEFAULT
                );
                errorChannel.setDescription("Shows error messages and warnings");
                errorChannel.setShowBadge(true);
                notificationManager.createNotificationChannel(errorChannel);
            }
        }
    }

    /**
     * Request notification permission for Android 13+
     *
     * @param activity Activity context for permission request
     * @return true if permission is already granted or not needed, false if request is made
     */
    public static boolean requestNotificationPermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(activity,
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_NOTIFICATION_PERMISSION);
                return false;
            }
        }
        return true;
    }

    /**
     * Check if notification permission is granted
     *
     * @param context Context
     * @return true if notifications are allowed
     */
    public static boolean areNotificationsEnabled(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ActivityCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }
        // For older versions, check if notifications are enabled
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        return notificationManager.areNotificationsEnabled();
    }

    /**
     * Show a connection status notification
     *
     * @param context Context
     * @param isConnected Connection status
     * @param deviceName Name of connected device (optional)
     */
    public static void showConnectionNotification(Context context, boolean isConnected, String deviceName) {
        if (!areNotificationsEnabled(context)) return;

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);

        if (isConnected) {
            String contentText = deviceName != null ?
                "Connected to " + deviceName : "OBD adapter connected";

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID_CONNECTION)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("OBDroid Connected")
                .setContentText(contentText)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setAutoCancel(false);

            // Add intent to open app when notification is clicked
            Intent intent = new Intent(context, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            builder.setContentIntent(pendingIntent);

            notificationManager.notify(NOTIFICATION_ID_CONNECTION, builder.build());
        } else {
            // Remove connection notification when disconnected
            notificationManager.cancel(NOTIFICATION_ID_CONNECTION);
        }
    }

    /**
     * Show data logging notification
     *
     * @param context Context
     * @param isLogging Logging status
     * @param recordCount Number of records logged
     */
    public static void showDataLoggingNotification(Context context, boolean isLogging, int recordCount) {
        if (!areNotificationsEnabled(context)) return;

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);

        if (isLogging) {
            String contentText = recordCount > 0 ?
                recordCount + " records logged" : "Data logging active";

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID_DATA)
                .setSmallIcon(android.R.drawable.ic_menu_save)
                .setContentTitle("OBDroid Logging")
                .setContentText(contentText)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setAutoCancel(false);

            // Add intent to open app
            Intent intent = new Intent(context, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            builder.setContentIntent(pendingIntent);

            notificationManager.notify(NOTIFICATION_ID_DATA_LOGGING, builder.build());
        } else {
            // Remove logging notification when stopped
            notificationManager.cancel(NOTIFICATION_ID_DATA_LOGGING);
        }
    }

    /**
     * Show error notification
     *
     * @param context Context
     * @param errorTitle Error title
     * @param errorMessage Error message
     */
    public static void showErrorNotification(Context context, String errorTitle, String errorMessage) {
        if (!areNotificationsEnabled(context)) return;

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID_ERROR)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(errorTitle)
            .setContentText(errorMessage)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true);

        // Add intent to open app
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        builder.setContentIntent(pendingIntent);

        notificationManager.notify(NOTIFICATION_ID_ERROR, builder.build());
    }

    /**
     * Cancel all notifications
     *
     * @param context Context
     */
    public static void cancelAllNotifications(Context context) {
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        notificationManager.cancelAll();
    }
}