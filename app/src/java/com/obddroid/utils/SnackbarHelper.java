package com.obddroid.utils;

import android.app.Activity;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.google.android.material.snackbar.Snackbar;

/**
 * Helper class for displaying Snackbars positioned above the VehicleInfoFooter
 * Appears at the bottom of the screen with proper margin to avoid overlapping the footer
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
     * Show a Snackbar positioned above the VehicleInfoFooter
     * @param activity The current activity
     * @param message The message to display
     * @param type The type of message (affects color)
     */
    public static void showSnackbar(Activity activity, String message, MessageType type) {
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
        // Add bottom margin to appear flush above the VehicleInfoFooter
        float scale = activity.getResources().getDisplayMetrics().density;
        int bottomMargin = (int) (80 * scale + 0.5f); // 80dp to sit flush on top of footer
        params.setMargins(0, 0, 0, bottomMargin); // No side margins for full width
        snackbarView.setLayoutParams(params);

        // Remove the default Snackbar rounded corners for flush appearance
        snackbarView.setBackgroundResource(android.R.color.transparent);

        // Create a rectangle background with the message type color
        snackbarView.setBackgroundColor(type.getColor());

        // Set elevation LOWER than VehicleInfoFooter so it slides from behind
        // VehicleInfoFooter should have higher elevation to stay on top
        snackbarView.setElevation(2f);  // Lower than footer's elevation

        // Set the snackbar's Z translation to be behind the footer initially
        snackbarView.setTranslationZ(-4f);

        // Show it
        snackbar.show();
    }

    /**
     * Show an error message
     */
    public static void showError(Activity activity, String message) {
        showSnackbar(activity, message, MessageType.ERROR);
    }

    /**
     * Show a warning message
     */
    public static void showWarning(Activity activity, String message) {
        showSnackbar(activity, message, MessageType.WARNING);
    }

    /**
     * Show an info message
     */
    public static void showInfo(Activity activity, String message) {
        showSnackbar(activity, message, MessageType.INFO);
    }

    /**
     * Show a success message
     */
    public static void showSuccess(Activity activity, String message) {
        showSnackbar(activity, message, MessageType.SUCCESS);
    }
}