package com.obddroid.ui.activities;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.app.SearchManager;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.obddroid.R;
import com.obddroid.core.ecu.EcuCodeItem;
import com.obddroid.core.ecu.EcuDataPv;
import com.obddroid.core.obd.ElmProt;
import com.obddroid.core.obd.ObdProt;
import com.obddroid.core.pvs.PvList;
import com.obddroid.services.CommService;
import com.obddroid.ui.adapters.ObdItemAdapter;
import com.obddroid.utils.SnackbarHelper;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * UI helper for displaying and interacting with fault code related dialogs.
 */
final class FaultCodeUiHelper
{
    private static final Logger log = Logger.getLogger(FaultCodeUiHelper.class.getName());

    private FaultCodeUiHelper()
    {
        // Utility class
    }

    static void showFaultCodeOptionsModal(MainActivity activity,
                                          ObdItemAdapter adapter,
                                          int position,
                                          ElmProt.STAT ecuConnectionState)
    {
        Object item = adapter.getItem(position);
        if (!(item instanceof EcuCodeItem))
        {
            SnackbarHelper.showWarning(activity, "No fault code data available for this item");
            return;
        }

        EcuCodeItem dfc = (EcuCodeItem) item;
        LayoutInflater inflater = activity.getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_fault_code_options, null);

        TextView codeNumber = dialogView.findViewById(R.id.fault_code_number);
        TextView codeDesc = dialogView.findViewById(R.id.fault_code_description);
        TextView codeType = dialogView.findViewById(R.id.fault_code_type);
        ImageView statusIcon = dialogView.findViewById(R.id.fault_code_status_icon);

        String code = String.valueOf(dfc.get(EcuCodeItem.FID_CODE));
        String description = String.valueOf(dfc.get(EcuCodeItem.FID_DESCRIPT));

        codeNumber.setText(code);
        codeDesc.setText(description);

        Integer svc = (Integer) dfc.get(EcuCodeItem.FID_STATUS);
        if (svc != null)
        {
            switch (svc)
            {
                case ObdProt.OBD_SVC_PENDINGCODES:
                    codeType.setText("Pending Code");
                    statusIcon.setImageResource(android.R.drawable.ic_menu_recent_history);
                    statusIcon.setColorFilter(Color.parseColor("#FF9800"));
                    break;
                case ObdProt.OBD_SVC_PERMACODES:
                    codeType.setText("Permanent Code");
                    statusIcon.setImageResource(android.R.drawable.ic_dialog_alert);
                    statusIcon.setColorFilter(Color.parseColor("#F44336"));
                    break;
                default:
                    codeType.setText("Confirmed Code");
                    statusIcon.setImageResource(android.R.drawable.ic_menu_myplaces);
                    statusIcon.setColorFilter(Color.parseColor("#F57C00"));
                    break;
            }
        }

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(dialogView)
                .create();

        View freezeFrameOption = dialogView.findViewById(R.id.option_freeze_frame);
        View searchOption = dialogView.findViewById(R.id.option_search_web);
        View copyOption = dialogView.findViewById(R.id.option_copy_code);
        Button closeButton = dialogView.findViewById(R.id.btn_close);

        boolean canViewFreezeFrames = (ecuConnectionState == ElmProt.STAT.CONNECTED ||
                                       ecuConnectionState == ElmProt.STAT.ECU_DETECTED);

        TextView freezeStatus = dialogView.findViewById(R.id.freeze_frame_status);
        if (!canViewFreezeFrames)
        {
            if (freezeStatus != null)
            {
                freezeStatus.setText("Connect to vehicle first");
            }
            freezeFrameOption.setAlpha(0.5f);
            freezeFrameOption.setEnabled(false);
        }
        else
        {
            freezeFrameOption.setOnClickListener(v ->
            {
                dialog.dismiss();
                showFreezeFrameDialog(activity, position, dfc);
            });
        }

