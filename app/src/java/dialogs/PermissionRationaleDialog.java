package com.obddroid.dialogs;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.obddroid.R;

/**
 * Dialog fragment for explaining why permissions are needed
 * Handles "Don't ask again" scenarios by directing users to app settings
 */
public class PermissionRationaleDialog extends DialogFragment {

    public static final String TAG = "PermissionRationale";

    // Arguments keys
    private static final String ARG_PERMISSION_TYPE = "permission_type";
    private static final String ARG_SHOW_SETTINGS = "show_settings";

    // Permission types
    public static final int PERMISSION_STORAGE = 1;
    public static final int PERMISSION_NOTIFICATION = 2;
    public static final int PERMISSION_BLUETOOTH = 3;
    public static final int PERMISSION_LOCATION = 4;

    // Shared preferences for tracking "don't ask again"
    private static final String PREFS_NAME = "permission_rationale_prefs";
    private static final String KEY_DONT_ASK_PREFIX = "dont_ask_";

    // Callback interface
    public interface OnPermissionRationaleListener {
        void onProceedToPermission(int permissionType);
        void onCancelPermission(int permissionType);
        void onOpenSettings(int permissionType);
    }

    private OnPermissionRationaleListener listener;
    private int permissionType;
    private boolean showSettingsOption;

    /**
     * Create a new instance of the dialog
     *
     * @param permissionType Type of permission to explain
     * @param showSettingsOption True if user has selected "Don't ask again"
     * @return Dialog instance
     */
    public static PermissionRationaleDialog newInstance(int permissionType, boolean showSettingsOption) {
        PermissionRationaleDialog fragment = new PermissionRationaleDialog();
        Bundle args = new Bundle();
        args.putInt(ARG_PERMISSION_TYPE, permissionType);
        args.putBoolean(ARG_SHOW_SETTINGS, showSettingsOption);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        try {
            listener = (OnPermissionRationaleListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString()
                + " must implement OnPermissionRationaleListener");
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            permissionType = getArguments().getInt(ARG_PERMISSION_TYPE);
            showSettingsOption = getArguments().getBoolean(ARG_SHOW_SETTINGS);
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        // Set title and message based on permission type
        String title = getPermissionTitle();
        String message = getPermissionMessage();

        builder.setTitle(title)
            .setMessage(message)
            .setIcon(android.R.drawable.ic_dialog_info);

        if (showSettingsOption) {
            // User has denied with "Don't ask again"
            builder.setPositiveButton("Open Settings", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    openAppSettings();
                    if (listener != null) {
                        listener.onOpenSettings(permissionType);
                    }
                }
            });
            builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (listener != null) {
                        listener.onCancelPermission(permissionType);
                    }
                }
            });
        } else {
            // Normal permission rationale
            builder.setPositiveButton("Continue", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (listener != null) {
                        listener.onProceedToPermission(permissionType);
                    }
                }
            });
            builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (listener != null) {
                        listener.onCancelPermission(permissionType);
                    }
                }
            });
            builder.setNeutralButton("Don't Ask Again", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    setDontAskAgain(permissionType);
                    if (listener != null) {
                        listener.onCancelPermission(permissionType);
                    }
                }
            });
        }

        return builder.create();
    }

    /**
     * Get title for the permission type
     */
    private String getPermissionTitle() {
        switch (permissionType) {
            case PERMISSION_STORAGE:
                return "Storage Permission Required";
            case PERMISSION_NOTIFICATION:
                return "Notification Permission Required";
            case PERMISSION_BLUETOOTH:
                return "Bluetooth Permission Required";
            case PERMISSION_LOCATION:
                return "Location Permission Required";
            default:
                return "Permission Required";
        }
    }

    /**
     * Get explanation message for the permission type
     */
    private String getPermissionMessage() {
        if (showSettingsOption) {
            return getSettingsMessage();
        }

        switch (permissionType) {
            case PERMISSION_STORAGE:
                return "OBDroid needs storage access to:\n\n" +
                    "• Save data logs and export files\n" +
                    "• Store screenshots of your dashboard\n" +
                    "• Import/export vehicle configurations\n\n" +
                    "Your data remains private and is only accessible by OBDroid.";

            case PERMISSION_NOTIFICATION:
                return "OBDroid needs notification permission to:\n\n" +
                    "• Show connection status when running in background\n" +
                    "• Alert you about important errors or warnings\n" +
                    "• Display data logging progress\n\n" +
                    "You can customize notification preferences in settings.";

            case PERMISSION_BLUETOOTH:
                return "OBDroid needs Bluetooth permission to:\n\n" +
                    "• Connect to your OBD-II adapter\n" +
                    "• Scan for available Bluetooth devices\n" +
                    "• Maintain connection during data collection\n\n" +
                    "This is essential for communicating with your vehicle.";

            case PERMISSION_LOCATION:
                return "OBDroid needs location permission to:\n\n" +
                    "• Scan for Bluetooth devices (Android requirement)\n" +
                    "• This app does NOT track or store your location\n\n" +
                    "This is an Android system requirement for Bluetooth scanning.";

            default:
                return "This permission is required for the app to function properly.";
        }
    }

    /**
     * Get message when user needs to go to settings
     */
    private String getSettingsMessage() {
        String baseMessage;
        switch (permissionType) {
            case PERMISSION_STORAGE:
                baseMessage = "Storage permission is required for saving data and screenshots.";
                break;
            case PERMISSION_NOTIFICATION:
                baseMessage = "Notification permission is required for background monitoring.";
                break;
            case PERMISSION_BLUETOOTH:
                baseMessage = "Bluetooth permission is required to connect to your OBD adapter.";
                break;
            case PERMISSION_LOCATION:
                baseMessage = "Location permission is required for Bluetooth scanning.";
                break;
            default:
                baseMessage = "This permission is required for the app to function.";
        }

        return baseMessage + "\n\nYou have previously denied this permission. " +
            "Please go to Settings and manually enable it for OBDroid.";
    }

    /**
     * Open app settings page
     */
    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", requireActivity().getPackageName(), null);
        intent.setData(uri);
        startActivity(intent);
    }

    /**
     * Save "Don't ask again" preference
     */
    private void setDontAskAgain(int permissionType) {
        if (getContext() != null) {
            SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putBoolean(KEY_DONT_ASK_PREFIX + permissionType, true).apply();
        }
    }

    /**
     * Check if user has selected "Don't ask again" for a permission
     */
    public static boolean isDontAskAgain(Context context, int permissionType) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_DONT_ASK_PREFIX + permissionType, false);
    }

    /**
     * Reset "Don't ask again" preference
     */
    public static void resetDontAskAgain(Context context, int permissionType) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().remove(KEY_DONT_ASK_PREFIX + permissionType).apply();
    }
}