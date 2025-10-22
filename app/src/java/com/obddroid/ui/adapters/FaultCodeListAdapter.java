package com.obddroid.ui.adapters;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.obddroid.R;
import com.obddroid.services.FaultCodeService;

import java.util.ArrayList;
import java.util.List;

/**
 * RecyclerView adapter for displaying fault codes in FaultCodesActivity.
 */
public class FaultCodeListAdapter extends RecyclerView.Adapter<FaultCodeListAdapter.ViewHolder> {

    private List<FaultCodeService.FaultCodeInfo> faultCodes = new ArrayList<>();
    private OnCodeClickListener clickListener;

    public interface OnCodeClickListener {
        void onCodeClick(FaultCodeService.FaultCodeInfo code);
    }

    public void setOnCodeClickListener(OnCodeClickListener listener) {
        this.clickListener = listener;
    }

    public void setFaultCodes(List<FaultCodeService.FaultCodeInfo> codes) {
        if (codes == null) {
            this.faultCodes = new ArrayList<>();
        } else {
            this.faultCodes = new ArrayList<>(codes);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.fault_code_list_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FaultCodeService.FaultCodeInfo code = faultCodes.get(position);
        holder.bind(code, clickListener);
    }

    @Override
    public int getItemCount() {
        return faultCodes.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final CardView cardView;
        private final ImageView statusIcon;
        private final TextView codeText;
        private final TextView descriptionText;
        private final TextView statusBadge;

        ViewHolder(View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.fault_code_card);
            statusIcon = itemView.findViewById(R.id.status_icon);
            codeText = itemView.findViewById(R.id.code_text);
            descriptionText = itemView.findViewById(R.id.description_text);
            statusBadge = itemView.findViewById(R.id.status_badge);
        }

        void bind(FaultCodeService.FaultCodeInfo code, OnCodeClickListener listener) {
            Context context = cardView.getContext();

            codeText.setText(code.code);
            descriptionText.setText(code.description);

            // Determine color and label based on code type
            int color;
            int labelResId;

            switch (code.type) {
                case PERMANENT:
                    // Permanent codes - Critical/Red - Can't be cleared with scan tool
                    color = ContextCompat.getColor(context, R.color.fault_critical);
                    labelResId = R.string.fault_codes_status_permanent;
                    break;
                case CONFIRMED:
                    // Confirmed/Stored codes - Error/Orange - Triggered MIL
                    color = ContextCompat.getColor(context, R.color.fault_error);
                    labelResId = R.string.fault_codes_status_confirmed;
                    break;
                case PENDING:
                    // Pending codes - Warning/Yellow - Not yet confirmed
                    color = ContextCompat.getColor(context, R.color.fault_pending);
                    labelResId = R.string.fault_codes_status_pending;
                    break;
                default:
                    color = ContextCompat.getColor(context, R.color.fault_error);
                    labelResId = R.string.fault_codes_status_confirmed;
            }

            statusBadge.setText(labelResId);

            if (statusBadge.getBackground() != null) {
                DrawableCompat.setTint(
                    DrawableCompat.wrap(statusBadge.getBackground()).mutate(),
                    color
                );
            } else {
                statusBadge.setBackgroundColor(color);
            }
            ImageViewCompat.setImageTintList(statusIcon, ColorStateList.valueOf(color));

            cardView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onCodeClick(code);
                }
            });
        }
    }
}