        searchOption.setOnClickListener(v ->
        {
            dialog.dismiss();
            searchFaultCodeOnWeb(activity, dfc);
        });

        copyOption.setOnClickListener(v ->
        {
            copyFaultCodeToClipboard(activity, code, description);
            dialog.dismiss();
        });

        closeButton.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private static void showFreezeFrameDialog(MainActivity activity,
                                              int dtcIndex,
                                              EcuCodeItem dfc)
    {
        try
        {
            LayoutInflater inflater = activity.getLayoutInflater();
            View dialogView = inflater.inflate(R.layout.dialog_freeze_frame_data, null);

            TextView codeText = dialogView.findViewById(R.id.freeze_frame_code);
            String codeInfo = dfc.get(EcuCodeItem.FID_CODE) + " - " + dfc.get(EcuCodeItem.FID_DESCRIPT);
            codeText.setText(codeInfo);

            View loadingContainer = dialogView.findViewById(R.id.loading_container);
            View dataContainer = dialogView.findViewById(R.id.data_container);
            View noDataContainer = dialogView.findViewById(R.id.no_data_container);
            LinearLayout dataList = dialogView.findViewById(R.id.freeze_frame_data_list);
            Button closeButton = dialogView.findViewById(R.id.btn_close);
            Button refreshButton = dialogView.findViewById(R.id.btn_refresh);

            AlertDialog freezeDialog = new AlertDialog.Builder(activity)
                    .setView(dialogView)
                    .create();

            closeButton.setOnClickListener(v -> freezeDialog.dismiss());

            Runnable loadFreezeFrameData = () ->
            {
                activity.runOnUiThread(() ->
                {
                    loadingContainer.setVisibility(View.VISIBLE);
                    dataContainer.setVisibility(View.GONE);
                    noDataContainer.setVisibility(View.GONE);
                    dataList.removeAllViews();
                    refreshButton.setVisibility(View.GONE);
                });

                try
                {
                    final int previousService = CommService.elm.getService();
                    final int frameIndex = Math.max(dtcIndex, 0);

                    // Trigger freeze frame retrieval
                    CommService.elm.setFreezeFrame_Id(frameIndex);

                    new Handler(Looper.getMainLooper()).postDelayed(() ->
                    {
                        activity.runOnUiThread(() ->
                        {
                            try
                            {
                                PvList freezeFrameData = ObdProt.getDataService().getFreezeFrameData(frameIndex);

                                if (freezeFrameData != null && freezeFrameData.size() > 0)
                                {
                                    populateFreezeFrameList(activity, dataList, freezeFrameData);

                                    if (dataList.getChildCount() > 0)
                                    {
                                        loadingContainer.setVisibility(View.GONE);
                                        dataContainer.setVisibility(View.VISIBLE);
                                        refreshButton.setVisibility(View.VISIBLE);
                                    }
                                    else
                                    {
                                        log.warning("Freeze Frame: Only PID support messages, no actual data");
                                        loadingContainer.setVisibility(View.GONE);
                                        noDataContainer.setVisibility(View.VISIBLE);
                                    }
                                }
                                else
                                {
                                    log.warning("Freeze Frame: No data available after waiting");
                                    loadingContainer.setVisibility(View.GONE);
                                    noDataContainer.setVisibility(View.VISIBLE);
                                }

                                if (previousService != ObdProt.OBD_SVC_FREEZEFRAME)
                                {
                                    CommService.elm.setService(previousService, true);
                                }
                            }
                            catch (Exception e)
                            {
                                log.log(Level.WARNING, "Error displaying freeze frame data", e);
                                loadingContainer.setVisibility(View.GONE);
                                noDataContainer.setVisibility(View.VISIBLE);
                                if (previousService != ObdProt.OBD_SVC_FREEZEFRAME)
                                {
                                    CommService.elm.setService(previousService, true);
                                }
                            }
                        });
                    }, 800);
                }
                catch (Exception e)
                {
                    log.log(Level.SEVERE, "Show freeze frame modal", e);
                    activity.runOnUiThread(() ->
                            SnackbarHelper.showError(activity,
                                    "Error showing freeze frame data: " + e.getMessage()));
                }
            };

            refreshButton.setOnClickListener(v -> new Thread(loadFreezeFrameData).start());
            new Thread(loadFreezeFrameData).start();

            freezeDialog.show();
            if (freezeDialog.getWindow() != null)
            {
                freezeDialog.getWindow().setLayout(
                        (int) (activity.getResources().getDisplayMetrics().widthPixels * 0.9),
                        (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.7));
            }
        }
        catch (Exception e)
        {
            log.log(Level.SEVERE, "Show freeze frame modal", e);
            SnackbarHelper.showError(activity, "Error showing freeze frame data: " + e.getMessage());
        }
    }

