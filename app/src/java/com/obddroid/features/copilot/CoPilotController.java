package com.obddroid.features.copilot;

import android.content.Context;

import androidx.annotation.Nullable;

import com.obddroid.utils.OpenAiService;
import com.obddroid.vehicle.discovery.DiscoveryManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Entry point for CoPilot conversations. Handles session lifecycle, prompt
 * building, logging, and command dispatch.
 */
public final class CoPilotController {

    private static CoPilotController instance;

    public static synchronized CoPilotController getInstance() {
        if (instance == null) {
            instance = new CoPilotController();
        }
        return instance;
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "copilot-worker");
        thread.setDaemon(true);
        return thread;
    });

    private Context appContext;
    private OpenAiService openAiService;
    private CoPilotCommandBridge commandBridge;
    private CoPilotPromptBuilder promptBuilder;
    private CoPilotSession session;
    private CoPilotLogger logger;

    private CoPilotController() {
    }

    public synchronized void initialize(Context context) {
        if (appContext != null) {
            return;
        }
        appContext = context.getApplicationContext();
        openAiService = new OpenAiService(appContext);
        commandBridge = new CoPilotCommandBridge();
        promptBuilder = new CoPilotPromptBuilder(appContext, commandBridge);
    }

    public synchronized void startSession(@Nullable String adapterName) {
        if (appContext == null) {
            return;
        }
        if (!CoPilotSettings.isEnabled(appContext)) {
            return;
        }
        if (session != null) {
            endSession("restarted");
        }

        String baseSessionId = DiscoveryManager.getInstance().getActiveSessionId();
        String effectiveSessionId = baseSessionId != null ? baseSessionId : generateStandaloneSessionId();

        JSONObject metadata = new JSONObject();
        try {
            metadata.put("adapter", adapterName);
        } catch (JSONException ignored) {
        }

        session = new CoPilotSession(appContext, effectiveSessionId, metadata);
        try {
            logger = CoPilotLogger.open(appContext, effectiveSessionId);
            logger.logEvent("session_start", metadata);
        } catch (IOException ignored) {
        }
    }

    public synchronized void endSession(String reason) {
        if (session == null) {
            return;
        }
        try {
            if (logger != null) {
                JSONObject payload = new JSONObject();
                payload.put("reason", reason);
                logger.logEvent("session_end", payload);
                logger.close();
            }
        } catch (Exception ignored) {
        }
        logger = null;
        session = null;
    }

    public void sendUserMessage(String message, CoPilotCallback callback) {
        CoPilotSession activeSession;
        CoPilotLogger activeLogger;
        synchronized (this) {
            if (session == null) {
                callback.onError("CoPilot is not active. Connect to a vehicle first.");
                return;
            }
            activeSession = session;
            activeLogger = logger;
            activeSession.appendMessage(new CoPilotMessage("user", message));
            if (activeLogger != null) {
                JSONObject payload = new JSONObject();
                try {
                    payload.put("role", "user");
                    payload.put("content", message);
                } catch (JSONException ignored) {
                }
                activeLogger.logEvent("message", payload);
            }
        }

        executor.execute(() -> {
            try {
                List<CoPilotMessage> history = activeSession.getHistorySnapshot();
                List<OpenAiService.ChatMessage> prompt = promptBuilder.buildPrompt(activeSession, history);
                String responseText = openAiService.completeChat(prompt, 600, 0.6d);

                CoPilotMessage assistantMessage = new CoPilotMessage("assistant", responseText);
                synchronized (CoPilotController.this) {
                    if (session != null) {
                        session.appendMessage(assistantMessage);
                        if (logger != null) {
                            JSONObject payload = new JSONObject();
                            try {
                                payload.put("role", "assistant");
                                payload.put("content", responseText);
                            } catch (JSONException ignored) {
                            }
                            logger.logEvent("message", payload);
                        }
                    }
                }
                callback.onResponse(responseText);
            } catch (Exception e) {
                callback.onError(e.getMessage());
            }
        });
    }

    private String generateStandaloneSessionId() {
        return "copilot_" + System.currentTimeMillis();
    }
}
