package com.obddroid.ui.coordinators;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.obddroid.R;
import com.obddroid.features.common.FeatureToggleNotifier;
import com.obddroid.services.logging.CsvLoggingService;
import com.obddroid.utils.SnackbarHelper;

import java.util.Objects;

/**
 * Handles UI integration points for CSV logging – namely listening for the
 * {@link CsvLoggingService} status broadcasts and surfacing snackbars above the
 * {@code VehicleInfoFooter}. This keeps {@link com.obddroid.ui.activities.MainActivity}
 * light while still reusing existing feature infrastructure under
 * {@code com.obddroid.features.csvlogging.data}.
 */
public final class CsvLoggingUiCoordinator {

    private final Activity host;
    private final FeatureToggleNotifier notifier;
    private final Runnable menuInvalidator;

    private boolean receiverRegistered;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context context, android.content.Intent intent) {
            if (!Objects.equals(intent.getAction(), CsvLoggingService.ACTION_STATUS_BROADCAST)) {
                return;
            }
            menuInvalidator.run();

            final int status = intent.getIntExtra(
                CsvLoggingService.EXTRA_STATUS,
                CsvLoggingService.STATUS_STOPPED
            );
            final String fileName = intent.getStringExtra(CsvLoggingService.EXTRA_FILE_NAME);

            switch (status) {
                case CsvLoggingService.STATUS_STARTED:
                    notifier.showToggle(true, R.string.csv_logging_started);
                    break;
                case CsvLoggingService.STATUS_STOPPED:
                    notifier.showToggle(false, R.string.csv_logging_stopped);
                    if (fileName != null && !fileName.isEmpty()) {
                        SnackbarHelper.showSuccess(host, host.getString(R.string.csv_logging_finished_text, fileName));
                    }
                    break;
                case CsvLoggingService.STATUS_AUTO_PAUSED:
                    SnackbarHelper.showWarning(host, host.getString(R.string.csv_logging_auto_pause_message));
                    if (fileName != null && !fileName.isEmpty()) {
                        SnackbarHelper.showInfo(host, host.getString(R.string.csv_logging_finished_text, fileName));
                    }
                    break;
                case CsvLoggingService.STATUS_ERROR:
                    SnackbarHelper.showError(host, host.getString(R.string.csv_logging_error));
                    break;
                default:
                    break;
            }
        }
    };

    public CsvLoggingUiCoordinator(Activity host,
                                   FeatureToggleNotifier notifier,
                                   Runnable menuInvalidator) {
        this.host = host;
        this.notifier = notifier;
        this.menuInvalidator = menuInvalidator;
    }

    public void onResume() {
        if (receiverRegistered) {
            return;
        }
        IntentFilter filter = new IntentFilter(CsvLoggingService.ACTION_STATUS_BROADCAST);
        ContextCompat.registerReceiver(
            host,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        );
        receiverRegistered = true;
    }

    public void onPause() {
        if (!receiverRegistered) {
            return;
        }
        try {
            host.unregisterReceiver(receiver);
        } catch (IllegalArgumentException ignored) {
            // Receiver already unregistered – safe to ignore.
        }
        receiverRegistered = false;
    }

    public void onDestroy() {
        onPause();
    }
}
