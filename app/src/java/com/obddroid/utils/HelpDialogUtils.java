package com.obddroid.utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.util.TypedValue;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

/**
 * Utility for showing help dialogs with consistent styling (smaller body text, tighter spacing).
 */
public final class HelpDialogUtils {

    private static final float MESSAGE_TEXT_SIZE_SP = 13f;
    private static final float MESSAGE_LINE_SPACING_MULT = 1.15f;

    private HelpDialogUtils() {
        // no-op
    }

    public static void showHelpDialog(Context context,
                                      @StringRes int titleRes,
                                      @StringRes int messageRes,
                                      @StringRes int positiveRes) {
        showHelpDialog(context, titleRes, messageRes, positiveRes, android.R.drawable.ic_menu_info_details);
    }

    public static void showHelpDialog(Context context,
                                      @StringRes int titleRes,
                                      @StringRes int messageRes,
                                      @StringRes int positiveRes,
                                      @DrawableRes int iconRes) {
        if (context == null) {
            return;
        }

        Activity activity = (context instanceof Activity) ? (Activity) context : null;
        if (activity != null && activity.isFinishing()) {
            return;
        }

        AlertDialog dialog = new AlertDialog.Builder(context)
            .setTitle(titleRes)
            .setMessage(messageRes)
            .setPositiveButton(positiveRes, null)
            .setIcon(iconRes)
            .show();

        TextView messageView = dialog.findViewById(android.R.id.message);
        if (messageView != null) {
            messageView.setTextSize(TypedValue.COMPLEX_UNIT_SP, MESSAGE_TEXT_SIZE_SP);
            messageView.setLineSpacing(0f, MESSAGE_LINE_SPACING_MULT);
        }
    }
}
