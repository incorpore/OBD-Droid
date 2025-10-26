package com.obddroid.features.copilot.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.airbnb.lottie.LottieAnimationView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;

import com.obddroid.R;
import com.obddroid.features.copilot.ChatMessage;
import com.obddroid.features.copilot.CoPilotCallback;
import com.obddroid.features.copilot.CoPilotController;
import com.obddroid.features.copilot.CoPilotTtsManager;
import com.obddroid.features.copilot.ui.ChatMessageAdapter;

import java.util.ArrayList;

import io.noties.markwon.Markwon;

public class CoPilotActivity extends AppCompatActivity {

    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;

    private LottieAnimationView aiAvatar;
    private TextView copilotStatus;
    private TextInputEditText messageInput;
    private FloatingActionButton sendButton;
    private FloatingActionButton voiceButton;
    private RecyclerView chatRecyclerView;
    private LinearLayout typingIndicator;
    private FrameLayout loadingOverlay;
    private TextView loadingText;
    private ChipGroup suggestionChips;

    private ChatMessageAdapter chatAdapter;
    private CoPilotController copilotController;
    private CoPilotTtsManager ttsManager;
    private Markwon markwon;
    private SpeechRecognizer speechRecognizer;
    private SharedPreferences preferences;
    private boolean isListening = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_copilot);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("CoPilot AI Assistant");
        }

        copilotController = CoPilotController.getInstance();
        ttsManager = CoPilotTtsManager.getInstance(this);
        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        // Initialize Markwon for markdown rendering
        markwon = Markwon.create(this);

        // Configure TTS from settings
        boolean ttsEnabled = preferences.getBoolean("copilot_tts_enabled", true);
        ttsManager.setEnabled(ttsEnabled);

        initializeViews();
        setupRecyclerView();
        setupListeners();
        setupSpeechRecognizer();
        updateStatus();
        showWelcomeMessage();
    }

    private void initializeViews() {
        aiAvatar = findViewById(R.id.ai_avatar);
        copilotStatus = findViewById(R.id.copilot_status);
        messageInput = findViewById(R.id.message_input);
        sendButton = findViewById(R.id.send_button);
        voiceButton = findViewById(R.id.voice_button);
        chatRecyclerView = findViewById(R.id.chat_recycler_view);
        typingIndicator = findViewById(R.id.typing_indicator);
        loadingOverlay = findViewById(R.id.loading_overlay);
        loadingText = findViewById(R.id.loading_text);
        suggestionChips = findViewById(R.id.suggestion_chips);
    }

    private void setupRecyclerView() {
        chatAdapter = new ChatMessageAdapter(markwon);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        chatRecyclerView.setLayoutManager(layoutManager);
        chatRecyclerView.setAdapter(chatAdapter);

        // Setup speaker button click listener for TTS
        chatAdapter.setSpeakerClickListener((message, speakerButton) -> {
            playResponseAudio(message.getContent(), speakerButton);
        });
    }

    private void setupListeners() {
        sendButton.setOnClickListener(v -> sendMessage());

        voiceButton.setOnClickListener(v -> toggleVoiceInput());

        messageInput.setOnEditorActionListener((v, actionId, event) -> {
            sendMessage();
            return true;
        });

        // Setup suggestion chip listeners
        for (int i = 0; i < suggestionChips.getChildCount(); i++) {
            View child = suggestionChips.getChildAt(i);
            if (child instanceof Chip) {
                Chip chip = (Chip) child;
                chip.setOnClickListener(v -> {
                    messageInput.setText(chip.getText());
                    sendMessage();
                });
            }
        }
    }

    private void setupSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onReadyForSpeech(Bundle params) {
                    copilotStatus.setText("Listening...");
                }

                @Override
                public void onBeginningOfSpeech() {
                    // Voice detected
                }

                @Override
                public void onRmsChanged(float rmsdB) {
                    // Audio level changed (could animate avatar here)
                }

                @Override
                public void onBufferReceived(byte[] buffer) {
                }

                @Override
                public void onEndOfSpeech() {
                    copilotStatus.setText("Processing...");
                }

                @Override
                public void onError(int error) {
                    isListening = false;
                    voiceButton.setImageResource(android.R.drawable.ic_btn_speak_now);
                    updateStatus();
                    Toast.makeText(CoPilotActivity.this, "Voice recognition error", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onResults(Bundle results) {
                    isListening = false;
                    voiceButton.setImageResource(android.R.drawable.ic_btn_speak_now);
                    updateStatus();

                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        String recognizedText = matches.get(0);
                        messageInput.setText(recognizedText);
                        sendMessage();
                    }
                }

                @Override
                public void onPartialResults(Bundle partialResults) {
                }

                @Override
                public void onEvent(int eventType, Bundle params) {
                }
            });
        }
    }

    private void toggleVoiceInput() {
        if (speechRecognizer == null) {
            Toast.makeText(this, "Voice recognition not available", Toast.LENGTH_SHORT).show();
            return;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    REQUEST_RECORD_AUDIO_PERMISSION);
            return;
        }

        if (isListening) {
            speechRecognizer.stopListening();
            isListening = false;
            voiceButton.setImageResource(android.R.drawable.ic_btn_speak_now);
        } else {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE,
                    getPackageName());
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);

            speechRecognizer.startListening(intent);
            isListening = true;
            voiceButton.setImageResource(android.R.drawable.ic_notification_clear_all);
            animateAvatar(true);
        }
    }

    private void updateStatus() {
        copilotStatus.setText("Ready to assist with diagnostics");
    }

    private void showWelcomeMessage() {
        ChatMessage welcomeMsg = new ChatMessage(
                "Hello! I'm your AI diagnostic assistant. I can help you:\n\n" +
                "• Explain scan results and fault codes\n" +
                "• Provide repair recommendations\n" +
                "• Answer questions about your vehicle\n" +
                "• Guide you through diagnostics\n\n" +
                "How can I help you today?",
                false,
                System.currentTimeMillis()
        );
        chatAdapter.addMessage(welcomeMsg);
    }

    private void sendMessage() {
        String message = messageInput.getText().toString().trim();

        if (TextUtils.isEmpty(message)) {
            return;
        }

        // Add user message to chat
        ChatMessage userMsg = new ChatMessage(message, true, System.currentTimeMillis());
        chatAdapter.addMessage(userMsg);

        // Clear input
        messageInput.setText("");

        // Show typing indicator
        showTypingIndicator(true);
        animateAvatar(true);

        // Send to CoPilot
        copilotController.sendUserMessage(message, new CoPilotCallback() {
            @Override
            public void onResponse(String response) {
                runOnUiThread(() -> {
                    showTypingIndicator(false);
                    animateAvatar(false);

                    ChatMessage aiMsg = new ChatMessage(response, false, System.currentTimeMillis());
                    chatAdapter.addMessage(aiMsg);

                    // Scroll to bottom
                    chatRecyclerView.smoothScrollToPosition(chatAdapter.getItemCount() - 1);

                    // Auto-play TTS if enabled
                    boolean autoPlay = preferences.getBoolean("copilot_tts_auto_play", true);
                    if (autoPlay && ttsManager.isEnabled()) {
                        playResponseAudio(response, null);
                    }
                });
            }

            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() -> {
                    showTypingIndicator(false);
                    animateAvatar(false);

                    Toast.makeText(CoPilotActivity.this,
                            "Error: " + errorMessage,
                            Toast.LENGTH_LONG).show();

                    ChatMessage errorMsg = new ChatMessage(
                            "I apologize, but I encountered an error: " + errorMessage +
                            "\n\nPlease check your internet connection and OpenAI API key in settings.",
                            false,
                            System.currentTimeMillis()
                    );
                    chatAdapter.addMessage(errorMsg);

                    chatRecyclerView.smoothScrollToPosition(chatAdapter.getItemCount() - 1);
                });
            }
        });
    }

    private void showTypingIndicator(boolean show) {
        typingIndicator.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void animateAvatar(boolean active) {
        if (aiAvatar != null) {
            if (active) {
                aiAvatar.setSpeed(1.5f);
                aiAvatar.playAnimation();
            } else {
                aiAvatar.setSpeed(1.0f);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                toggleVoiceInput();
            } else {
                Toast.makeText(this, "Microphone permission required for voice input",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * Plays audio for an AI response using TTS.
     */
    private void playResponseAudio(String text, ImageButton speakerButton) {
        if (!ttsManager.isEnabled()) {
            Toast.makeText(this, "Voice responses are disabled in settings", Toast.LENGTH_SHORT).show();
            return;
        }

        // Stop any currently playing audio
        ttsManager.stopPlayback();

        // Update status
        copilotStatus.setText("Speaking...");

        // Animate avatar faster during speech
        animateAvatar(true);

        ttsManager.speak(text, new CoPilotTtsManager.TtsCallback() {
            @Override
            public void onTtsStart() {
                runOnUiThread(() -> {
                    if (speakerButton != null) {
                        speakerButton.setImageResource(android.R.drawable.ic_media_pause);
                    }
                });
            }

            @Override
            public void onTtsComplete() {
                runOnUiThread(() -> {
                    copilotStatus.setText("Ready to assist with diagnostics");
                    animateAvatar(false);
                    if (speakerButton != null) {
                        speakerButton.setImageResource(android.R.drawable.ic_lock_silent_mode_off);
                    }
                });
            }

            @Override
            public void onTtsError(String error) {
                runOnUiThread(() -> {
                    copilotStatus.setText("Ready to assist with diagnostics");
                    animateAvatar(false);
                    if (speakerButton != null) {
                        speakerButton.setImageResource(android.R.drawable.ic_lock_silent_mode_off);
                    }
                    Toast.makeText(CoPilotActivity.this,
                            "Voice playback error: " + error,
                            Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        if (ttsManager != null) {
            ttsManager.cleanup();
        }
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