    private static void populateFreezeFrameList(Context context,
                                                LinearLayout dataList,
                                                PvList freezeFrameData)
    {
        LayoutInflater inflater = LayoutInflater.from(context);

        if (freezeFrameData == null || freezeFrameData.isEmpty())
        {
            return;
        }

        for (Object valueObj : freezeFrameData.values())
        {
            if (!(valueObj instanceof EcuDataPv))
            {
                continue;
            }

            EcuDataPv pv = (EcuDataPv) valueObj;

            View itemView = inflater.inflate(R.layout.freeze_frame_data_item, dataList, false);
            TextView label = itemView.findViewById(R.id.data_label);
            TextView value = itemView.findViewById(R.id.data_value);

            Object descObj = pv.get(EcuDataPv.FID_DESCRIPT);
            String labelText = descObj != null ? descObj.toString() : null;

            if (labelText == null || labelText.trim().isEmpty() || labelText.equals("null"))
            {
                Object key = pv.get(EcuDataPv.FID_PID);
                if (key instanceof Number)
                {
                    labelText = String.format("PID 0x%02X", ((Number) key).intValue());
                }
                else if (key != null)
                {
                    labelText = "PID " + key;
                }
                else
                {
                    labelText = "PID";
                }
            }
            label.setText(labelText);

            Object dataValue = pv.get(EcuDataPv.FID_VALUE);
            Object units = pv.get(EcuDataPv.FID_UNITS);

            String displayValue;
            if (dataValue == null)
            {
                displayValue = "N/A";
            }
            else if (dataValue instanceof byte[])
            {
                byte[] bytes = (byte[]) dataValue;
                StringBuilder hex = new StringBuilder("0x");
                for (byte b : bytes)
                {
                    hex.append(String.format("%02X", b & 0xFF));
                }
                displayValue = hex.toString();
            }
            else
            {
                displayValue = dataValue.toString();
            }

            if (units != null && !units.toString().isEmpty())
            {
                displayValue += " " + units;
            }
            value.setText(displayValue);

            dataList.addView(itemView);
        }
    }

    private static void searchFaultCodeOnWeb(Context context, EcuCodeItem dfc)
    {
        try
        {
            Intent intent = new Intent(Intent.ACTION_WEB_SEARCH);
            intent.putExtra(SearchManager.QUERY,
                    "OBD " + String.valueOf(dfc.get(EcuCodeItem.FID_CODE)));
            context.startActivity(intent);
        }
        catch (Exception e)
        {
            log.log(Level.SEVERE, "WebSearch DFC", e);
            SnackbarHelper.showError(context, e.getMessage());
        }
    }

    private static void copyFaultCodeToClipboard(Context context, String code, String description)
    {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null)
        {
            SnackbarHelper.showWarning(context, "Clipboard not available on this device");
            return;
        }

        String text = code + " - " + description;
        ClipData clip = ClipData.newPlainText("OBD Fault Code", text);
        clipboard.setPrimaryClip(clip);
        SnackbarHelper.showSuccess(context, "Copied: " + code);
    }
}
