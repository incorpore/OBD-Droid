package com.obddroid.ui.helpers;

import android.app.Dialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.TextView;

import com.obddroid.R;
import com.obddroid.obd.ObdProt;
import com.obddroid.services.StateManager;

import java.util.logging.Logger;

/**
 * Modern Material Design progress dialog for state cleanup operations.
 *
 * Shows a beautiful animated progress indicator while StateManager runs
 * cleanup on a background thread, preventing UI freezes.
 *
 * USAGE:
 * StateCleanupDialog.show(context, previousService, new StateCleanupDialog.Callback() {
 *     @Override
 *     public void onComplete(boolean success) {
 *         // Cleanup done - update UI
 *     }
 * });
 */
public class StateCleanupDialog {

    private static final Logger log = Logger.getLogger("StateCleanupDialog");

    /**
     * Callback for cleanup completion
     */
    public interface Callback {
        void onComplete(boolean success);
    }

    /**
     * Progress update callback (optional - for advanced use)
     */
    public interface ProgressCallback extends Callback {
        void onProgress(int step, String message);
    }

    /**
     * Show cleanup dialog and run cleanup asynchronously
     *
     * @param context Activity context
     * @param fromService Service mode we're cleaning up from
     * @param callback Called when cleanup completes
     */
    public static void show(Context context, int fromService, Callback callback) {
        // Create modern dialog
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        View view = LayoutInflater.from(context).inflate(R.layout.dialog_state_cleanup, null);
        dialog.setContentView(view);
        dialog.setCancelable(false); // Don't allow dismissal during cleanup

        // Get views
        TextView titleView = view.findViewById(R.id.cleanup_title);
        TextView statusView = view.findViewById(R.id.cleanup_status);
        TextView stepView = view.findViewById(R.id.cleanup_step);

        // Determine cleanup level for UI messaging
        StateManager.CleanupLevel level = StateManager.determineCleanupLevel(fromService);
        String serviceName = ObdProt.getServiceName(fromService);

        // Set initial title based on cleanup level
        if (level == StateManager.CleanupLevel.FULL) {
            titleView.setText("Resetting Adapter");
            statusView.setText("Performing full ELM327 reset...");
        } else if (level == StateManager.CleanupLevel.HARD) {
            titleView.setText("Cleaning Up State");
            statusView.setText("Resetting adapter to defaults...");
        } else {
            titleView.setText("Cleaning Up State");
            statusView.setText("Clearing OBD data...");
        }

        stepView.setText("Preparing...");

        // Make dialog background transparent (CardView provides the background)
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        dialog.show();

        // Run cleanup on background thread
        Handler mainHandler = new Handler(Looper.getMainLooper());

        new Thread(() -> {
            try {
                log.info("Starting async state cleanup from: " + serviceName);

                // Update progress: Step 1/6
                mainHandler.post(() -> {
                    statusView.setText("Stopping service...");
                    stepView.setText("Step 1 of 6");
                });

                // Small delay so user can see the progress
                Thread.sleep(200);

                // Update progress: Step 2/6
                mainHandler.post(() -> {
                    statusView.setText("Clearing data collections...");
                    stepView.setText("Step 2 of 6");
                });

                Thread.sleep(200);

                // Update progress: Step 3/6
                mainHandler.post(() -> {
                    statusView.setText("Clearing ECU addresses...");
                    stepView.setText("Step 3 of 6");
                });

                Thread.sleep(200);

                // Run the actual cleanup (this contains the Thread.sleep calls for adapter reset)
                final boolean success = StateManager.cleanupState(fromService);

                // Update progress: Validation
                mainHandler.post(() -> {
                    statusView.setText("Validating cleanup...");
                    stepView.setText("Step 6 of 6");
                });

                Thread.sleep(200);

                // Cleanup complete
                mainHandler.post(() -> {
                    dialog.dismiss();

                    if (callback != null) {
                        callback.onComplete(success);
                    }

                    log.info("Async state cleanup complete: " + (success ? "SUCCESS" : "ERRORS"));
                });

            } catch (Exception e) {
                log.severe("Error during async cleanup: " + e.getMessage());

                mainHandler.post(() -> {
                    dialog.dismiss();

                    if (callback != null) {
                        callback.onComplete(false);
                    }
                });
            }
        }, "StateCleanup-Thread").start();
    }

