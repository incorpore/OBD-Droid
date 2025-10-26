package com.obddroid.features.copilot.data;

/**
 * Callback interface for CoPilot responses.
 */
public interface CoPilotCallback {

    void onResponse(String message);

    void onError(String errorMessage);
}
