package com.obddroid.features.copilot.ui;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.obddroid.R;
import com.obddroid.features.copilot.data.AgentThreadManager;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Activity for managing CoPilot conversation threads.
 *
 * Features:
 * - View all conversations by vehicle/session
 * - Delete individual conversations
 * - Delete all data (privacy feature)
 * - Shows vehicle info and last active date
 */
public class ThreadManagerActivity extends AppCompatActivity {

    private RecyclerView threadsRecyclerView;
    private TextView threadCountText;
    private View emptyState;
    private ExtendedFloatingActionButton deleteAllFab;

    private AgentThreadManager threadManager;
    private ThreadListAdapter adapter;
    private List<ThreadItem> threads = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_thread_manager);

        threadManager = new AgentThreadManager(this);

        setupToolbar();
        initializeViews();
        setupRecyclerView();
        setupDeleteAllButton();
        loadThreads();
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Conversation History");
        }
    }

    private void initializeViews() {
        threadsRecyclerView = findViewById(R.id.threads_recycler_view);
        threadCountText = findViewById(R.id.thread_count_text);
        emptyState = findViewById(R.id.empty_state);
        deleteAllFab = findViewById(R.id.delete_all_fab);
    }

    private void setupRecyclerView() {
        adapter = new ThreadListAdapter(threads, this::onThreadClicked, this::onDeleteThread);
        threadsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        threadsRecyclerView.setAdapter(adapter);
    }

    private void setupDeleteAllButton() {
        deleteAllFab.setOnClickListener(v -> showDeleteAllConfirmation());
    }

    private void loadThreads() {
        new Thread(() -> {
            try {
                Map<String, String> mappings = threadManager.getAllThreadMappings();
                List<ThreadItem> loadedThreads = new ArrayList<>();

                for (Map.Entry<String, String> entry : mappings.entrySet()) {
                    String vin = entry.getKey();
                    String threadId = entry.getValue();

                    // Get metadata
                    JSONObject metadata = threadManager.getThreadMetadata(vin);

                    ThreadItem item = new ThreadItem();
                    item.vin = vin;
                    item.threadId = threadId;

                    if (metadata != null) {
                        item.year = metadata.optString("year", "");
                        item.make = metadata.optString("make", "");
                        item.model = metadata.optString("model", "");
                    }

                    loadedThreads.add(item);
                }

                runOnUiThread(() -> {
                    threads.clear();
                    threads.addAll(loadedThreads);
                    adapter.notifyDataSetChanged();
                    updateUI();
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "Error loading conversations: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void updateUI() {
        int count = threads.size();

        // Update count text
        if (count == 0) {
            threadCountText.setText("No conversations");
        } else if (count == 1) {
            threadCountText.setText("1 conversation");
        } else {
            threadCountText.setText(count + " conversations");
        }

        // Show/hide empty state
        if (count == 0) {
            threadsRecyclerView.setVisibility(View.GONE);
            emptyState.setVisibility(View.VISIBLE);
            deleteAllFab.setVisibility(View.GONE);
        } else {
            threadsRecyclerView.setVisibility(View.VISIBLE);
            emptyState.setVisibility(View.GONE);
            deleteAllFab.setVisibility(View.VISIBLE);
        }
    }

    private void onThreadClicked(ThreadItem thread) {
        // TODO: Open thread details or resume conversation in CoPilot
        Toast.makeText(this, "Opening conversation for " + thread.getDisplayName(),
            Toast.LENGTH_SHORT).show();
    }

    private void onDeleteThread(ThreadItem thread) {
        new AlertDialog.Builder(this)
            .setTitle("Delete Conversation")
            .setMessage("Delete conversation for " + thread.getDisplayName() + "?\n\n" +
                "This will permanently delete the conversation from both your device and OpenAI's servers.")
            .setPositiveButton("Delete", (dialog, which) -> performDeleteThread(thread))
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void performDeleteThread(ThreadItem thread) {
        new Thread(() -> {
            try {
                threadManager.deleteThread(thread.vin);

                runOnUiThread(() -> {
                    threads.remove(thread);
                    adapter.notifyDataSetChanged();
                    updateUI();
                    Toast.makeText(this, "Conversation deleted", Toast.LENGTH_SHORT).show();
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "Error deleting conversation: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void showDeleteAllConfirmation() {
        new AlertDialog.Builder(this)
            .setTitle("Delete All Data")
            .setMessage("This will permanently delete ALL your CoPilot conversations from both " +
                "your device and OpenAI's servers.\n\n" +
                "This action cannot be undone.\n\n" +
                "Are you sure?")
            .setPositiveButton("Delete All", (dialog, which) -> performDeleteAll())
            .setNegativeButton("Cancel", null)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show();
    }

    private void performDeleteAll() {
        new Thread(() -> {
            try {
                threadManager.deleteAllThreads();

                runOnUiThread(() -> {
                    threads.clear();
                    adapter.notifyDataSetChanged();
                    updateUI();
                    Toast.makeText(this, "All conversations deleted", Toast.LENGTH_SHORT).show();
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "Error deleting conversations: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Data class representing a thread list item.
     */
    public static class ThreadItem {
        public String vin;
        public String threadId;
        public String year;
        public String make;
        public String model;

        public String getDisplayName() {
            if (year != null && !year.isEmpty() && make != null && !make.isEmpty() && model != null && !model.isEmpty()) {
                return year + " " + make + " " + model;
            } else if (vin != null && !vin.isEmpty()) {
                return "VIN: " + vin;
            } else {
                return "Unknown Vehicle";
            }
        }
    }
}
