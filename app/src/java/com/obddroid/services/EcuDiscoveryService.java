package com.obddroid.services;

import android.util.Log;
import com.obddroid.core.obd.TelegramListener;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Isolated ECU discovery service using Mode 9 with headers enabled.
 *
 * DESIGN PRINCIPLE: This service is completely isolated from the rest of the app.
 * - Does NOT modify VidPvs or existing Mode 9 flow
 * - Only used by ECU Modules page (EcuListActivity)
 * - Can be disabled without affecting any other functionality
 *
 * HOW IT WORKS:
 * 1. Temporarily enables CAN headers (ATH1)
 * 2. Sends Mode 9 requests (0904, 090A for calibration ID and ECU name)
 * 3. Parses responses to extract ECU addresses from headers
 * 4. Builds map of ECU address → ECU info
 * 5. Restores headers to disabled state (ATH0)
 * 6. Returns results via CompletableFuture
 *
 * @author Wal33D <aquataze@yahoo.com>
 */
public class EcuDiscoveryService implements TelegramListener {
    private static final String TAG = "EcuDiscoveryService";
    private static final Logger log = Logger.getLogger(TAG);

    // Discovery state
    private final Map<Integer, EcuDiscoveryInfo> discoveredEcus = new HashMap<>();
    private CompletableFuture<Map<Integer, EcuDiscoveryInfo>> currentDiscovery;
    private final AtomicBoolean isDiscovering = new AtomicBoolean(false);
    private String multilineBuffer = "";
    private int lastEcuAddress = -1;

    /**
     * ECU information discovered via Mode 9
     */
    public static class EcuDiscoveryInfo {
        public final int address;
        public String name;
        public String calibrationId;
        public String cvn;

        public EcuDiscoveryInfo(int address) {
            this.address = address;
        }

        @Override
        public String toString() {
            return String.format("ECU 0x%X: name=%s, calId=%s", address, name, calibrationId);
        }
    }

