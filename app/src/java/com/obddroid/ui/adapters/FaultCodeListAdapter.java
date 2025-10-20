package com.obddroid.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.obddroid.R;
import com.obddroid.services.FaultCodeService;

import java.util.ArrayList;
import java.util.List;

/**
 * RecyclerView adapter for displaying fault codes in FaultCodesActivity.
 *
 * @author Wal33D <aquataze@yahoo.com>
 */
public class FaultCodeListAdapter extends RecyclerView.Adapter<FaultCodeListAdapter.ViewHolder> {

    private List<FaultCodeService.FaultCodeInfo> faultCodes = new ArrayList<>();
    private OnCodeClickListener clickListener;

    /**
     * Interface for handling fault code clicks
     */
    public interface OnCodeClickListener {
        void onCodeClick(FaultCodeService.FaultCodeInfo code);
    }

    /**
     * Set click listener
     */
    public void setOnCodeClickListener(OnCodeClickListener listener) {
        this.clickListener = listener;
    }

    /**
     * Update the fault codes list
     */
    public void setFaultCodes(List<FaultCodeService.FaultCodeInfo> codes) {
        this.faultCodes = codes != null ? codes : new ArrayList<>();
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

    /**
     * ViewHolder for fault code items
     */
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
            // Set fault code and description
            codeText.setText(code.code);
            descriptionText.setText(code.description);

            // Set status badge
            if (code.isPending) {
                statusBadge.setText("PENDING");
                statusBadge.setBackgroundColor(0xFF42A5F5);  // Blue for pending
            } else {
                statusBadge.setText("CONFIRMED");
                statusBadge.setBackgroundColor(0xFFEF5350);  // Red for confirmed
            }

            // Set icon color based on status
            if (code.isPending) {
                statusIcon.setColorFilter(0xFF42A5F5);  // Blue
            } else {
                statusIcon.setColorFilter(0xFFEF5350);  // Red
            }

            // Set click listener
            cardView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onCodeClick(code);
                }
            });
        }
    }
}
