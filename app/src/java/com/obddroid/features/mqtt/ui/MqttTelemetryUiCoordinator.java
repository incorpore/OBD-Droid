package com.obddroid.features.mqtt.ui;

import android.app.Activity;
import android.content.SharedPreferences;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;

import com.obddroid.R;
import com.obddroid.features.common.FeatureToggleNotifier;
import com.obddroid.features.mqtt.MqttTelemetryManager;
import com.obddroid.utils.SnackbarHelper;

/**
 * Orchestrates MQTT telemetry interactions with the host activity, keeping UI
 * concerns (snackbars, dialogs, preference flags) alongside the feature code.
 */
public final class MqttTelemetryUiCoordinator {

    private final Activity host;
    private final FeatureToggleNotifier notifier;
    private final Runnable menuInvalidator;
    private final SharedPreferences preferences;
    private final MqttTelemetryManager telemetryManager;

    private String lastStatusCode = "";
    private String lastStatusMessage = "";

    public MqttTelemetryUiCoordinator(Activity host,
                                      FeatureToggleNotifier notifier,
                                      Runnable menuInvalidator) {
        this.host = host;
        this.notifier = notifier;
        this.menuInvalidator = menuInvalidator;
        this.preferences = PreferenceManager.getDefaultSharedPreferences(host);
        this.telemetryManager = new MqttTelemetryManager(host);
        this.telemetryManager.setStatusListener(this::handleStatusUpdate);
    }

    public boolean isActive() {
        return telemetryManager.isActive();
    }

    public void togglePublisher() {
        if (telemetryManager.isActive()) {
            telemetryManager.stop();
            preferences.edit()
                .putBoolean(MqttTelemetryManager.PREF_ENABLED_STATE, false)
                .apply();
            notifier.showToggle(false, R.string.mqtt_publisher_stopped);
        } else {
            if (telemetryManager.start()) {
                preferences.edit()
                    .putBoolean(MqttTelemetryManager.PREF_ENABLED_STATE, true)
                    .apply();
                notifier.showToggle(true, R.string.mqtt_publisher_started);
            } else {
                preferences.edit()
                    .putBoolean(MqttTelemetryManager.PREF_ENABLED_STATE, false)
                    .apply();
                showConfigurationDialog();
            }
        }
        menuInvalidator.run();
    }

    public void release() {
        telemetryManager.setStatusListener(null);
        telemetryManager.stop();
    }

    private void handleStatusUpdate(String statusCode, String detail) {
        if (statusCode == null) {
            statusCode = MqttTelemetryManager.STATUS_IDLE;
        }
        if (detail == null) {
            detail = "";
        }

        if (statusCode.equals(lastStatusCode) && detail.equals(lastStatusMessage)) {
            return;
        }

        String previousCode = lastStatusCode;
        lastStatusCode = statusCode;
        lastStatusMessage = detail;

        switch (statusCode) {
            case MqttTelemetryManager.STATUS_FAILURE: {
                String reason = detail.trim().isEmpty()
                    ? host.getString(R.string.mqtt_status_error_unknown)
                    : detail.trim();
                SnackbarHelper.showError(host, host.getString(R.string.mqtt_status_snackbar_failure, reason));
                break;
            }
            case MqttTelemetryManager.STATUS_SUCCESS: {
                if (!MqttTelemetryManager.STATUS_FAILURE.equals(previousCode)) {
                    return;
                }
                SnackbarHelper.showSuccess(host, host.getString(R.string.mqtt_status_snackbar_recovered));
                break;
            }
            default:
                break;
        }
    }

    private void showConfigurationDialog() {
        new AlertDialog.Builder(host)
            .setTitle(R.string.mqtt_not_configured_title)
            .setMessage(host.getString(R.string.mqtt_not_configured_message))
            .setPositiveButton(R.string.mqtt_not_configured_open_settings, (dialog, which) -> {
                try {
                    host.startActivity(new android.content.Intent(host, com.obddroid.ui.activities.SettingsActivity.class));
                } catch (android.content.ActivityNotFoundException ignored) {
                    // Settings screen unavailable; nothing else to do.
                }
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }
}
