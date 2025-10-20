package com.obddroid.services;

import android.os.SystemClock;

import com.obddroid.core.obd.RawTelegramListener;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Isolated fault code scanning service using Mode 03 (current codes) and Mode 07 (pending codes).
 *
 * DESIGN PRINCIPLE: This service is completely isolated from the rest of the app.
 * - Does NOT modify ObdProt.tCodes or existing fault code flow
 * - Only used by Fault Codes page (FaultCodesActivity)
 * - Can be disabled without affecting any other functionality
 *
 * HOW IT WORKS:
 * 1. Sends Mode 03 request (read current/confirmed fault codes)
 * 2. Sends Mode 07 request (read pending fault codes)
 * 3. Parses DTC responses (e.g., "43 02 01 33 00 00" → "P0133")
 * 4. Returns list of fault codes via CompletableFuture
 * 5. Can clear codes using Mode 04
 */
public class FaultCodeService implements RawTelegramListener {

    private static final Logger log = Logger.getLogger(FaultCodeService.class.getSimpleName());
    private static final String PROMPT = ">";
    private static final long SCAN_TIMEOUT_MS = 2_500L;
    private static final long CLEAR_TIMEOUT_MS = 1_500L;

    private enum ScanMode { CONFIRMED, PENDING }

    // Execution and state management
    private final ExecutorService scanExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "FaultCodeService");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean isScanning = new AtomicBoolean(false);
    private volatile CompletableFuture<List<FaultCodeInfo>> currentScan;

    // Response synchronization
    private final Object responseLock = new Object();
    private final StringBuilder responseBuffer = new StringBuilder();
    private final AtomicBoolean expectingResponse = new AtomicBoolean(false);
    private boolean waitingForPrompt;

    /**
     * Fault code information
     */
    public static class FaultCodeInfo {
        public final String code;
        public final String description;
        public final boolean isPending;
        public final boolean hasFreeze;
        public final int dtcNumber;

        public FaultCodeInfo(String code, String description, boolean isPending, boolean hasFreeze, int dtcNumber) {
            this.code = code;
            this.description = description;
            this.isPending = isPending;
            this.hasFreeze = hasFreeze;
            this.dtcNumber = dtcNumber;
        }
    }

    /**
     * Scan for confirmed (Mode 03) fault codes.
     */
    public CompletableFuture<List<FaultCodeInfo>> scanFaultCodes() {
        return startScan(EnumSet.of(ScanMode.CONFIRMED));
    }

    /**
     * Scan for pending (Mode 07) fault codes.
     */
    public CompletableFuture<List<FaultCodeInfo>> scanPendingCodes() {
        return startScan(EnumSet.of(ScanMode.PENDING));
    }

    /**
     * Scan for both confirmed and pending fault codes in a single request chain.
     */
    public CompletableFuture<List<FaultCodeInfo>> scanAllCodes() {
        return startScan(EnumSet.of(ScanMode.CONFIRMED, ScanMode.PENDING));
    }

    /**
     * Clear all fault codes using Mode 04.
     *
     * @return CompletableFuture that completes with true if successful
     */
    public CompletableFuture<Boolean> clearFaultCodes() {
        if (CommService.elm == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("ELM not available"));
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Clearing fault codes (Mode 04)");
                CommService.elm.addRawTelegramListener(this);
                try {
                    String response = sendAndAwait("04", CLEAR_TIMEOUT_MS);
                    log.fine(() -> "Clear response: " + response);
                    return response.toUpperCase(Locale.US).contains("OK")
                        || response.toUpperCase(Locale.US).contains("NODATA");
                } finally {
                    CommService.elm.removeRawTelegramListener(this);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CompletionException(e);
            } catch (TimeoutException e) {
                throw new CompletionException(e);
            }
        }, scanExecutor);
    }

    /**
     * Start scanning based on the requested modes.
     */
    private CompletableFuture<List<FaultCodeInfo>> startScan(EnumSet<ScanMode> modes) {
        if (CommService.elm == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("ELM not available"));
        }

        if (!isScanning.compareAndSet(false, true)) {
            log.warning("Scan already in progress – returning existing future");
            return currentScan != null
                ? currentScan
                : CompletableFuture.failedFuture(new IllegalStateException("Scan already in progress"));
        }

        CompletableFuture<List<FaultCodeInfo>> future = CompletableFuture.supplyAsync(() -> {
            try {
                return executeScan(modes);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CompletionException(e);
            } catch (TimeoutException e) {
                throw new CompletionException(e);
            }
        }, scanExecutor);

        currentScan = future;
        future.whenComplete((result, throwable) -> {
            isScanning.set(false);
            expectingResponse.set(false);
            synchronized (responseLock) {
                waitingForPrompt = false;
                responseBuffer.setLength(0);
                responseLock.notifyAll();
            }
        });

        return future;
    }

    /**
     * Execute the actual scan sequence synchronously on the scan executor thread.
     */
    private List<FaultCodeInfo> executeScan(EnumSet<ScanMode> modes) throws InterruptedException, TimeoutException {
        log.info(() -> "Starting fault code scan for modes: " + modes);

        List<FaultCodeInfo> results = new ArrayList<>();
        CommService.elm.addRawTelegramListener(this);
        try {
            if (modes.contains(ScanMode.CONFIRMED)) {
                String response = sendAndAwait("03", SCAN_TIMEOUT_MS);
                log.fine(() -> "Mode 03 response: " + response);
                results.addAll(parseFaultCodes(response, false));
            }
            if (modes.contains(ScanMode.PENDING)) {
                String response = sendAndAwait("07", SCAN_TIMEOUT_MS);
                log.fine(() -> "Mode 07 response: " + response);
                results.addAll(parseFaultCodes(response, true));
            }
        } finally {
            CommService.elm.removeRawTelegramListener(this);
        }

        // Sort confirmed codes first, then alphabetically.
        results.sort(Comparator
            .comparing((FaultCodeInfo code) -> code.isPending)
            .thenComparing(code -> code.code));

        log.info(() -> "Scan complete. Found " + results.size() + " codes.");
        return results;
    }

    /**
     * Send a raw command and wait for the trailing prompt.
     */
    private String sendAndAwait(String command, long timeoutMs) throws InterruptedException, TimeoutException {
        synchronized (responseLock) {
            responseBuffer.setLength(0);
            waitingForPrompt = true;
            expectingResponse.set(true);
        }

        sendRawCommand(command);

        long deadline = SystemClock.uptimeMillis() + timeoutMs;
        synchronized (responseLock) {
            while (waitingForPrompt) {
                long remaining = deadline - SystemClock.uptimeMillis();
                if (remaining <= 0) {
                    waitingForPrompt = false;
                    expectingResponse.set(false);
                    throw new TimeoutException("Timed out waiting for response to command " + command);
                }
                responseLock.wait(remaining);
            }
            expectingResponse.set(false);
            return responseBuffer.toString().trim();
        }
    }

    /**
     * Send raw command to ELM adapter.
     */
    private void sendRawCommand(String command) {
        if (CommService.elm != null) {
            log.fine(() -> "TX: " + command);
            CommService.elm.sendTelegram(command.toCharArray());
        }
    }

    /**
     * Handle incoming RAW telegram responses from ELM adapter.
     */
    @Override
    public int handleRawTelegram(char[] buffer) {
        String response = new String(buffer).trim();
        if (response.isEmpty()) {
            return buffer.length;
        }

        log.finest(() -> "RX: " + response);

        if (!expectingResponse.get()) {
            return buffer.length;
        }

        if (response.startsWith("AT") || response.startsWith("SEARCHING") || response.startsWith("OK")) {
            return buffer.length;
        }

        synchronized (responseLock) {
            if (PROMPT.equals(response)) {
                waitingForPrompt = false;
                responseLock.notifyAll();
            } else {
                responseBuffer.append(response).append(' ');
            }
        }

        return buffer.length;
    }

    /**
     * Parse fault codes from a response string.
     */
    private List<FaultCodeInfo> parseFaultCodes(String response, boolean isPending) {
        List<FaultCodeInfo> codes = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        if (response == null || response.isEmpty()) {
            return codes;
        }

        String cleanData = response.replaceAll("\\s+", "").toUpperCase(Locale.US);
        if (cleanData.contains("NODATA") || cleanData.length() < 4) {
            return codes;
        }

        String modePrefix = isPending ? "47" : "43";
        int cursor = cleanData.indexOf(modePrefix);
        while (cursor >= 0 && cursor + 4 <= cleanData.length()) {
            int count;
            try {
                count = Integer.parseInt(cleanData.substring(cursor + 2, cursor + 4), 16);
            } catch (NumberFormatException ex) {
                log.log(Level.WARNING, "Invalid DTC count in response segment: " + cleanData, ex);
                break;
            }

            int index = cursor + 4;
            for (int i = 0; i < count && index + 4 <= cleanData.length(); i++) {
                String dtcHex = cleanData.substring(index, index + 4);
                index += 4;

                try {
                    int dtcValue = Integer.parseInt(dtcHex, 16);
                    if (dtcValue == 0 || !seen.add(dtcValue)) {
                        continue;
                    }
                    String dtcCode = convertToDtcCode(dtcValue);
                    String description = getDtcDescription(dtcCode);
                    boolean hasFreeze = !isPending;
                    codes.add(new FaultCodeInfo(dtcCode, description, isPending, hasFreeze, dtcValue));
                } catch (NumberFormatException ex) {
                    log.log(Level.WARNING, "Failed to parse DTC value: " + dtcHex, ex);
                }
            }

            cursor = cleanData.indexOf(modePrefix, index);
        }

        return codes;
    }

    /**
     * Convert DTC value to standard code format.
     */
    private String convertToDtcCode(int dtcValue) {
        int prefix = (dtcValue >> 14) & 0x03;
        char prefixChar;
        switch (prefix) {
            case 0: prefixChar = 'P'; break;
            case 1: prefixChar = 'C'; break;
            case 2: prefixChar = 'B'; break;
            case 3: prefixChar = 'U'; break;
            default: prefixChar = 'P'; break;
        }

        int codeValue = dtcValue & 0x3FFF;
        return String.format(Locale.US, "%c%04X", prefixChar, codeValue);
    }

    /**
     * Get human-readable description for DTC code.
     * TODO: Replace with lookup from bundled DTC database.
     */
    private String getDtcDescription(String code) {
        switch (code) {
            case "P0133": return "O2 Sensor Circuit Slow Response (Bank 1, Sensor 1)";
            case "P0301": return "Cylinder 1 Misfire Detected";
            case "P0302": return "Cylinder 2 Misfire Detected";
            case "P0303": return "Cylinder 3 Misfire Detected";
            case "P0304": return "Cylinder 4 Misfire Detected";
            case "P0420": return "Catalyst System Efficiency Below Threshold (Bank 1)";
            case "P0171": return "System Too Lean (Bank 1)";
            case "P0172": return "System Too Rich (Bank 1)";
            default: return "Unknown fault code";
        }
    }

    /**
     * Check if scan is currently in progress.
     */
    public boolean isScanning() {
        return isScanning.get();
    }
}
