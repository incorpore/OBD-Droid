package com.obddroid.features.copilot.data;

import android.content.Context;

import androidx.annotation.Nullable;

import com.obddroid.features.copilot.agent.AgentCoPilotController;
import com.obddroid.services.VehicleManager;

import io.github.vindecoder.nhtsa.VehicleData;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Entry point for CoPilot conversations using OpenAI Agent API.
 * Provides VIN-based persistent conversations with tool calling support.
 */
public final class CoPilotController {

    private static CoPilotController instance;

    public static synchronized CoPilotController getInstance() {
        if (instance == null) {
            instance = new CoPilotController();
        }
        return instance;
    }

    private Context appContext;
    private AgentCoPilotController agentController;

    private CoPilotController() {
    }

    public synchronized void initialize(Context context) {
        if (appContext != null) {
            return;
        }
        appContext = context.getApplicationContext();
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

        // Get VIN and vehicle data for persistent thread
        String vin = VehicleManager.getInstance().getCurrentVIN();
        VehicleData vehicleData = VehicleManager.getInstance().getCurrentVehicleData();

        JSONObject metadata = buildVehicleMetadata(vehicleData, adapterName);
        agentController.startSession(vin, metadata);
    }

    public synchronized void endSession(String reason) {
        if (agentController != null) {
            agentController.endSession();
        }
    }

    public void sendUserMessage(String message, CoPilotCallback callback) {
        agentController.sendUserMessage(message, callback);
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
