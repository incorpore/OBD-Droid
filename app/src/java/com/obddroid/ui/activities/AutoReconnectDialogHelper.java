package com.obddroid.ui.activities;

import android.content.SharedPreferences;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;

import com.obddroid.R;
import com.obddroid.utils.SnackbarHelper;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Displays and handles the auto-reconnect settings dialog.
 */
final class AutoReconnectDialogHelper {
    private static final Logger log = Logger.getLogger(AutoReconnectDialogHelper.class.getName());

    private AutoReconnectDialogHelper() {
        // Utility class
    }

    static void show(MainActivity activity) {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
            boolean currentAutoReconnect = prefs.getBoolean("auto_reconnect_on_startup", false);

            View dialogView = activity.getLayoutInflater()
                .inflate(R.layout.dialog_auto_reconnect_settings, null);

            android.widget.CheckBox checkbox = dialogView.findViewById(R.id.checkbox_auto_reconnect);
            android.widget.LinearLayout lastAdapterInfo = dialogView.findViewById(R.id.last_adapter_info);
            TextView lastAdapterName = dialogView.findViewById(R.id.last_adapter_name);
            Button cancelButton = dialogView.findViewById(R.id.btn_cancel);
            Button saveButton = dialogView.findViewById(R.id.btn_save);

            checkbox.setChecked(currentAutoReconnect);

            String lastAdapterType = prefs.getString("LAST_ADAPTER_TYPE", null);
            if (lastAdapterType != null) {
                lastAdapterInfo.setVisibility(View.VISIBLE);
                lastAdapterName.setText(buildAdapterDisplayName(prefs, lastAdapterType));
            } else {
                lastAdapterInfo.setVisibility(View.GONE);
            }

            AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(dialogView)
                .create();

            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            }

            cancelButton.setOnClickListener(v -> dialog.dismiss());
            saveButton.setOnClickListener(v -> {
                boolean newValue = checkbox.isChecked();
                prefs.edit().putBoolean("auto_reconnect_on_startup", newValue).apply();

                String message = newValue
                    ? "Auto-reconnect enabled. The app will reconnect on startup."
                    : "Auto-reconnect disabled. You'll need to manually reconnect.";
                SnackbarHelper.showInfo(activity, message);

                log.info("Auto-reconnect setting changed to: " + newValue);
                dialog.dismiss();
            });

            dialog.show();
        } catch (Exception e) {
            log.log(Level.WARNING, "Failed to show auto-reconnect settings dialog", e);
            SnackbarHelper.showError(activity,
                "Unable to open auto-reconnect settings. Please try again.");
        }
    }

    private static String buildAdapterDisplayName(SharedPreferences prefs, String adapterType) {
        switch (adapterType) {
            case "BLUETOOTH":
                String btAddress = prefs.getString("LAST_DEV_ADDRESS", null);
                if (btAddress != null) {
                    String nickname = prefs.getString("device_nickname_" + btAddress, "");
                    if (!nickname.isEmpty()) {
                        return nickname + " (Bluetooth)";
                    }
                    String savedName = prefs.getString("LAST_ADAPTER_NAME", null);
                    if (savedName != null && !savedName.isEmpty()) {
                        return savedName + " (Bluetooth)";
                    }
                }
                return "Bluetooth Device";
            case "NETWORK":
                String ip = prefs.getString("DEVICE_ADDRESS", "Unknown");
                int port = prefs.getInt("DEVICE_PORT", 35000);
                return ip + ":" + port + " (Network)";
            case "USB":
                return "USB Adapter";
            default:
                return "Unknown Adapter";
        }
    }
}
