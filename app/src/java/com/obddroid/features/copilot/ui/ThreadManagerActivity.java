package com.obddroid.features.copilot.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.obddroid.R;
import com.obddroid.features.copilot.data.AgentApiClient;
import com.obddroid.features.copilot.data.AgentCoPilotController;
import com.obddroid.features.copilot.data.AgentThreadManager;
import com.obddroid.features.copilot.data.AgentThreadManager.ThreadOverview;
import com.obddroid.scan.ScanResultsManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

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

    private static final String PREFS_PRIVACY = "copilot_privacy";
    private static final String KEY_PRIVACY_ACK = "thread_manager_privacy_ack";
    private static final SimpleDateFormat SCAN_DATE_FORMAT =
        new SimpleDateFormat("MMM d, yyyy h:mm a", Locale.US);

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
        maybeShowPrivacyDialog();
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
        adapter = new ThreadListAdapter(
            threads,
            this::onThreadClicked,
            this::onDeleteThread,
            this::onCompareScans,
            this::onExportThread
        );
        threadsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        threadsRecyclerView.setAdapter(adapter);
    }

    private void setupDeleteAllButton() {
        deleteAllFab.setOnClickListener(v -> showDeleteAllConfirmation());
    }

    private void maybeShowPrivacyDialog() {
        boolean acknowledged = getSharedPreferences(PREFS_PRIVACY, MODE_PRIVATE)
            .getBoolean(KEY_PRIVACY_ACK, false);
        if (acknowledged) {
            return;
        }

        new AlertDialog.Builder(this)
            .setTitle("CoPilot Conversations & Privacy")
            .setMessage("CoPilot stores your conversation history (including uploaded scan reports) on your device and with OpenAI so sessions can resume seamlessly.\n\n" +
                "You can delete any conversation at any time, or wipe all data using the button below. " +
                "Export conversations before deleting if you want to keep a record.")
            .setCancelable(false)
            .setPositiveButton("I Understand", (dialog, which) -> {
                getSharedPreferences(PREFS_PRIVACY, MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_PRIVACY_ACK, true)
                    .apply();
                dialog.dismiss();
            })
            .setNegativeButton("Close Screen", (dialog, which) -> {
                dialog.dismiss();
                finish();
            })
            .show();
    }

    private void loadThreads() {
        new Thread(() -> {
            try {
                List<ThreadItem> loadedThreads = new ArrayList<>();

                List<ThreadOverview> overviews = threadManager.getThreadOverviews(5);
                for (ThreadOverview overview : overviews) {
                    loadedThreads.add(ThreadItem.fromOverview(overview));
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
        startCopilotForThread(thread, null);
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

    private void onCompareScans(ThreadItem thread) {
        if (TextUtils.isEmpty(thread.comparePrompt)) {
            Toast.makeText(this, "Need at least two scans to compare", Toast.LENGTH_SHORT).show();
            return;
        }
        startCopilotForThread(thread, thread.comparePrompt);
    }

    private void onExportThread(ThreadItem thread) {
        performExportThread(thread);
    }

    private void startCopilotForThread(ThreadItem thread, String initialMessage) {
        AgentCoPilotController controller = AgentCoPilotController.getInstance();
        controller.initialize(getApplicationContext());
        controller.startSession(thread.vin, thread.getMetadataCopy());

        Intent intent = new Intent(this, CoPilotActivity.class);
        if (!TextUtils.isEmpty(initialMessage)) {
            intent.putExtra(CoPilotActivity.EXTRA_INITIAL_MESSAGE, initialMessage);
        }
        startActivity(intent);
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

    private void performExportThread(ThreadItem thread) {
        if (TextUtils.isEmpty(thread.threadId)) {
            Toast.makeText(this, "Unable to export: conversation missing thread id", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            try {
                AgentThreadManager.ThreadOverview overview = thread.overview;
                AgentCoPilotController controller = AgentCoPilotController.getInstance();
                controller.initialize(getApplicationContext());
                AgentApiClient apiClient = new AgentApiClient(getApplicationContext());

                AgentApiClient.Thread threadInfo = apiClient.retrieveThread(thread.threadId);
                List<AgentApiClient.Message> messages = apiClient.listMessages(thread.threadId, 100);
                if (messages == null) {
                    messages = Collections.emptyList();
                }

                JSONObject export = new JSONObject();
                export.put("threadId", thread.threadId);
                export.put("vin", thread.vin);
                export.put("displayName", thread.getDisplayName());
                export.put("metadata", thread.getMetadataCopy());
                export.put("createdAt", threadInfo != null ? threadInfo.createdAt : 0);

                JSONArray scansArray = new JSONArray();
                if (overview.scanHistory != null) {
                    for (ScanResultsManager.ScanSummary summary : overview.scanHistory) {
                        JSONObject scanJson = new JSONObject();
                        scanJson.put("scanId", summary.scanId);
                        scanJson.put("timestamp", summary.timestamp);
                        scanJson.put("success", summary.success);
                        scanJson.put("stageCount", summary.stageCount);
                        scanJson.put("fileId", summary.fileId);
                        scanJson.put("fileAttached", summary.fileAttached);
                        scansArray.put(scanJson);
                    }
                }
                export.put("recentScans", scansArray);

                JSONArray messageArray = new JSONArray();
                // listMessages returns newest-first, reverse for chronological order
                for (int i = messages.size() - 1; i >= 0; i--) {
                    AgentApiClient.Message message = messages.get(i);
                    JSONObject messageJson = new JSONObject();
                    messageJson.put("id", message.id);
                    messageJson.put("role", message.role);
                    messageJson.put("content", message.content);
                    messageJson.put("createdAt", message.createdAt);
                    messageArray.put(messageJson);
                }
                export.put("messages", messageArray);

                File exportDir = new File(getCacheDir(), "copilot_exports");
                if (!exportDir.exists() && !exportDir.mkdirs()) {
                    throw new IOException("Unable to create export directory");
                }

                String safeId = thread.threadId != null
                    ? thread.threadId.replaceAll("[^A-Za-z0-9_-]", "")
                    : "thread";
                File exportFile = new File(exportDir, safeId + "_conversation.json");

                try (FileWriter writer = new FileWriter(exportFile, false)) {
                    writer.write(export.toString(2));
                }

                Uri uri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".provider",
                    exportFile
                );

                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("application/json");
                shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
                shareIntent.putExtra(Intent.EXTRA_SUBJECT, "CoPilot conversation export");
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                runOnUiThread(() -> {
                    Toast.makeText(this, "Conversation export ready", Toast.LENGTH_SHORT).show();
                    startActivity(Intent.createChooser(shareIntent, "Share CoPilot conversation"));
                });

            } catch (Exception e) {
                runOnUiThread(() ->
                    Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
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
        private final ThreadOverview overview;
        public final String vin;
        public final String threadId;
        private final String displayName;
        private final JSONObject metadata;
        public final ScanResultsManager.ScanSummary latestScan;
        public final ScanResultsManager.ScanSummary previousScan;
        public final String lastScanSummary;
        public final String fileStatusText;
        public final String comparePrompt;
        public final boolean canExport;

        private ThreadItem(ThreadOverview overview,
                           String vin,
                           String threadId,
                           String displayName,
                           JSONObject metadata,
                           ScanResultsManager.ScanSummary latestScan,
                           ScanResultsManager.ScanSummary previousScan,
                           String lastScanSummary,
                           String fileStatusText,
                           String comparePrompt,
                           boolean canExport) {
            this.overview = overview;
            this.vin = vin;
            this.threadId = threadId;
            this.displayName = displayName;
            this.metadata = metadata;
            this.latestScan = latestScan;
            this.previousScan = previousScan;
            this.lastScanSummary = lastScanSummary;
            this.fileStatusText = fileStatusText;
            this.comparePrompt = comparePrompt;
            this.canExport = canExport;
        }

        static ThreadItem fromOverview(ThreadOverview overview) {
            JSONObject metadataCopy = cloneJson(overview.metadata);

            String label = buildVehicleLabel(metadataCopy);
            if (TextUtils.isEmpty(label) && overview.latestScan() != null &&
                !TextUtils.isEmpty(overview.latestScan().vehicleLabel)) {
                label = overview.latestScan().vehicleLabel;
            }
            if (TextUtils.isEmpty(label) && overview.scanHistory != null && !overview.scanHistory.isEmpty()) {
                ScanResultsManager.ScanSummary first = overview.scanHistory.get(0);
                if (first != null && !TextUtils.isEmpty(first.vehicleLabel)) {
                    label = first.vehicleLabel;
                }
            }
            if (TextUtils.isEmpty(label)) {
                label = !TextUtils.isEmpty(overview.vin) ? overview.vin : "Unknown Vehicle";
            }

            ScanResultsManager.ScanSummary latest = overview.latestScan();
            ScanResultsManager.ScanSummary previous = overview.previousScan();

            String lastScanSummary = null;
            String fileStatus = null;
            boolean canExport = !TextUtils.isEmpty(overview.threadId);

            if (latest != null) {
                lastScanSummary = "Latest scan: " + formatScan(latest);
                if (!TextUtils.isEmpty(latest.fileId)) {
                    fileStatus = latest.fileAttached ? "Synced to CoPilot ✓"
                        : "Upload pending—syncing to CoPilot";
                } else {
                    fileStatus = "No scan upload yet";
                }
                canExport = true;
            }

            String comparePrompt = overview.buildComparePrompt();

            return new ThreadItem(
                overview,
                overview.vin,
                overview.threadId,
                label,
                metadataCopy,
                latest,
                previous,
                lastScanSummary,
                fileStatus,
                comparePrompt,
                canExport
            );
        }

        public String getDisplayName() {
            return displayName;
        }

        public JSONObject getMetadataCopy() {
            return cloneJson(metadata);
        }

        private static String buildVehicleLabel(JSONObject metadata) {
            if (metadata == null) {
                return null;
            }

            String year = metadata.optString("year", "");
            String make = metadata.optString("make", "");
            String model = metadata.optString("model", "");
            String vehicleLabel = metadata.optString("vehicleLabel", "");

            StringBuilder sb = new StringBuilder();
            if (!TextUtils.isEmpty(year)) {
                sb.append(year).append(" ");
            }
            if (!TextUtils.isEmpty(make)) {
                sb.append(make);
            }
            if (!TextUtils.isEmpty(model)) {
                if (sb.length() > 0) {
                    sb.append(" ");
                }
                sb.append(model);
            }

            String label = sb.toString().trim();
            if (TextUtils.isEmpty(label)) {
                label = vehicleLabel;
            }
            return TextUtils.isEmpty(label) ? null : label;
        }

        private static String formatScan(ScanResultsManager.ScanSummary summary) {
            Date date = summary.timestamp > 0 ? new Date(summary.timestamp) : new Date();
            StringBuilder sb = new StringBuilder();
            synchronized (SCAN_DATE_FORMAT) {
                sb.append(SCAN_DATE_FORMAT.format(date));
            }
            sb.append(" • ");
            sb.append(summary.success ? "Success" : "Issues");
            if (summary.stageCount > 0) {
                sb.append(" • ").append(summary.stageCount).append(" stages");
            }
            return sb.toString();
        }

        private static JSONObject cloneJson(JSONObject json) {
            if (json == null) {
                return null;
            }
            try {
                return new JSONObject(json.toString());
            } catch (JSONException e) {
                return null;
            }
        }
    }
}
