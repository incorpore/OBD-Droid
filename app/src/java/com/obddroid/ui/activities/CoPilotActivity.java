package com.obddroid.ui.activities;

import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.widget.NestedScrollView;

import com.obddroid.R;
import com.obddroid.copilot.CoPilotCallback;
import com.obddroid.copilot.CoPilotController;

public class CoPilotActivity extends AppCompatActivity {

    private EditText messageInput;
    private Button sendButton;
    private LinearLayout chatMessagesContainer;
    private NestedScrollView chatScrollView;
    private ProgressBar progressBar;
    private TextView copilotStatus;

    private CoPilotController copilotController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_copilot);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("CoPilot");
        }

        copilotController = CoPilotController.getInstance();

        initializeViews();
        setupListeners();
        updateStatus();
    }

    private void initializeViews() {
        messageInput = findViewById(R.id.message_input);
        sendButton = findViewById(R.id.send_button);
        chatMessagesContainer = findViewById(R.id.chat_messages_container);
        chatScrollView = findViewById(R.id.chat_scroll_view);
        progressBar = findViewById(R.id.progress_bar);
        copilotStatus = findViewById(R.id.copilot_status);
    }

    private void setupListeners() {
        sendButton.setOnClickListener(v -> sendMessage());

        messageInput.setOnEditorActionListener((v, actionId, event) -> {
            sendMessage();
            return true;
        });
    }

    private void updateStatus() {
        // Check if CoPilot is active (session started)
        // For now, just show ready
        copilotStatus.setText("Ready - Connected to vehicle");
    }

    private void sendMessage() {
        String message = messageInput.getText().toString().trim();

        if (TextUtils.isEmpty(message)) {
            return;
        }

        // Add user message to chat
        addMessageToChat(message, true);

        // Clear input
        messageInput.setText("");

        // Show progress
        progressBar.setVisibility(View.VISIBLE);
        sendButton.setEnabled(false);

        // Send to CoPilot
        copilotController.sendUserMessage(message, new CoPilotCallback() {
            @Override
            public void onResponse(String response) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    sendButton.setEnabled(true);
                    addMessageToChat(response, false);
                });
            }

            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    sendButton.setEnabled(true);
                    Toast.makeText(CoPilotActivity.this,
                        "Error: " + errorMessage,
                        Toast.LENGTH_LONG).show();
                    addMessageToChat("Error: " + errorMessage, false);
                });
            }
        });
    }

    private void addMessageToChat(String message, boolean isUser) {
        // Create a card for the message
        CardView cardView = new CardView(this);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, dpToPx(8));

        if (isUser) {
            cardParams.gravity = Gravity.END;
            cardView.setCardBackgroundColor(getColor(R.color.colorPrimary));
        } else {
            cardParams.gravity = Gravity.START;
            cardView.setCardBackgroundColor(getColor(R.color.background_secondary));
        }

        cardView.setLayoutParams(cardParams);
        cardView.setRadius(dpToPx(8));
        cardView.setCardElevation(dpToPx(2));
        cardView.setUseCompatPadding(true);

        // Create text view for message
        TextView textView = new TextView(this);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        textView.setLayoutParams(textParams);
        textView.setText(message);
        textView.setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12));

        if (isUser) {
            textView.setTextColor(Color.WHITE);
        } else {
            textView.setTextColor(getColor(R.color.text_primary));
        }

        textView.setTextSize(14);

        cardView.addView(textView);
        chatMessagesContainer.addView(cardView);

        // Scroll to bottom
        chatScrollView.post(() -> chatScrollView.fullScroll(View.FOCUS_DOWN));
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
