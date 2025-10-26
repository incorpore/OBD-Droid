package com.obddroid.ui.activities;

import androidx.appcompat.app.AlertDialog;

import com.obddroid.obd.ElmProt;
import com.obddroid.obd.ObdProt;
import com.obddroid.services.CommService;

import java.util.logging.Logger;

/**
 * Handles detection of unsupported diagnostic modes and user notifications.
 */
final class UnsupportedModeHelper {
    private static final int MAX_CYCLES_BEFORE_ALERT = 3;
    private static final long CYCLE_RESET_TIMEOUT_MS = 5000; // milliseconds

    private final MainActivity activity;
    private final Logger log;

    private ElmProt.STAT lastConnectionState = ElmProt.STAT.UNDEFINED;
    private ElmProt.STAT savedEcuState = ElmProt.STAT.UNDEFINED;
    private int connectionCycleCount = 0;
    private int serviceWhenCycleStarted = ObdProt.OBD_SVC_NONE;
    private long lastCycleTimestamp = 0;
    private AlertDialog unsupportedModeDialog;

    UnsupportedModeHelper(MainActivity activity, Logger log) {
        this.activity = activity;
        this.log = log;
    }

    void recordPreUnsupportedState(ElmProt.STAT state) {
        savedEcuState = state;
        log.info("Saved ECU state before entering potential unsupported mode: " + savedEcuState);
    }

    ElmProt.STAT getSavedEcuState() {
        return savedEcuState;
    }

    void clearSavedEcuState() {
        savedEcuState = ElmProt.STAT.UNDEFINED;
    }

    void onStateChanged(ElmProt.STAT newState, ElmProt.STAT ecuConnectionState) {
        if (CommService.elm == null) {
            log.info("detectConnectionCycles: CommService.elm is null");
            lastConnectionState = newState;
            return;
        }

        int currentService = CommService.elm.getService();
        log.info(String.format(
            "detectConnectionCycles: service=%s (%d), newState=%s, lastState=%s",
            ObdProt.getServiceName(currentService),
            currentService,
            newState,
            lastConnectionState
        ));

        if (currentService != ObdProt.OBD_SVC_CTRL_MODE &&
            currentService != ObdProt.OBD_SVC_MON_RESULT) {
            log.info("detectConnectionCycles: Not Mode 8 or 6, resetting");
            reset();
            lastConnectionState = newState;
            return;
        }

        log.info("detectConnectionCycles: Tracking cycles for this service");

        long currentTime = System.currentTimeMillis();
        if (lastCycleTimestamp > 0 && (currentTime - lastCycleTimestamp) > CYCLE_RESET_TIMEOUT_MS) {
            log.info("Cycle detection timeout - resetting");
            reset();
        }

        boolean isCycle =
            (lastConnectionState == ElmProt.STAT.CONNECTING && newState == ElmProt.STAT.NODATA) ||
            (lastConnectionState == ElmProt.STAT.NODATA && newState == ElmProt.STAT.CONNECTING);

        if (isCycle) {
            log.info("detectConnectionCycles: *** CYCLE DETECTED ***");

            if (serviceWhenCycleStarted == ObdProt.OBD_SVC_NONE) {
                serviceWhenCycleStarted = currentService;
                if (savedEcuState == ElmProt.STAT.UNDEFINED) {
                    if (lastConnectionState == ElmProt.STAT.CONNECTED ||
                        lastConnectionState == ElmProt.STAT.ECU_DETECTED ||
                        lastConnectionState == ElmProt.STAT.ECU_SELECTED) {
                        savedEcuState = lastConnectionState;
                        log.info("Backup: Saved ECU state from lastConnectionState: " + savedEcuState);
                    } else if (ecuConnectionState == ElmProt.STAT.CONNECTED ||
                               ecuConnectionState == ElmProt.STAT.ECU_DETECTED ||
                               ecuConnectionState == ElmProt.STAT.ECU_SELECTED) {
                        savedEcuState = ecuConnectionState;
                        log.info("Backup: Saved ECU state from ecuConnectionState: " + savedEcuState);
                    } else {
                        log.info("No good ECU state available to save");
                    }
                } else {
                    log.info("ECU state already saved: " + savedEcuState);
                }
            }

            if (serviceWhenCycleStarted == currentService) {
                connectionCycleCount++;
                lastCycleTimestamp = currentTime;
                log.info(String.format(
                    "Connection cycle detected for service %s: count=%d",
                    ObdProt.getServiceName(currentService),
                    connectionCycleCount
                ));

                if (connectionCycleCount >= MAX_CYCLES_BEFORE_ALERT && unsupportedModeDialog == null) {
                    showUnsupportedModeDialog(currentService);
                }
            }
        } else {
            log.info("detectConnectionCycles: No cycle detected in this transition");
        }

        lastConnectionState = newState;
    }

    void reset() {
        connectionCycleCount = 0;
        serviceWhenCycleStarted = ObdProt.OBD_SVC_NONE;
        lastCycleTimestamp = 0;
        lastConnectionState = ElmProt.STAT.UNDEFINED;
        savedEcuState = ElmProt.STAT.UNDEFINED;
    }

    void onDestroy() {
        if (unsupportedModeDialog != null) {
            unsupportedModeDialog.dismiss();
            unsupportedModeDialog = null;
        }
    }

    private void showUnsupportedModeDialog(int obdService) {
        if (unsupportedModeDialog != null && unsupportedModeDialog.isShowing()) {
            return;
        }

        String serviceName = ObdProt.getServiceName(obdService);
        String message;

        switch (obdService) {
            case ObdProt.OBD_SVC_CTRL_MODE:
                message = "Test Control (Mode 8) is not supported by this vehicle.\n\n" +
                          "This mode is used for specialized diagnostic tests like evaporative system leak tests " +
                          "and is rarely supported by consumer vehicles or OBD emulators.\n\n" +
                          "This is completely normal.";
                break;
            case ObdProt.OBD_SVC_MON_RESULT:
                message = "Monitor Test Results (Mode 6) is not supported by this vehicle.\n\n" +
                          "This mode provides detailed emissions monitoring test results and is not supported " +
                          "by all vehicles.\n\n" +
                          "This is normal for many vehicles.";
                break;
            default:
                message = String.format(
                    "%s is not responding.\n\nThis feature may not be supported by your vehicle.",
                    serviceName
                );
                break;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("Mode Not Supported")
               .setMessage(message)
               .setCancelable(false)
               .setNegativeButton("Keep Trying", (dialog, which) -> {
                   reset();
                   dialog.dismiss();
                   unsupportedModeDialog = null;
               })
               .setPositiveButton("Go Back", (dialog, which) -> {
                   log.info("Go Back button clicked - returning to dashboard");

                   reset();
                   dialog.dismiss();
                   unsupportedModeDialog = null;

                   if (CommService.elm != null) {
                       log.info("Triggering ECU reconnection after unsupported mode");
                       CommService.elm.reset();
                   } else {
                       log.warning("Cannot reset - CommService.elm is null");
                   }

                   activity.setObdService(ObdProt.OBD_SVC_NONE, null);
               });

        unsupportedModeDialog = builder.create();
        unsupportedModeDialog.show();

        log.info(String.format(
            "Showed unsupported mode dialog for %s after %d cycles",
            serviceName,
            connectionCycleCount
        ));
    }
}
