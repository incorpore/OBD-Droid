package com.obddroid.ui.activities;

import android.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
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
            liveDataCard.setOnLongClickListener(v -> showCardInfoDialog(
                activity,
                "Live Data",
                "Monitor real-time sensor values from the vehicle."
            ));
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
            testControlCard.setOnLongClickListener(v -> showCardInfoDialog(
                activity,
                "Test Control",
                "Run diagnostic tests and actuator controls supported by the vehicle."
            ));
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
            faultCodesCard.setOnLongClickListener(v -> showCardInfoDialog(
                activity,
                "Fault Codes",
                "Read, decode, and clear diagnostic trouble codes stored by the vehicle."
            ));
        }

        // Reconnect card
        View reconnectCard = activity.findViewById(R.id.card_reconnect_adapter);
        if (reconnectCard != null) {
            // Check if there's a previously saved adapter
            String lastAdapterType = activity.getPrefs().getString("LAST_ADAPTER_TYPE", null);

            if (lastAdapterType == null) {
                // No adapter has been connected yet - remove the reconnect card from layout
                ViewGroup parent = (ViewGroup) reconnectCard.getParent();
                if (parent != null) {
                    parent.removeView(reconnectCard);
                    log.info("Reconnect card removed from layout - no previous adapter found");
                }
            } else {
                // Show the card and set up functionality
                reconnectCard.setVisibility(View.VISIBLE);
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
                reconnectCard.setOnLongClickListener(v -> showReconnectInfoDialog(activity));
            }
        }

        // Fuel Economy card
        View fuelEconomyCard = activity.findViewById(R.id.card_fuel_economy);
        if (fuelEconomyCard != null) {
            addCardPressAnimation(fuelEconomyCard);
            fuelEconomyCard.setOnClickListener(v -> {
                log.info("Fuel Economy card clicked!");
                activity.launchFuelEconomyActivity();
            });
            fuelEconomyCard.setOnLongClickListener(v -> showCardInfoDialog(
                activity,
                "Fuel Economy",
                "Track MPG, fuel level, and driving range statistics."
            ));
        } else {
            log.warning("Fuel Economy card NOT found!");
        }

        // Vehicle History card
        View vehicleHistoryCard = activity.findViewById(R.id.card_vehicle_history);
        if (vehicleHistoryCard != null) {
            addCardPressAnimation(vehicleHistoryCard);
            vehicleHistoryCard.setOnClickListener(v -> {
                log.info("Vehicle History card clicked!");
                activity.launchAutoCheckActivity();
            });
            vehicleHistoryCard.setOnLongClickListener(v -> showCardInfoDialog(
                activity,
                "Vehicle History",
                "Get comprehensive vehicle history reports powered by AutoCheck. Check for accidents, ownership history, title status, recalls, and more."
            ));
        } else {
            log.warning("Vehicle History card NOT found!");
        }

        // Safety Recalls card
        View recallsCard = activity.findViewById(R.id.card_vehicle_recalls);
        if (recallsCard != null) {
            addCardPressAnimation(recallsCard);
            recallsCard.setOnClickListener(v -> {
                log.info("Safety Recalls card clicked!");
                activity.launchRecallActivity();
            });
            recallsCard.setOnLongClickListener(v -> showCardInfoDialog(
                activity,
                activity.getString(R.string.safety_recalls),
                "Look up open safety recalls using the official NHTSA database."
            ));
        } else {
            log.warning("Safety Recalls card NOT found!");
        }

        updateReconnectCardSubtitle(activity);
    }

    static void updateReconnectCardSubtitle(MainActivity activity) {
        View reconnectCard = activity.findViewById(R.id.card_reconnect_adapter);
        TextView titleView = activity.findViewById(R.id.reconnect_adapter_title);
        TextView subtitle = activity.findViewById(R.id.reconnect_adapter_subtitle);

        String lastAdapterType = activity.getPrefs().getString("LAST_ADAPTER_TYPE", null);
        String lastAdapterName = activity.getPrefs().getString("LAST_ADAPTER_NAME", null);
        log.info("updateReconnectCardSubtitle - Last adapter type: " + lastAdapterType + ", name: " + lastAdapterName);

        if (lastAdapterType == null) {
            // No adapter saved - remove the card
            if (reconnectCard != null) {
                ViewGroup parent = (ViewGroup) reconnectCard.getParent();
                if (parent != null) {
                    parent.removeView(reconnectCard);
                    log.info("Reconnect card removed during subtitle update - no previous adapter");
                }
            }
            return;
        }

        // Adapter exists - make sure card is visible
        if (reconnectCard != null) {
            reconnectCard.setVisibility(View.VISIBLE);
        }

        if (titleView == null) {
            log.fine("Reconnect card title not found - layout may not be set yet");
            return;
        }

        if (subtitle != null) {
            subtitle.setText("");
            subtitle.setVisibility(View.GONE);
        }

        String titleText;
        switch (lastAdapterType) {
            case "BLUETOOTH":
                titleText = buildBluetoothTitle(activity, lastAdapterName);
                break;
            case "NETWORK":
                titleText = buildNetworkTitle(activity);
                break;
            case "USB":
                titleText = "Reconnect USB adapter";
                break;
            default:
                titleText = null;
                break;
        }

        if (titleText == null || titleText.isEmpty()) {
            titleView.setText("Reconnect Adapter");
        } else {
            titleView.setText(titleText);
        }
    }

    private static String buildBluetoothTitle(MainActivity activity, @Nullable String lastAdapterName) {
        String btAddress = activity.getPrefs().getString("LAST_DEV_ADDRESS", null);
        if (btAddress != null) {
            String nickname = activity.getPrefs().getString("device_nickname_" + btAddress, "");
            if (!nickname.isEmpty()) {
                return "Reconnect " + nickname;
            }
            if (lastAdapterName != null && !lastAdapterName.isEmpty()) {
                return "Reconnect " + lastAdapterName;
            }
            return "Reconnect Bluetooth device";
        }
        return "";
    }

    private static String buildNetworkTitle(MainActivity activity) {
        String ip = activity.getPrefs().getString("DEVICE_ADDRESS", "Unknown");
        int port = activity.getPrefs().getInt("DEVICE_PORT", 35000);
        if (ip == null || ip.isEmpty() || "Unknown".equals(ip)) {
            return "";
        }
        return "Reconnect " + ip + ":" + port;
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

    private static boolean showCardInfoDialog(MainActivity activity, String title, String message) {
        new AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Close", null)
            .show();
        return true;
    }

    private static boolean showReconnectInfoDialog(MainActivity activity) {
        try {
            android.content.SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(activity);
            boolean currentAutoReconnect = prefs.getBoolean("auto_reconnect_on_startup", false);

            View dialogView = activity.getLayoutInflater()
                .inflate(R.layout.dialog_auto_reconnect_settings, null);

            android.widget.CheckBox checkbox = dialogView.findViewById(R.id.checkbox_auto_reconnect);
            android.widget.LinearLayout lastAdapterInfo = dialogView.findViewById(R.id.last_adapter_info);
            TextView lastAdapterName = dialogView.findViewById(R.id.last_adapter_name);
            android.widget.Button closeButton = dialogView.findViewById(R.id.btn_cancel);
            android.widget.Button reconnectButton = dialogView.findViewById(R.id.btn_reconnect);
            android.widget.Button saveButton = dialogView.findViewById(R.id.btn_save);

            checkbox.setChecked(currentAutoReconnect);

            // Show last adapter info if available
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

            closeButton.setOnClickListener(v -> dialog.dismiss());

            reconnectButton.setOnClickListener(v -> {
                dialog.dismiss();
                activity.reconnectToLastAdapter();
            });

            saveButton.setOnClickListener(v -> {
                boolean newValue = checkbox.isChecked();
                prefs.edit().putBoolean("auto_reconnect_on_startup", newValue).apply();

                String message = newValue
                    ? "Auto-reconnect enabled. The app will reconnect on startup."
                    : "Auto-reconnect disabled. You'll need to manually reconnect.";
                SnackbarHelper.showInfo(activity, message);

                dialog.dismiss();
            });

            dialog.show();
        } catch (Exception e) {
            log.warning("Failed to show reconnect dialog: " + e.getMessage());
            SnackbarHelper.showError(activity,
                "Unable to open reconnect settings. Please try again.");
        }
        return true;
    }

    private static String buildAdapterDisplayName(android.content.SharedPreferences prefs, String adapterType) {
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
