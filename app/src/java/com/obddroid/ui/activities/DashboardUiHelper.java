package com.obddroid.ui.activities;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.obddroid.R;
import com.obddroid.core.obd.ElmProt;
import com.obddroid.core.obd.ObdProt;
import com.obddroid.utils.SnackbarHelper;

import java.util.logging.Logger;

/**
 * Helper utilities for configuring the dashboard cards shown on the main screen.
 */
final class DashboardUiHelper {
    private static final Logger log = Logger.getLogger(DashboardUiHelper.class.getName());

    private DashboardUiHelper() {
        // Utility class
    }

    static void setupDashboardCards(MainActivity activity) {
        // Live Data card
        View liveDataCard = activity.findViewById(R.id.card_live_data);
        if (liveDataCard != null) {
            addCardPressAnimation(liveDataCard);
            liveDataCard.setOnClickListener(v -> {
                log.info("Live Data card clicked!");
                ElmProt.STAT ecuState = activity.getEcuConnectionState();
                if (ecuState == ElmProt.STAT.ECU_DETECTED || ecuState == ElmProt.STAT.CONNECTED) {
                    activity.setObdService(ObdProt.OBD_SVC_DATA, "Live Data");
                } else {
                    SnackbarHelper.showWarning(activity, "Please connect to vehicle first");
                }
            });
        } else {
            log.warning("Live Data card NOT found!");
        }

        // Test Control card
        View testControlCard = activity.findViewById(R.id.card_test_control);
        if (testControlCard != null) {
            addCardPressAnimation(testControlCard);
            testControlCard.setOnClickListener(v -> {
                log.info("Test Control card clicked!");
                ElmProt.STAT ecuState = activity.getEcuConnectionState();
                if (ecuState == ElmProt.STAT.ECU_DETECTED ||
                    ecuState == ElmProt.STAT.CONNECTED ||
                    ecuState == ElmProt.STAT.ECU_SELECTED) {
                    UnsupportedModeHelper helper = activity.getUnsupportedModeHelper();
                    if (helper != null) {
                        helper.recordPreUnsupportedState(ecuState);
                    }
                    activity.setObdService(ObdProt.OBD_SVC_CTRL_MODE, "Test Control");
                } else {
                    SnackbarHelper.showWarning(activity, "Please connect to vehicle first");
                }
            });
        } else {
            log.warning("Test Control card NOT found!");
        }

        // Fault Codes card
        View faultCodesCard = activity.findViewById(R.id.card_fault_codes);
        if (faultCodesCard != null) {
            addCardPressAnimation(faultCodesCard);
            faultCodesCard.setOnClickListener(v -> {
                ElmProt.STAT ecuState = activity.getEcuConnectionState();
                if (ecuState == ElmProt.STAT.ECU_DETECTED || ecuState == ElmProt.STAT.CONNECTED) {
                    activity.setObdService(ObdProt.OBD_SVC_READ_CODES, "Fault Codes");
                } else {
                    SnackbarHelper.showWarning(activity, "Please connect to vehicle first");
                }
            });
        }

        // Reconnect card
        View reconnectCard = activity.findViewById(R.id.card_reconnect_adapter);
        if (reconnectCard != null) {
            addCardPressAnimation(reconnectCard);
            long lastReconnectTime = activity.getLastReconnectTime();
            int cooldownMs = activity.getReconnectCooldownMs();
            long currentTime = System.currentTimeMillis();
            long timeSinceLastReconnect = currentTime - lastReconnectTime;

            if (timeSinceLastReconnect < cooldownMs && lastReconnectTime > 0) {
                reconnectCard.setEnabled(false);
                reconnectCard.setAlpha(0.5f);
                long remainingCooldown = cooldownMs - timeSinceLastReconnect;
                log.fine("Reconnect card still in cooldown - " + remainingCooldown + "ms remaining");
                View finalReconnectCard = reconnectCard;
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    finalReconnectCard.setEnabled(true);
                    finalReconnectCard.setAlpha(1.0f);
                    log.info("Reconnect card re-enabled after cooldown");
                }, remainingCooldown);
            } else {
                reconnectCard.setEnabled(true);
                reconnectCard.setAlpha(1.0f);
            }

            reconnectCard.setOnClickListener(v -> activity.reconnectToLastAdapter());
            reconnectCard.setOnLongClickListener(v -> {
                AutoReconnectDialogHelper.show(activity);
                return true;
            });
        }

        updateReconnectCardSubtitle(activity);
        setupBreadcrumbNavigation(activity);
    }

    static void updateReconnectCardSubtitle(MainActivity activity) {
        TextView subtitle = activity.findViewById(R.id.reconnect_adapter_subtitle);
        if (subtitle == null) {
            log.fine("Reconnect card subtitle not found - layout may not be set yet");
            return;
        }

        String lastAdapterType = activity.getPrefs().getString("LAST_ADAPTER_TYPE", null);
        String lastAdapterName = activity.getPrefs().getString("LAST_ADAPTER_NAME", null);
        log.info("updateReconnectCardSubtitle - Last adapter type: " + lastAdapterType + ", name: " + lastAdapterName);

        if (lastAdapterType == null) {
            subtitle.setText("No adapter connected yet");
            return;
        }

        String subtitleText;
        switch (lastAdapterType) {
            case "BLUETOOTH":
                subtitleText = buildBluetoothSubtitle(activity, lastAdapterName);
                break;
            case "NETWORK":
                subtitleText = buildNetworkSubtitle(activity);
                break;
            case "USB":
                subtitleText = "Reconnect to USB adapter";
                break;
            default:
                subtitleText = "No adapter connected yet";
                break;
        }

        subtitle.setText(subtitleText);
    }

    private static String buildBluetoothSubtitle(MainActivity activity, @Nullable String lastAdapterName) {
        String btAddress = activity.getPrefs().getString("LAST_DEV_ADDRESS", null);
        if (btAddress != null) {
            String nickname = activity.getPrefs().getString("device_nickname_" + btAddress, "");
            if (!nickname.isEmpty()) {
                return "Reconnect to " + nickname;
            }
            if (lastAdapterName != null && !lastAdapterName.isEmpty()) {
                return "Reconnect to " + lastAdapterName;
            }
            return "Reconnect to Bluetooth device";
        }
        return "No adapter connected yet";
    }

    private static String buildNetworkSubtitle(MainActivity activity) {
        String ip = activity.getPrefs().getString("DEVICE_ADDRESS", "Unknown");
        int port = activity.getPrefs().getInt("DEVICE_PORT", 35000);
        return ip + ":" + port;
    }

    private static void setupBreadcrumbNavigation(MainActivity activity) {
        ImageView navLeft = activity.findViewById(R.id.breadcrumb_nav_left);
        if (navLeft != null) {
            navLeft.setOnClickListener(v -> log.info("Breadcrumb left navigation clicked (stub)"));
        }

        ImageView navRight = activity.findViewById(R.id.breadcrumb_nav_right);
        if (navRight != null) {
            navRight.setOnClickListener(v -> log.info("Breadcrumb right navigation clicked (stub)"));
        }
    }

    private static void addCardPressAnimation(View card) {
        card.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    v.animate()
                        .scaleX(0.97f)
                        .scaleY(0.97f)
                        .setDuration(100)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();
                    break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    v.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();
                    break;
            }
            return false;
        });
    }
}
