package com.obddroid.utils;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.google.android.material.snackbar.Snackbar;

/**
 * Helper class for displaying Snackbars positioned above the VehicleInfoFooter
 * Appears at the bottom of the screen with proper margin to avoid overlapping the footer
 * Enhanced to support non-Activity contexts, duration options, and action buttons
 */
public class SnackbarHelper {

    public enum MessageType {
        ERROR(Color.parseColor("#D32F2F")),     // Red
        WARNING(Color.parseColor("#F57C00")),   // Orange
        INFO(Color.parseColor("#757575")),      // Gray
        SUCCESS(Color.parseColor("#388E3C"));   // Green

        private final int color;

        MessageType(int color) {
            this.color = color;
        }

        public int getColor() {
            return color;
        }
    }

    public enum Duration {
        SHORT(Snackbar.LENGTH_SHORT),
        LONG(Snackbar.LENGTH_LONG),
        INDEFINITE(Snackbar.LENGTH_INDEFINITE);

        private final int value;

        Duration(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    /**
     * Convert Context to Activity if possible
     */
    private static Activity getActivityFromContext(Context context) {
        if (context == null) {
            return null;
        }
        if (context instanceof Activity) {
            return (Activity) context;
        }
        if (context instanceof ContextWrapper) {
            return getActivityFromContext(((ContextWrapper) context).getBaseContext());
        }
        return null;
    }

    /**
     * Show a Snackbar with all options
     * @param context The context (Activity or ContextWrapper)
     * @param message The message to display
     * @param type The type of message (affects color)
     * @param duration Duration of the Snackbar
     * @param actionText Optional action button text (null for no action)
     * @param actionListener Optional action button click listener
     */
    public static void showSnackbar(Context context, String message, MessageType type, Duration duration,
                                     String actionText, View.OnClickListener actionListener) {
        Activity activity = getActivityFromContext(context);
        if (activity == null || activity.isFinishing()) {
            return;
        }

        View rootView = activity.findViewById(android.R.id.content);
        if (rootView == null) {
            return;
        }

        Snackbar snackbar = Snackbar.make(rootView, message, duration.getValue());

        View footer = activity.findViewById(com.obddroid.R.id.vehicle_info_footer);
        // Don't use anchor view - we'll position manually to align flush with footer
        // if (footer != null) {
        //     snackbar.setAnchorView(footer);
        // }

        // Add action button if provided
        if (actionText != null && actionListener != null) {
            snackbar.setAction(actionText, actionListener);
            snackbar.setActionTextColor(Color.WHITE);
        }

        // Style the Snackbar
        View snackbarView = snackbar.getView();

        // Get the TextView and style it
        TextView textView = snackbarView.findViewById(com.google.android.material.R.id.snackbar_text);
        if (textView != null) {
            textView.setTextColor(Color.WHITE);
            textView.setMaxLines(3);
        }

        // Position at the BOTTOM but above the VehicleInfoFooter
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) snackbarView.getLayoutParams();
        params.gravity = Gravity.BOTTOM;
        params.width = FrameLayout.LayoutParams.MATCH_PARENT;

        // Get the actual footer height and position snackbar right above it
        int footerHeight = 0;
        if (footer != null) {
            footer.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
            footerHeight = footer.getMeasuredHeight();
            if (footerHeight == 0 && footer.getHeight() > 0) {
                footerHeight = footer.getHeight();
            }
        }

        // If we couldn't get footer height, use default 80dp
        if (footerHeight == 0) {
            float scale = activity.getResources().getDisplayMetrics().density;
            footerHeight = (int) (80 * scale + 0.5f);
        }

        float density = activity.getResources().getDisplayMetrics().density;
        int desiredGapPx = (int) (12 * density + 0.5f); // keep Snackbar ~12dp above footer

        // Set bottom margin so Snackbar sits slightly above the footer
        int marginBottom = Math.max(footerHeight - desiredGapPx, 0);
        params.setMargins(0, 0, 0, marginBottom);
        snackbarView.setLayoutParams(params);

        // Remove the default Snackbar rounded corners for flush appearance
        snackbarView.setBackgroundResource(android.R.color.transparent);

        // Create a rectangle background with the message type color
        snackbarView.setBackgroundColor(type.getColor());
        snackbarView.setElevation(8f);
        snackbarView.setTranslationZ(8f);


        snackbar.addCallback(new Snackbar.Callback() {
            @Override
            public void onDismissed(Snackbar transientBottomBar, int event) {
                snackbarView.animate().translationY(snackbarView.getHeight()).setDuration(200).start();
            }
        });

        snackbarView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                       int oldLeft, int oldTop, int oldRight, int oldBottom) {
                snackbarView.removeOnLayoutChangeListener(this);

                // Calculate how much to push down to align with footer
                int additionalOffset = 0;
                if (footer != null && footer.getHeight() > 0) {
                    // Get the snackbar's current bottom position
                    int[] snackbarLocation = new int[2];
                    snackbarView.getLocationOnScreen(snackbarLocation);
                    int snackbarBottom = snackbarLocation[1] + snackbarView.getHeight();

                    // Get the footer's top position
                    int[] footerLocation = new int[2];
                    footer.getLocationOnScreen(footerLocation);
                    int footerTop = footerLocation[1];

                    // Calculate offset needed to align snackbar bottom with footer top
                    additionalOffset = footerTop - desiredGapPx - snackbarBottom;
                }

                snackbarView.setTranslationY(snackbarView.getHeight());
                snackbarView.animate().translationY(additionalOffset).setDuration(220).start();
            }
        });
        // Show it
        snackbar.show();
    }

    /**
     * Show a Snackbar positioned above the VehicleInfoFooter
     * @param context The context (Activity or ContextWrapper)
     * @param message The message to display
     * @param type The type of message (affects color)
     */
    public static void showSnackbar(Context context, String message, MessageType type) {
        showSnackbar(context, message, type, Duration.LONG, null, null);
    }

    /**
     * Show a Snackbar with custom duration
     * @param context The context
     * @param message The message to display
     * @param type The type of message (affects color)
     * @param duration Duration of the Snackbar
     */
    public static void showSnackbar(Context context, String message, MessageType type, Duration duration) {
        showSnackbar(context, message, type, duration, null, null);
    }

    /**
     * Show an error message
     */
    public static void showError(Context context, String message) {
        showSnackbar(context, message, MessageType.ERROR);
    }

    /**
     * Show an error message with custom duration
     */
    public static void showError(Context context, String message, Duration duration) {
        showSnackbar(context, message, MessageType.ERROR, duration);
    }

    /**
     * Show a warning message
     */
    public static void showWarning(Context context, String message) {
        showSnackbar(context, message, MessageType.WARNING);
    }

    /**
     * Show a warning message with custom duration
     */
    public static void showWarning(Context context, String message, Duration duration) {
        showSnackbar(context, message, MessageType.WARNING, duration);
    }

    /**
     * Show an info message
     */
    public static void showInfo(Context context, String message) {
        showSnackbar(context, message, MessageType.INFO);
    }

    /**
     * Show an info message with custom duration
     */
    public static void showInfo(Context context, String message, Duration duration) {
        showSnackbar(context, message, MessageType.INFO, duration);
    }

    /**
     * Show a success message
     */
    public static void showSuccess(Context context, String message) {
        showSnackbar(context, message, MessageType.SUCCESS);
    }

    /**
     * Show a success message with custom duration
     */
    public static void showSuccess(Context context, String message, Duration duration) {
        showSnackbar(context, message, MessageType.SUCCESS, duration);
    }
}
