package com.obddroid.scan;

import org.json.JSONObject;

/**
 * Result of executing a single scan stage.
 */
public final class StageResult {

    public enum Status {
        SUCCESS,        // Stage completed successfully
        SKIPPED,        // Stage was skipped (not applicable)
        FAILED,         // Stage failed but scan can continue
        FATAL_ERROR     // Stage failed and scan should abort
    }

    private final Status status;
    private final String message;
    private final JSONObject data;
    private final Exception error;

    private StageResult(Status status, String message, JSONObject data, Exception error) {
        this.status = status;
        this.message = message;
        this.data = data;
        this.error = error;
    }

    public static StageResult success(String message, JSONObject data) {
        return new StageResult(Status.SUCCESS, message, data, null);
    }

    public static StageResult skipped(String reason) {
        return new StageResult(Status.SKIPPED, reason, null, null);
    }

    public static StageResult failed(String message, Exception error) {
        return new StageResult(Status.FAILED, message, null, error);
    }

    public static StageResult fatalError(String message, Exception error) {
        return new StageResult(Status.FATAL_ERROR, message, null, error);
    }

    public Status getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public JSONObject getData() {
        return data;
    }

    public Exception getError() {
        return error;
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    public boolean isFatal() {
        return status == Status.FATAL_ERROR;
    }
}
