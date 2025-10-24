package com.obddroid.copilot;

import android.content.Context;

import com.obddroid.vehicle.VehicleManager;
import com.obddroid.vehicle.discovery.DiscoveryManager;
import com.obddroid.utils.OpenAiService;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class CoPilotPromptBuilder {

    private final VehicleManager vehicleManager;
    private final DiscoveryManager discoveryManager;
    private final CoPilotCommandBridge commandBridge;

    CoPilotPromptBuilder(Context context, CoPilotCommandBridge commandBridge) {
        this.vehicleManager = VehicleManager.getInstance(context.getApplicationContext());
        this.discoveryManager = DiscoveryManager.getInstance();
        this.commandBridge = commandBridge;
    }

    List<OpenAiService.ChatMessage> buildPrompt(CoPilotSession session,
                                               List<CoPilotMessage> history) {
        List<OpenAiService.ChatMessage> chatMessages = new ArrayList<>();
        chatMessages.add(new OpenAiService.ChatMessage("system", buildSystemPrompt(session)));
        for (CoPilotMessage message : history) {
            chatMessages.add(message.toChatMessage());
        }
        return chatMessages;
    }

    private String buildSystemPrompt(CoPilotSession session) {
        JSONObject context = new JSONObject();
        try {
            context.put("sessionId", session.getSessionId());
            context.put("vehicle", buildVehicleContext());
            context.put("discovery", discoveryManager.getDiscoverySummary());
            context.put("availableCommands", commandBridge.describeCommands());
            context.put("metadata", session.getMetadata());
        } catch (JSONException ignored) {
        }

        return "You are OBD Droid CoPilot, a proactive diagnostic assistant. " +
            "Respond conversationally, reference the provided JSON context, and " +
            "offer actionable guidance. When unsure, request more data or suggest " +
            "running relevant scans. Context JSON:\n" + context.toString();
    }

    private JSONObject buildVehicleContext() throws JSONException {
        JSONObject vehicleJson = new JSONObject();
        String vin = vehicleManager.getCurrentVIN();
        if (vin != null) {
            vehicleJson.put("vin", vin);
        }
        if (vehicleManager.getCurrentVehicleData() != null) {
            vehicleJson.put("make", vehicleManager.getCurrentVehicleData().getMake());
            vehicleJson.put("model", vehicleManager.getCurrentVehicleData().getModel());
            vehicleJson.put("year", vehicleManager.getCurrentVehicleData().getModelYear());
            vehicleJson.put("trim", vehicleManager.getCurrentVehicleData().trim);
            vehicleJson.put("engine", formatEngineDescription());
        }
        vehicleJson.put("ecuConnected", vehicleManager.isECUConnected());
        vehicleJson.put("manufacturer", vehicleManager.getManufacturer());
        vehicleJson.put("displayName", vehicleManager.getVehicleDisplayName());

        JSONArray addresses = new JSONArray();
        for (Integer address : discoveryManager.getDiscoveredAddressesSnapshot()) {
            addresses.put(String.format("0x%X", address));
        }
        vehicleJson.put("ecuAddresses", addresses);
        vehicleJson.put("ecuSnapshots", discoveryManager.getAllEcuSnapshots());
        return vehicleJson;
    }

    private String formatEngineDescription() {
        if (vehicleManager.getCurrentVehicleData() == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        String displacement = vehicleManager.getCurrentVehicleData().displacementL;
        if (displacement != null && !displacement.isEmpty()) {
            builder.append(displacement).append("L");
        }
        String cylinders = vehicleManager.getCurrentVehicleData().engineCylinders;
        if (cylinders != null && !cylinders.isEmpty()) {
            if (builder.length() > 0) builder.append(' ');
            builder.append(cylinders).append("-cylinder");
        }
        String fuel = vehicleManager.getCurrentVehicleData().fuelTypePrimary;
        if (fuel != null && !fuel.isEmpty() && !"Not Applicable".equalsIgnoreCase(fuel)) {
            if (builder.length() > 0) builder.append(' ');
            builder.append(fuel);
        }
        return builder.toString();
    }
}
