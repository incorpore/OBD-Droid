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

    public interface OnThreadClickListener {
        void onThreadClicked(ThreadManagerActivity.ThreadItem thread);
    }

    public interface OnThreadDeleteListener {
        void onThreadDelete(ThreadManagerActivity.ThreadItem thread);
    }

    public ThreadListAdapter(
        List<ThreadManagerActivity.ThreadItem> threads,
        OnThreadClickListener onThreadClick,
        OnThreadDeleteListener onThreadDelete
    ) {
        this.threads = threads;
        this.onThreadClick = onThreadClick;
        this.onThreadDelete = onThreadDelete;
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
        private final TextView threadDate;
        private final ImageButton deleteButton;

        public ThreadViewHolder(@NonNull View itemView) {
            super(itemView);
            threadTitle = itemView.findViewById(R.id.thread_title);
            threadVin = itemView.findViewById(R.id.thread_vin);
            threadDate = itemView.findViewById(R.id.thread_date);
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

            // Set date (TODO: Store and display actual last active timestamp)
            threadDate.setText("Thread ID: " + thread.threadId.substring(0, Math.min(16, thread.threadId.length())) + "...");

            // Set click listeners
            itemView.setOnClickListener(v -> {
                if (onThreadClick != null) {
                    onThreadClick.onThreadClicked(thread);
                }
            });

            deleteButton.setOnClickListener(v -> {
                if (onThreadDelete != null) {
                    onThreadDelete.onThreadDelete(thread);
                }
            });
        }
    }
}
