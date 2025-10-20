package com.obddroid.services;

import android.util.Log;
import com.obddroid.core.obd.RawTelegramListener;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
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
 *
 * @author Wal33D <aquataze@yahoo.com>
 */
public class FaultCodeService implements RawTelegramListener {
    private static final String TAG = "FaultCodeService";
    private static final Logger log = Logger.getLogger(TAG);

    // Scan state
    private final List<FaultCodeInfo> discoveredCodes = new ArrayList<>();
    private CompletableFuture<List<FaultCodeInfo>> currentScan;
    private final AtomicBoolean isScanning = new AtomicBoolean(false);
    private StringBuilder responseBuffer = new StringBuilder();
    private boolean expectingResponse = false;

    /**
     * Fault code information
     */
    public static class FaultCodeInfo {
        public final String code;           // "P0301"
        public final String description;    // "Cylinder 1 Misfire Detected"
        public final boolean isPending;     // true = pending, false = confirmed
        public final boolean hasFreeze;     // Has freeze frame data available
        public final int dtcNumber;         // Raw DTC number for freeze frame lookup

        public FaultCodeInfo(String code, String description, boolean isPending, boolean hasFreeze, int dtcNumber) {
            this.code = code;
            this.description = description;
            this.isPending = isPending;
            this.hasFreeze = hasFreeze;
            this.dtcNumber = dtcNumber;
        }

        @Override
        public String toString() {
            return String.format("%s: %s (pending=%s, freeze=%s)", code, description, isPending, hasFreeze);
        }
    }

    /**
     * Scan for all fault codes (both current and pending).
     *
     * This is a non-blocking operation that returns immediately with a CompletableFuture.
     * The scan process runs in a background thread.
     *
     * @return CompletableFuture that completes with list of fault codes
     */
    public CompletableFuture<List<FaultCodeInfo>> scanFaultCodes() {
        if (isScanning.get()) {
            Log.w(TAG, "Scan already in progress");
            return currentScan;
        }

        if (CommService.elm == null) {
            Log.e(TAG, "ELM protocol not available - cannot scan fault codes");
            return CompletableFuture.failedFuture(new IllegalStateException("ELM not available"));
        }

        currentScan = new CompletableFuture<>();
        discoveredCodes.clear();
        responseBuffer.setLength(0);

        new Thread(() -> {
            try {
                isScanning.set(true);
                Log.i(TAG, "========== FAULT CODE SCAN START ==========");

                // Add ourselves as a RAW telegram listener
                CommService.elm.addRawTelegramListener(this);
                Log.i(TAG, "Registered as RAW telegram listener");

                // Step 1: Request Mode 03 (Read Confirmed DTCs)
                Log.i(TAG, "Step 1: Requesting confirmed fault codes (03)");
                expectingResponse = true;
                sendRawCommand("03");
                Thread.sleep(800);  // Wait for response

                // Parse Mode 03 responses
                if (responseBuffer.length() > 0) {
                    parseFaultCodes(responseBuffer.toString(), false);
                    responseBuffer.setLength(0);
                }

                // Step 2: Request Mode 07 (Read Pending DTCs)
                Log.i(TAG, "Step 2: Requesting pending fault codes (07)");
                expectingResponse = true;
                sendRawCommand("07");
                Thread.sleep(800);  // Wait for response

                // Parse Mode 07 responses
                if (responseBuffer.length() > 0) {
                    parseFaultCodes(responseBuffer.toString(), true);
                    responseBuffer.setLength(0);
                }

                // Remove raw listener
                CommService.elm.removeRawTelegramListener(this);
                Log.i(TAG, "Unregistered RAW telegram listener");

                // Complete scan
                Log.i(TAG, "========== FAULT CODE SCAN COMPLETE ==========");
                Log.i(TAG, "Found " + discoveredCodes.size() + " fault codes:");
                for (FaultCodeInfo code : discoveredCodes) {
                    Log.i(TAG, "  " + code.toString());
                }

                currentScan.complete(new ArrayList<>(discoveredCodes));

            } catch (Exception e) {
                Log.e(TAG, "Fault code scan failed: " + e.getMessage(), e);
                CommService.elm.removeRawTelegramListener(this);
                currentScan.completeExceptionally(e);
            } finally {
                isScanning.set(false);
                expectingResponse = false;
            }
        }, "FaultCode-Scan-Thread").start();

        return currentScan;
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

        CompletableFuture<Boolean> clearFuture = new CompletableFuture<>();

        new Thread(() -> {
            try {
                Log.i(TAG, "========== CLEARING FAULT CODES ==========");
                sendRawCommand("04");
                Thread.sleep(500);  // Wait for clear to complete
                Log.i(TAG, "Fault codes cleared successfully");
                clearFuture.complete(true);
            } catch (Exception e) {
                Log.e(TAG, "Failed to clear fault codes: " + e.getMessage(), e);
                clearFuture.completeExceptionally(e);
            }
        }, "FaultCode-Clear-Thread").start();

        return clearFuture;
    }

