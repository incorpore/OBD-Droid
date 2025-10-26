package com.obddroid.core.obd;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * Comprehensive error handling for OBD protocol operations
 * Provides error recovery, retry logic, and diagnostics
 *
 * @author Wal33D
 */
public class ErrorHandler {
    private static final Logger log = Logger.getLogger(ErrorHandler.class.getName());

    // Error types
    public enum ErrorType {
        COMMUNICATION_ERROR("Communication failure"),
        TIMEOUT("Operation timed out"),
        PROTOCOL_ERROR("Protocol error"),
        INVALID_RESPONSE("Invalid response from ECU"),
        SERVICE_NOT_SUPPORTED("Service not supported"),
        NO_DATA("No data available"),
        BUFFER_OVERFLOW("Buffer overflow"),
        CAN_ERROR("CAN bus error"),
        CONNECTION_LOST("Connection lost"),
        INITIALIZATION_FAILED("Initialization failed"),
        UNKNOWN("Unknown error");

        private final String description;

        ErrorType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    // Error severity levels
    public enum Severity {
        INFO,      // Informational, no action needed
        WARNING,   // Warning, operation continues
        ERROR,     // Error, operation may be retried
        CRITICAL   // Critical, operation cannot continue
    }

    /**
     * Error event structure
     */
    public static class ErrorEvent {
        public final ErrorType type;
        public final Severity severity;
        public final String message;
        public final String context;
        public final Exception exception;
        public final long timestamp;
        public final int retryCount;

        public ErrorEvent(ErrorType type, Severity severity, String message,
                          String context, Exception exception, int retryCount) {
            this.type = type;
            this.severity = severity;
            this.message = message;
            this.context = context;
            this.exception = exception;
            this.timestamp = System.currentTimeMillis();
            this.retryCount = retryCount;
        }
    }

    // Error statistics
    private final AtomicInteger totalErrors = new AtomicInteger(0);
    private final AtomicInteger recoveredErrors = new AtomicInteger(0);
    private final AtomicInteger criticalErrors = new AtomicInteger(0);

    // Error history (bounded)
    private final ConcurrentLinkedQueue<ErrorEvent> errorHistory = new ConcurrentLinkedQueue<>();
    private static final int MAX_HISTORY_SIZE = 100;

    // Retry configuration
    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 500;

    // Error listeners
    private ErrorListener errorListener;

    /**
     * Error listener interface
     */
    public interface ErrorListener {
        void onError(ErrorEvent error);
        void onRecovery(ErrorEvent error);
    }

    /**
     * Set error listener
     */
    public void setErrorListener(ErrorListener listener) {
        this.errorListener = listener;
    }

    /**
     * Handle an error with retry logic
     *
     * @param type Error type
     * @param message Error message
     * @param context Operation context
     * @param retryable Whether the operation can be retried
     * @param retryAction The action to retry
     * @return true if recovered, false if unrecoverable
     */
    public boolean handleError(ErrorType type, String message, String context,
                                boolean retryable, Runnable retryAction) {
        return handleError(type, message, context, null, retryable, retryAction, DEFAULT_MAX_RETRIES);
    }