    /**
     * Show cleanup dialog with detailed progress updates
     *
     * @param context Activity context
     * @param fromService Service mode we're cleaning up from
     * @param callback Called with progress updates and completion
     */
    public static void showWithProgress(Context context, int fromService, ProgressCallback callback) {
        // Create modern dialog
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        View view = LayoutInflater.from(context).inflate(R.layout.dialog_state_cleanup, null);
        dialog.setContentView(view);
        dialog.setCancelable(false);

        // Get views
        TextView titleView = view.findViewById(R.id.cleanup_title);
        TextView statusView = view.findViewById(R.id.cleanup_status);
        TextView stepView = view.findViewById(R.id.cleanup_step);

        // Determine cleanup level
        StateManager.CleanupLevel level = StateManager.determineCleanupLevel(fromService);

        // Set title
        if (level == StateManager.CleanupLevel.FULL) {
            titleView.setText("Resetting Adapter");
        } else if (level == StateManager.CleanupLevel.HARD) {
            titleView.setText("Cleaning Up State");
        } else {
            titleView.setText("Cleaning Up State");
        }

        // Make background transparent
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        dialog.show();

        // Run cleanup with detailed progress
        Handler mainHandler = new Handler(Looper.getMainLooper());

        new Thread(() -> {
            try {
                // Step 1: Stop service
                mainHandler.post(() -> {
                    statusView.setText("Stopping service...");
                    stepView.setText("Step 1 of 6");
                    if (callback != null) callback.onProgress(1, "Stopping service");
                });
                Thread.sleep(300);

                // Step 2: Clear data
                mainHandler.post(() -> {
                    statusView.setText("Clearing data collections...");
                    stepView.setText("Step 2 of 6");
                    if (callback != null) callback.onProgress(2, "Clearing data");
                });
                Thread.sleep(300);

                // Step 3: Clear ECU addresses
                mainHandler.post(() -> {
                    statusView.setText("Clearing ECU addresses...");
                    stepView.setText("Step 3 of 6");
                    if (callback != null) callback.onProgress(3, "Clearing ECU addresses");
                });
                Thread.sleep(300);

                // Step 4: Reset adapter (this is the long one for HARD/FULL)
                if (level == StateManager.CleanupLevel.HARD || level == StateManager.CleanupLevel.FULL) {
                    mainHandler.post(() -> {
                        statusView.setText(level == StateManager.CleanupLevel.FULL
                            ? "Resetting adapter (ATZ)..."
                            : "Resetting adapter (ATD)...");
                        stepView.setText("Step 4 of 6");
                        if (callback != null) callback.onProgress(4, "Resetting adapter");
                    });
                } else {
                    mainHandler.post(() -> {
                        statusView.setText("Skipping adapter reset...");
                        stepView.setText("Step 4 of 6");
                        if (callback != null) callback.onProgress(4, "Skipping adapter reset");
                    });
                    Thread.sleep(300);
                }

                // Run actual cleanup
                final boolean success = StateManager.cleanupState(fromService);

                // Step 5: Clear app state
                mainHandler.post(() -> {
                    statusView.setText("Clearing application state...");
                    stepView.setText("Step 5 of 6");
                    if (callback != null) callback.onProgress(5, "Clearing app state");
                });
                Thread.sleep(300);

                // Step 6: Validate
                mainHandler.post(() -> {
                    statusView.setText("Validating cleanup...");
                    stepView.setText("Step 6 of 6");
                    if (callback != null) callback.onProgress(6, "Validating");
                });
                Thread.sleep(300);

                // Complete
                mainHandler.post(() -> {
                    dialog.dismiss();
                    if (callback != null) {
                        callback.onComplete(success);
                    }
                });

            } catch (Exception e) {
                log.severe("Error during async cleanup: " + e.getMessage());
                mainHandler.post(() -> {
                    dialog.dismiss();
                    if (callback != null) {
                        callback.onComplete(false);
                    }
                });
            }
        }, "StateCleanup-Thread").start();
    }
}