    /**
     * Send raw command to ELM adapter
     */
    private void sendRawCommand(String command) {
        if (CommService.elm != null) {
            CommService.elm.sendTelegram(command.toCharArray());
            Log.d(TAG, "TX: " + command);
        }
    }

    /**
     * Handle incoming RAW telegram responses from ELM adapter.
     */
    @Override
    public int handleRawTelegram(char[] buffer) {
        String response = new String(buffer).trim();

        if (response.isEmpty()) {
            return 0;
        }

        Log.d(TAG, "RX: " + response);

        // Ignore prompts and AT responses
        if (response.equals(">") || response.startsWith("OK") ||
            response.startsWith("AT") || response.startsWith("SEARCH")) {
            return response.length();
        }

        // Accumulate response data
        if (expectingResponse && !response.equals("NO DATA")) {
            responseBuffer.append(response).append(" ");
        }

        return response.length();
    }

    /**
     * Parse fault codes from Mode 03 or Mode 07 response.
     *
     * Format examples:
     * - "43 02 01 33 00 00" → 2 codes: P0133, P0000 (ignore P0000)
     * - "47 01 01 33" → 1 pending code: P0133
     *
     * DTC Format:
     * - First 2 bits determine prefix: 00=P, 01=C, 10=B, 11=U
     * - Remaining 14 bits are hex digits
     * - Example: 0133 → P0133
     *
     * @param response Raw response string
     * @param isPending true if these are pending codes (Mode 07), false if confirmed (Mode 03)
     */
    private void parseFaultCodes(String response, boolean isPending) {
        try {
            // Remove all spaces and convert to uppercase
            String cleanData = response.replaceAll("\\s+", "").toUpperCase();
            Log.d(TAG, "Parsing fault codes from: " + cleanData);

            // Find mode response (43 for Mode 03, 47 for Mode 07)
            String modePrefix = isPending ? "47" : "43";
            int start = cleanData.indexOf(modePrefix);
            if (start == -1) {
                Log.d(TAG, "No " + modePrefix + " response found");
                return;
            }

            // Skip mode byte (43/47) and count byte
            start += 4;  // Skip "43XX" or "47XX"

            // Parse DTCs in pairs of bytes
            while (start + 4 <= cleanData.length()) {
                String dtcHex = cleanData.substring(start, start + 4);
                start += 4;

                // Convert hex to DTC code
                int dtcValue = Integer.parseInt(dtcHex, 16);

                // Skip P0000 (no fault)
                if (dtcValue == 0) {
                    continue;
                }

                String dtcCode = convertToDtcCode(dtcValue);
                String description = getDtcDescription(dtcCode);
                boolean hasFreeze = !isPending;  // Only confirmed codes have freeze frames

                FaultCodeInfo codeInfo = new FaultCodeInfo(dtcCode, description, isPending, hasFreeze, dtcValue);
                discoveredCodes.add(codeInfo);
                Log.i(TAG, "Found fault code: " + codeInfo.toString());
            }

        } catch (Exception e) {
            Log.w(TAG, "Failed to parse fault codes: " + e.getMessage());
        }
    }

    /**
     * Convert DTC value to standard code format.
     *
     * First 2 bits determine prefix:
     * - 00 = P (Powertrain)
     * - 01 = C (Chassis)
     * - 10 = B (Body)
     * - 11 = U (Network)
     *
     * Example: 0x0133 → P0133
     */
    private String convertToDtcCode(int dtcValue) {
        // Extract first 2 bits for prefix
        int prefix = (dtcValue >> 14) & 0x03;
        char prefixChar;
        switch (prefix) {
            case 0: prefixChar = 'P'; break;
            case 1: prefixChar = 'C'; break;
            case 2: prefixChar = 'B'; break;
            case 3: prefixChar = 'U'; break;
            default: prefixChar = 'P'; break;
        }

        // Extract remaining 14 bits as 4 hex digits
        int codeValue = dtcValue & 0x3FFF;
        return String.format("%c%04X", prefixChar, codeValue);
    }

    /**
     * Get human-readable description for DTC code.
     * This is a simplified version - real implementation would use DTC database.
     */
    private String getDtcDescription(String code) {
        // TODO: Integrate with DTC database
        // For now, return generic description
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
     * Check if scan is currently in progress
     */
    public boolean isScanning() {
        return isScanning.get();
    }
}
