package com.obddroid.features.copilot.data;

import android.content.Context;

import androidx.annotation.Nullable;

import com.obddroid.features.copilot.agent.AgentCoPilotController;
import com.obddroid.utils.OpenAiService;
import com.obddroid.vehicle.VehicleManager;
import com.obddroid.vehicle.discovery.DiscoveryManager;

import io.github.vindecoder.nhtsa.VehicleData;

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
    private AgentCoPilotController agentController;

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
        agentController = AgentCoPilotController.getInstance();
        agentController.initialize(appContext);
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

        // Check if Agent API mode is enabled
        if (CoPilotSettings.isAgentApiEnabled(appContext)) {
            // Agent API mode: Use VIN-based persistent threads
            String vin = VehicleManager.getInstance().getCurrentVIN();
            VehicleData vehicleData = VehicleManager.getInstance().getCurrentVehicleData();

            JSONObject metadata = buildVehicleMetadata(vehicleData, adapterName);
            agentController.startSession(vin, metadata);
        } else {
            // Chat Completions mode: Use stateless session with history
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
    }

    public synchronized void endSession(String reason) {
        // End Agent API session if active
        if (agentController != null && CoPilotSettings.isAgentApiEnabled(appContext)) {
            agentController.endSession();
        }

        // End Chat Completions session if active
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
        // Route to Agent API if enabled
        if (CoPilotSettings.isAgentApiEnabled(appContext)) {
            agentController.sendUserMessage(message, callback);
            return;
        }

        // Chat Completions mode (original implementation)
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

    private JSONObject buildVehicleMetadata(@Nullable VehicleData vehicleData, @Nullable String adapterName) {
        JSONObject metadata = new JSONObject();
        try {
            if (vehicleData != null) {
                if (vehicleData.make != null) metadata.put("make", vehicleData.make);
                if (vehicleData.model != null) metadata.put("model", vehicleData.model);
                if (vehicleData.modelYear != null) metadata.put("year", vehicleData.modelYear);
                if (vehicleData.bodyClass != null) metadata.put("bodyClass", vehicleData.bodyClass);
                if (vehicleData.engineCylinders != null) metadata.put("cylinders", vehicleData.engineCylinders);
                if (vehicleData.displacementL != null) metadata.put("displacement", vehicleData.displacementL);
                if (vehicleData.fuelTypePrimary != null) metadata.put("fuelType", vehicleData.fuelTypePrimary);
            }
            if (adapterName != null) {
                metadata.put("adapter", adapterName);
            }
        } catch (JSONException e) {
            // Ignore metadata errors
        }
        return metadata;
    }
}
