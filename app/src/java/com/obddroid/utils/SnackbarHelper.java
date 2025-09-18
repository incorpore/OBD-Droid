package com.obddroid.utils;

import android.app.Activity;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.google.android.material.snackbar.Snackbar;

/**
 * Helper class for displaying Snackbars at the top of the screen
 * to avoid conflict with VehicleInfoFooter at the bottom
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

    /**
     * Show a Snackbar at the TOP of the screen
     * @param activity The current activity
     * @param message The message to display
     * @param type The type of message (affects color)
     */
    public static void showTopSnackbar(Activity activity, String message, MessageType type) {
        if (activity == null || activity.isFinishing()) {
            return;
        }

        View rootView = activity.findViewById(android.R.id.content);
        if (rootView == null) {
            return;
        }

        Snackbar snackbar = Snackbar.make(rootView, message, Snackbar.LENGTH_LONG);

        // Style the Snackbar
        View snackbarView = snackbar.getView();

        // Set background color based on type
        snackbarView.setBackgroundColor(type.getColor());

        // Get the TextView and style it
        TextView textView = snackbarView.findViewById(com.google.android.material.R.id.snackbar_text);
        if (textView != null) {
            textView.setTextColor(Color.WHITE);
            textView.setMaxLines(3);
        }

        // Position at the TOP of the screen
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) snackbarView.getLayoutParams();
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.setMargins(16, 80, 16, 0); // Top margin to avoid status bar
        snackbarView.setLayoutParams(params);

        // Add subtle elevation for modern look
        snackbarView.setElevation(6f);

        // Show it
        snackbar.show();
    }

    /**
     * Show an error message
     */
    public static void showError(Activity activity, String message) {
        showTopSnackbar(activity, message, MessageType.ERROR);
    }

    /**
     * Show a warning message
     */
    public static void showWarning(Activity activity, String message) {
        showTopSnackbar(activity, message, MessageType.WARNING);
    }

    /**
     * Show an info message
     */
    public static void showInfo(Activity activity, String message) {
        showTopSnackbar(activity, message, MessageType.INFO);
    }

    /**
     * Show a success message
     */
    public static void showSuccess(Activity activity, String message) {
        showTopSnackbar(activity, message, MessageType.SUCCESS);
    }
}