    /**
     * Discover all ECUs on the CAN bus using Mode 9 with headers enabled.
     *
     * This is a non-blocking operation that returns immediately with a CompletableFuture.
     * The discovery process runs in a background thread.
     *
     * @return CompletableFuture that completes with map of ECU address → info
     */
    public CompletableFuture<Map<Integer, EcuDiscoveryInfo>> discoverEcus() {
        if (isDiscovering.get()) {
            Log.w(TAG, "Discovery already in progress");
            return currentDiscovery;
        }

        if (CommService.elm == null) {
            Log.e(TAG, "ELM protocol not available - cannot discover ECUs");
            return CompletableFuture.failedFuture(new IllegalStateException("ELM not available"));
        }

        currentDiscovery = new CompletableFuture<>();
        discoveredEcus.clear();
        multilineBuffer = "";
        lastEcuAddress = -1;

        new Thread(() -> {
            try {
                isDiscovering.set(true);
                Log.i(TAG, "========== ECU DISCOVERY START ==========");

                // Add ourselves as a telegram listener to receive responses
                CommService.elm.addTelegramListener(this);

                // Step 1: Enable headers to capture ECU addresses
                Log.i(TAG, "Step 1: Enabling headers (ATH1)");
                sendRawCommand("ATH1");
                Thread.sleep(150);  // Wait for header enable

                // Step 2: Request Mode 9 PID 04 (Calibration ID)
                Log.i(TAG, "Step 2: Requesting Calibration ID (0904)");
                sendRawCommand("0904");
                Thread.sleep(500);  // Wait for all ECU responses

                // Step 3: Request Mode 9 PID 0A (ECU Name)
                Log.i(TAG, "Step 3: Requesting ECU Name (090A)");
                sendRawCommand("090A");
                Thread.sleep(500);  // Wait for all ECU responses

                // Step 4: Disable headers to restore normal operation
                Log.i(TAG, "Step 4: Disabling headers (ATH0)");
                sendRawCommand("ATH0");
                Thread.sleep(150);

                // Remove listener
                CommService.elm.removeTelegramListener(this);

                // Step 5: Complete discovery
                Log.i(TAG, "========== ECU DISCOVERY COMPLETE ==========");
                Log.i(TAG, "Discovered " + discoveredEcus.size() + " ECUs:");
                for (EcuDiscoveryInfo info : discoveredEcus.values()) {
                    Log.i(TAG, "  " + info.toString());
                }

                currentDiscovery.complete(new HashMap<>(discoveredEcus));

            } catch (Exception e) {
                Log.e(TAG, "ECU discovery failed: " + e.getMessage(), e);
                CommService.elm.removeTelegramListener(this);
                currentDiscovery.completeExceptionally(e);
            } finally {
                isDiscovering.set(false);
            }
        }, "ECU-Discovery-Thread").start();

        return currentDiscovery;
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
     * Handle incoming telegram responses from ELM adapter.
     * This is called by the ELM protocol layer for every response.
     */
    @Override
    public int handleTelegram(char[] buffer) {
        String response = new String(buffer).trim();

        if (response.isEmpty()) {
            return 0;
        }

        Log.d(TAG, "RX: " + response);

        // Ignore AT command responses (OK, prompt, etc.)
        if (response.startsWith("OK") || response.equals(">") ||
            response.startsWith("ATH") || response.startsWith("SEARCH")) {
            return response.length();
        }

        // Parse ECU address from header (if present)
        int ecuAddress = parseEcuAddress(response);

        // Handle multiline responses
        if (isMultilineStart(response)) {
            multilineBuffer = response;
            lastEcuAddress = ecuAddress;
            return response.length();
        } else if (isMultilineContinuation(response) && !multilineBuffer.isEmpty()) {
            multilineBuffer += " " + response;
            return response.length();
        } else if (isMultilineEnd(response) && !multilineBuffer.isEmpty()) {
            multilineBuffer += " " + response;
            response = multilineBuffer;
            ecuAddress = lastEcuAddress;
            multilineBuffer = "";
            lastEcuAddress = -1;
        }

        // Parse Mode 9 data if we have an ECU address
        if (ecuAddress != -1) {
            parseMode9Response(ecuAddress, response);
        }

        return response.length();
    }

    /**
     * Parse ECU address from response with header.
     *
     * Expected formats:
     * - "7E8 03 49 04 01..." (with spaces)
     * - "7E803490401..." (no spaces)
     * - "18DAF110 03 49 04..." (29-bit CAN)
     *
     * @return ECU address (0x7E8, 0x7E9, etc.) or -1 if no header found
     */
    private int parseEcuAddress(String response) {
        try {
            // Remove any leading/trailing whitespace
            response = response.trim();

            // Format with space: "7E8 03 49..."
            if (response.length() >= 4 && response.charAt(3) == ' ') {
                String addrHex = response.substring(0, 3);
                return Integer.parseInt(addrHex, 16);
            }
            // Format without space: "7E803490A..." - check if first 3 chars look like CAN address
            else if (response.length() >= 3) {
                String potentialAddr = response.substring(0, 3);
                int addr = Integer.parseInt(potentialAddr, 16);
                // Valid OBD ECU addresses are typically 0x7E0-0x7EF or 0x7F0-0x7FF
                if ((addr >= 0x7E0 && addr <= 0x7FF) || (addr >= 0x18D && addr <= 0x18F)) {
                    return addr;
                }
            }
            // 29-bit CAN format: "18DAF110 03 49..."
            else if (response.length() >= 9 && response.charAt(8) == ' ') {
                // For 29-bit, we'll use the functional address portion
                String addrHex = response.substring(0, 8);
                return Integer.parseInt(addrHex, 16);
            }
        } catch (NumberFormatException e) {
            Log.d(TAG, "Could not parse ECU address from: " + response);
        }

        return -1;  // No header found
    }

    /**
     * Check if this is the start of a multiline response
     */
    private boolean isMultilineStart(String response) {
        return response.contains("0:") || response.contains("1:");
    }

    /**
     * Check if this is a continuation line of a multiline response
     */
    private boolean isMultilineContinuation(String response) {
        return response.matches(".*[2-9][:].+");
    }

    /**
     * Check if this is the end of a multiline response
     */
    private boolean isMultilineEnd(String response) {
        // End is typically when we see a line without a line number, or line 0 again
        return !response.contains(":") || response.startsWith("0:");
    }

    /**
     * Parse Mode 9 response to extract data
     */
    private void parseMode9Response(int ecuAddress, String response) {
        // Remove ECU address header from response
        String data = removeHeader(response);

        // Check if this is a Mode 9 response (starts with 49)
        if (!data.startsWith("49") && !data.contains("49")) {
            return;
        }

        // Get or create ECU info
        EcuDiscoveryInfo info = getOrCreate(ecuAddress);

        // Parse based on PID
        if (data.contains("49 04") || data.contains("4904")) {
            // Calibration ID (PID 04)
            String calId = parseCalibrationId(data);
            if (calId != null && !calId.isEmpty()) {
                info.calibrationId = calId;
                Log.i(TAG, String.format("ECU 0x%X Cal ID: %s", ecuAddress, calId));
            }
        } else if (data.contains("49 0A") || data.contains("490A")) {
            // ECU Name (PID 0A)
            String name = parseEcuName(data);
            if (name != null && !name.isEmpty()) {
                info.name = name;
                Log.i(TAG, String.format("ECU 0x%X Name: %s", ecuAddress, name));
            }
        } else if (data.contains("49 06") || data.contains("4906")) {
            // CVN (PID 06)
            String cvn = parseCvn(data);
            if (cvn != null && !cvn.isEmpty()) {
                info.cvn = cvn;
                Log.i(TAG, String.format("ECU 0x%X CVN: %s", ecuAddress, cvn));
            }
        }
    }

    /**
     * Remove ECU address header from response
     */
    private String removeHeader(String response) {
        // Remove "7E8 03 " or "7E803" prefix
        if (response.length() >= 4 && response.charAt(3) == ' ') {
            // Format: "7E8 03 49 04..." → "49 04..."
            int dataStart = response.indexOf(' ', 4) + 1;
            return response.substring(dataStart);
        } else if (response.length() >= 6) {
            // Format: "7E803490401..." → "490401..."
            return response.substring(5);
        }
        return response;
    }

    /**
     * Parse Calibration ID from Mode 9 PID 04 response
     * Format: "49 04 01 XXXX..." where XXXX is ASCII hex
     */
    private String parseCalibrationId(String data) {
        try {
            // Remove spaces
            data = data.replaceAll("\\s+", "");

            // Find "4904" and extract data after it
            int start = data.indexOf("4904");
            if (start == -1) return null;

            // Skip "4904" + message count byte (01)
            start += 6;
            if (start >= data.length()) return null;

            // Extract hex data and convert to ASCII
            String hexData = data.substring(start);
            return hexToAscii(hexData);

        } catch (Exception e) {
            Log.w(TAG, "Failed to parse calibration ID: " + e.getMessage());
            return null;
        }
    }

    /**
     * Parse ECU Name from Mode 9 PID 0A response
     * Format: "49 0A 01 XXXX..." where XXXX is ASCII hex
     */
    private String parseEcuName(String data) {
        try {
            // Remove spaces
            data = data.replaceAll("\\s+", "");

            // Find "490A" and extract data after it
            int start = data.indexOf("490A");
            if (start == -1) return null;

            // Skip "490A" + message count byte (01)
            start += 6;
            if (start >= data.length()) return null;

            // Extract hex data and convert to ASCII
            String hexData = data.substring(start);
            return hexToAscii(hexData);

        } catch (Exception e) {
            Log.w(TAG, "Failed to parse ECU name: " + e.getMessage());
            return null;
        }
    }

    /**
     * Parse CVN from Mode 9 PID 06 response
     */
    private String parseCvn(String data) {
        try {
            // Remove spaces
            data = data.replaceAll("\\s+", "");

            // Find "4906" and extract data after it
            int start = data.indexOf("4906");
            if (start == -1) return null;

            // Skip "4906" + message count byte (01)
            start += 6;
            if (start >= data.length()) return null;

            // CVN is typically 4 bytes (8 hex chars)
            String hexData = data.substring(start, Math.min(start + 8, data.length()));
            return hexData.toUpperCase();

        } catch (Exception e) {
            Log.w(TAG, "Failed to parse CVN: " + e.getMessage());
            return null;
        }
    }

    /**
     * Convert hex string to ASCII, filtering out non-printable characters
     */
    private String hexToAscii(String hex) {
        StringBuilder ascii = new StringBuilder();

        // Process pairs of hex digits
        for (int i = 0; i < hex.length() - 1; i += 2) {
            String hexByte = hex.substring(i, i + 2);
            try {
                int charCode = Integer.parseInt(hexByte, 16);
                // Only include printable ASCII (0x20-0x7E), skip 0x00 (null terminator)
                if (charCode >= 0x20 && charCode <= 0x7E) {
                    ascii.append((char) charCode);
                } else if (charCode == 0x00) {
                    break;  // Stop at null terminator
                }
            } catch (NumberFormatException e) {
                // Skip invalid hex
            }
        }

        return ascii.toString().trim();
    }

    /**
     * Get or create EcuDiscoveryInfo for given address
     */
    private EcuDiscoveryInfo getOrCreate(int ecuAddress) {
        return discoveredEcus.computeIfAbsent(ecuAddress, EcuDiscoveryInfo::new);
    }

    /**
     * Check if discovery is currently in progress
     */
    public boolean isDiscovering() {
        return isDiscovering.get();
    }
}
