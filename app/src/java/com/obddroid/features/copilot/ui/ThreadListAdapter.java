package com.obddroid.features.copilot.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.obddroid.R;

import java.util.List;

/**
 * RecyclerView adapter for displaying CoPilot conversation threads.
 */
public class ThreadListAdapter extends RecyclerView.Adapter<ThreadListAdapter.ThreadViewHolder> {

    private final List<ThreadManagerActivity.ThreadItem> threads;
    private final OnThreadClickListener onThreadClick;
    private final OnThreadDeleteListener onThreadDelete;
    private final OnThreadCompareListener onThreadCompare;
    private final OnThreadExportListener onThreadExport;

    public interface OnThreadClickListener {
        void onThreadClicked(ThreadManagerActivity.ThreadItem thread);
    }

    public interface OnThreadDeleteListener {
        void onThreadDelete(ThreadManagerActivity.ThreadItem thread);
    }

    public interface OnThreadCompareListener {
        void onThreadCompare(ThreadManagerActivity.ThreadItem thread);
    }

    public interface OnThreadExportListener {
        void onThreadExport(ThreadManagerActivity.ThreadItem thread);
    }

    public ThreadListAdapter(
        List<ThreadManagerActivity.ThreadItem> threads,
        OnThreadClickListener onThreadClick,
        OnThreadDeleteListener onThreadDelete,
        OnThreadCompareListener onThreadCompare,
        OnThreadExportListener onThreadExport
    ) {
        this.threads = threads;
        this.onThreadClick = onThreadClick;
        this.onThreadDelete = onThreadDelete;
        this.onThreadCompare = onThreadCompare;
        this.onThreadExport = onThreadExport;
    }

    @NonNull
    @Override
    public ThreadViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_thread, parent, false);
        return new ThreadViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ThreadViewHolder holder, int position) {
        ThreadManagerActivity.ThreadItem thread = threads.get(position);
        holder.bind(thread);
    }

    @Override
    public int getItemCount() {
        return threads.size();
    }

    class ThreadViewHolder extends RecyclerView.ViewHolder {
        private final TextView threadTitle;
        private final TextView threadVin;
        private final TextView threadLastScan;
        private final TextView threadFileStatus;
        private final View actionOpen;
        private final View actionCompare;
        private final View actionExport;
        private final ImageButton deleteButton;

        public ThreadViewHolder(@NonNull View itemView) {
            super(itemView);
            threadTitle = itemView.findViewById(R.id.thread_title);
            threadVin = itemView.findViewById(R.id.thread_vin);
            threadLastScan = itemView.findViewById(R.id.thread_last_scan);
            threadFileStatus = itemView.findViewById(R.id.thread_file_status);
            actionOpen = itemView.findViewById(R.id.action_open);
            actionCompare = itemView.findViewById(R.id.action_compare);
            actionExport = itemView.findViewById(R.id.action_export);
            deleteButton = itemView.findViewById(R.id.delete_button);
        }

        public void bind(ThreadManagerActivity.ThreadItem thread) {
            // Set title (vehicle name)
            threadTitle.setText(thread.getDisplayName());

            // Set VIN
            if (thread.vin != null && !thread.vin.isEmpty()) {
                threadVin.setText("VIN: " + thread.vin);
                threadVin.setVisibility(View.VISIBLE);
            } else {
                threadVin.setVisibility(View.GONE);
            }

            // Latest scan summary
            if (thread.lastScanSummary != null) {
                threadLastScan.setText(thread.lastScanSummary);
                threadLastScan.setVisibility(View.VISIBLE);
            } else {
                threadLastScan.setText("No scans yet");
                threadLastScan.setVisibility(View.VISIBLE);
            }

            // File sync status
            if (thread.fileStatusText != null) {
                threadFileStatus.setText(thread.fileStatusText);
                threadFileStatus.setVisibility(View.VISIBLE);
            } else {
                threadFileStatus.setVisibility(View.GONE);
            }

            // Set click listeners
            View.OnClickListener openListener = v -> {
                if (onThreadClick != null) {
                    onThreadClick.onThreadClicked(thread);
                }
            };
            itemView.setOnClickListener(openListener);
            actionOpen.setOnClickListener(openListener);

            if (thread.comparePrompt != null && !thread.comparePrompt.isEmpty()) {
                actionCompare.setVisibility(View.VISIBLE);
                actionCompare.setOnClickListener(v -> {
                    if (onThreadCompare != null) {
                        onThreadCompare.onThreadCompare(thread);
                    }
                });
            } else {
                actionCompare.setVisibility(View.GONE);
            }

            if (thread.canExport) {
                actionExport.setVisibility(View.VISIBLE);
                actionExport.setOnClickListener(v -> {
                    if (onThreadExport != null) {
                        onThreadExport.onThreadExport(thread);
                    }
                });
            } else {
                actionExport.setVisibility(View.GONE);
            }

            deleteButton.setOnClickListener(v -> {
                if (onThreadDelete != null) {
                    onThreadDelete.onThreadDelete(thread);
                }
            });
        }
    }
}
