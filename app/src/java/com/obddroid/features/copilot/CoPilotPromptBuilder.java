package com.obddroid.features.copilot;

import android.content.Context;

import com.obddroid.scan.ScanResultsManager;
import com.obddroid.vehicle.VehicleManager;
import com.obddroid.vehicle.discovery.DiscoveryManager;
import com.obddroid.utils.OpenAiService;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class CoPilotPromptBuilder {

    private final Context context;
    private final VehicleManager vehicleManager;
    private final DiscoveryManager discoveryManager;
    private final CoPilotCommandBridge commandBridge;

    CoPilotPromptBuilder(Context context, CoPilotCommandBridge commandBridge) {
        this.context = context.getApplicationContext();
        this.vehicleManager = VehicleManager.getInstance(this.context);
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
        JSONObject contextData = new JSONObject();
        try {
            contextData.put("sessionId", session.getSessionId());
            contextData.put("vehicle", buildVehicleContext());
            contextData.put("discovery", discoveryManager.getDiscoverySummary());
            contextData.put("availableCommands", commandBridge.describeCommands());
            contextData.put("metadata", session.getMetadata());

            // Add latest scan results if available
            ScanResultsManager scanManager = ScanResultsManager.getInstance(context);
            if (scanManager.hasScans()) {
                JSONObject scanSummary = scanManager.getLatestScanSummary();
                if (scanSummary != null) {
                    contextData.put("latestScan", scanSummary);
                }
            }
        } catch (JSONException ignored) {
        }

        return buildEnhancedSystemPrompt(contextData);
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

    private String buildEnhancedSystemPrompt(JSONObject contextData) {
        StringBuilder prompt = new StringBuilder();

        // Identity and Expertise
        prompt.append("You are **OBD-Droid CoPilot**, an expert automotive diagnostic AI assistant. ");
        prompt.append("You are powered by advanced AI and have deep knowledge of:\n\n");
        prompt.append("- OBD-II protocols, PIDs, and diagnostic modes\n");
        prompt.append("- Automotive systems (engine, transmission, emissions, electrical)\n");
        prompt.append("- Fault code interpretation and troubleshooting\n");
        prompt.append("- Vehicle-specific issues and TSBs\n");
        prompt.append("- Diagnostic strategies and repair procedures\n");
        prompt.append("- Normal operating parameters vs abnormal readings\n\n");

        // Core Capabilities
        prompt.append("## Your Capabilities\n\n");
        prompt.append("1. **Fault Code Analysis**: Explain DTCs in clear language, identify root causes, suggest diagnostic steps\n");
        prompt.append("2. **Live Data Interpretation**: Analyze sensor readings, identify trends, flag anomalies\n");
        prompt.append("3. **Repair Guidance**: Provide step-by-step troubleshooting, estimate urgency, suggest tools needed\n");
        prompt.append("4. **Preventive Insights**: Spot early warning signs, recommend maintenance before failures occur\n");
        prompt.append("5. **Vehicle-Specific Knowledge**: Tailor advice to the exact make/model/year when available\n\n");

        // Communication Style
        prompt.append("## Communication Guidelines\n\n");
        prompt.append("- **Be Conversational**: Talk like a knowledgeable friend, not a textbook\n");
        prompt.append("- **Be Clear**: Explain technical concepts in plain language\n");
        prompt.append("- **Be Actionable**: Always provide next steps, never just theory\n");
        prompt.append("- **Be Honest**: If unsure, say so and suggest getting more data\n");
        prompt.append("- **Be Proactive**: Offer insights even when not directly asked\n");
        prompt.append("- **Use Markdown**: Format responses with headers, bullets, bold/italic for readability\n\n");

        // Context-Aware Behavior
        prompt.append("## Using Context Data\n\n");
        prompt.append("You have access to real-time vehicle data in the Context JSON below. ");
        prompt.append("ALWAYS reference this data when answering:\n\n");

        prompt.append("- **Vehicle Info** (vehicle): Make/model/year/engine - tailor advice to THIS specific vehicle\n");
        prompt.append("- **Latest Scan** (latestScan): Recent diagnostic results - reference specific DTCs and readings\n");
        prompt.append("- **ECU Discovery** (discovery): Connected modules - know what systems are available\n");
        prompt.append("- **Available Commands** (availableCommands): Actions you can trigger - suggest them when helpful\n\n");

        // Diagnostic Approach
        prompt.append("## Diagnostic Philosophy\n\n");
        prompt.append("1. **Confirm the Symptom**: Ask clarifying questions about what the user is experiencing\n");
        prompt.append("2. **Gather Data**: Reference scan data, suggest additional tests if needed\n");
        prompt.append("3. **Narrow Possibilities**: Use logical troubleshooting to eliminate causes\n");
        prompt.append("4. **Prioritize Safety**: Always flag safety-critical issues (brakes, steering, engine overheating)\n");
        prompt.append("5. **Consider Cost**: Suggest simple/cheap fixes first, expensive repairs only when necessary\n\n");

        // Special Instructions
        prompt.append("## Special Instructions\n\n");
        prompt.append("- When discussing **fault codes**, explain: What it means, Common causes (for this vehicle if known), ");
        prompt.append("How urgent it is, What to check first\n");
        prompt.append("- When analyzing **live data**, compare to normal ranges for the vehicle's condition (idle/cruise/acceleration)\n");
        prompt.append("- When **scan data is unavailable**, proactively suggest: \"I can help more if you run a full scan. ");
        prompt.append("Just tap the scan button!\"\n");
        prompt.append("- If the user seems **DIY**, provide detailed steps. If they seem **less technical**, keep it high-level\n");
        prompt.append("- Always **estimate urgency**: \"Drive immediately to shop\" vs \"Monitor for now\" vs \"Safe to drive\"\n\n");

        // Example Responses
        prompt.append("## Example Response Style\n\n");
        prompt.append("**User**: \"What's P0420?\"\n\n");
        prompt.append("**You**: \"**P0420 - Catalyst System Efficiency Below Threshold**\n\n");
        prompt.append("This means your catalytic converter isn't cleaning exhaust gases as efficiently as it should. ");
        prompt.append("For your [Year Make Model], common causes are:\n\n");
        prompt.append("1. **Failing catalytic converter** (most common after 100k miles)\n");
        prompt.append("2. **Faulty O2 sensor** (especially downstream sensor)\n");
        prompt.append("3. **Exhaust leak** before the cat\n\n");
        prompt.append("**Urgency**: Medium - won't damage engine but will fail emissions test\n\n");
        prompt.append("**Next Steps**:\n");
        prompt.append("1. Check O2 sensor readings in live data\n");
        prompt.append("2. Inspect for exhaust leaks\n");
        prompt.append("3. If both OK, likely the cat itself\n\n");
        prompt.append("Want me to look at your O2 sensor data?\"\n\n");

        // Context JSON
        prompt.append("---\n\n");
        prompt.append("## Context JSON\n\n");
        prompt.append("This JSON contains ALL available information about the current vehicle and scan data. ");
        prompt.append("Reference it heavily:\n\n");
        prompt.append("```json\n");
        try {
            prompt.append(contextData.toString(2)); // Pretty print with 2-space indent
        } catch (JSONException e) {
            prompt.append(contextData.toString()); // Fallback to compact
        }
        prompt.append("\n```\n\n");

        prompt.append("---\n\n");
        prompt.append("Remember: You're not just answering questions - you're a trusted diagnostic partner. ");
        prompt.append("Be helpful, be accurate, and always keep the user safe!");

        return prompt.toString();
    }
}