    /**
     * Handle an error with custom retry count
     */
    public boolean handleError(ErrorType type, String message, String context,
                                Exception exception, boolean retryable,
                                Runnable retryAction, int maxRetries) {

        // Determine severity
        Severity severity = determineSeverity(type, exception);

        // Log the error
        logError(type, severity, message, context, exception);

        // Update statistics
        totalErrors.incrementAndGet();
        if (severity == Severity.CRITICAL) {
            criticalErrors.incrementAndGet();
        }

        // Try recovery if possible
        if (retryable && retryAction != null && maxRetries > 0) {
            for (int retry = 1; retry <= maxRetries; retry++) {
                // Record retry attempt
                ErrorEvent event = new ErrorEvent(type, severity, message, context, exception, retry);
                addToHistory(event);
                notifyError(event);

                // Wait before retry
                try {
                    Thread.sleep(RETRY_DELAY_MS * retry);  // Exponential backoff
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }

                // Attempt retry
                try {
                    log.info("Retry attempt " + retry + "/" + maxRetries + " for: " + context);
                    retryAction.run();

                    // Success!
                    recoveredErrors.incrementAndGet();
                    log.info("Successfully recovered from error after " + retry + " attempts");
                    notifyRecovery(event);
                    return true;

                } catch (Exception e) {
                    if (retry == maxRetries) {
                        log.severe("All retry attempts failed for: " + context);
                        return false;
                    }
                    // Continue to next retry
                }
            }
        }

        // Record final error
        ErrorEvent event = new ErrorEvent(type, severity, message, context, exception, 0);
        addToHistory(event);
        notifyError(event);

        return false;
    }

    /**
     * Determine severity based on error type
     */
    private Severity determineSeverity(ErrorType type, Exception exception) {
        switch (type) {
            case CONNECTION_LOST:
            case INITIALIZATION_FAILED:
                return Severity.CRITICAL;

            case COMMUNICATION_ERROR:
            case PROTOCOL_ERROR:
            case CAN_ERROR:
                return Severity.ERROR;

            case TIMEOUT:
            case INVALID_RESPONSE:
            case SERVICE_NOT_SUPPORTED:
                return Severity.WARNING;

            case NO_DATA:
            default:
                return Severity.INFO;
        }
    }

    /**
     * Log error with appropriate level
     */
    private void logError(ErrorType type, Severity severity, String message,
                          String context, Exception exception) {
        String fullMessage = String.format("[%s] %s - %s: %s",
                severity, type.getDescription(), context, message);

        switch (severity) {
            case CRITICAL:
                if (exception != null) {
                    log.severe(fullMessage + " - " + exception.getMessage());
                } else {
                    log.severe(fullMessage);
                }
                break;

            case ERROR:
                log.warning(fullMessage);
                break;

            case WARNING:
                log.info(fullMessage);
                break;

            case INFO:
            default:
                log.fine(fullMessage);
                break;
        }
    }

    /**
     * Add error to history
     */
    private void addToHistory(ErrorEvent event) {
        errorHistory.offer(event);

        // Maintain bounded size
        while (errorHistory.size() > MAX_HISTORY_SIZE) {
            errorHistory.poll();
        }
    }

    /**
     * Notify error listener
     */
    private void notifyError(ErrorEvent event) {
        if (errorListener != null) {
            try {
                errorListener.onError(event);
            } catch (Exception e) {
                log.warning("Error listener threw exception: " + e.getMessage());
            }
        }
    }

    /**
     * Notify recovery
     */
    private void notifyRecovery(ErrorEvent event) {
        if (errorListener != null) {
            try {
                errorListener.onRecovery(event);
            } catch (Exception e) {
                log.warning("Recovery listener threw exception: " + e.getMessage());
            }
        }
    }

    /**
     * Get error statistics
     */
    public String getStatistics() {
        return String.format("Errors: Total=%d, Recovered=%d, Critical=%d, Recovery Rate=%.1f%%",
                totalErrors.get(),
                recoveredErrors.get(),
                criticalErrors.get(),
                totalErrors.get() > 0 ?
                        (100.0 * recoveredErrors.get() / totalErrors.get()) : 0);
    }

    /**
     * Get recent errors
     */
    public ErrorEvent[] getRecentErrors(int count) {
        return errorHistory.stream()
                .limit(count)
                .toArray(ErrorEvent[]::new);
    }

    /**
     * Clear error history
     */
    public void clearHistory() {
        errorHistory.clear();
        totalErrors.set(0);
        recoveredErrors.set(0);
        criticalErrors.set(0);
    }

    /**
     * Check if system is healthy
     */
    public boolean isHealthy() {
        // Consider unhealthy if too many critical errors
        return criticalErrors.get() < 5;
    }
}