package com.obddroid.vehicle;

import androidx.annotation.NonNull;

/**
 * Represents an Electronic Control Unit (ECU) in the vehicle.
 * Contains identification, calibration, and diagnostic information.
 */
public class EcuInfo {
    private final int address;
    private String name;
    private String calibrationId;
    private String calibrationVerification;
    private int responseCount;

    /**
     * Create ECU info with address
     * @param address ECU address (e.g., 0x7E8 for engine)
     */
    public EcuInfo(int address) {
        this.address = address;
        this.name = null;
        this.calibrationId = null;
        this.calibrationVerification = null;
        this.responseCount = 0;
    }

    public int getAddress() {
        return address;
    }

    public String getAddressHex() {
        return String.format("0x%02X", address);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCalibrationId() {
        return calibrationId;
    }

    public void setCalibrationId(String calibrationId) {
        this.calibrationId = calibrationId;
    }

    public String getCalibrationVerification() {
        return calibrationVerification;
    }

    public void setCalibrationVerification(String calibrationVerification) {
        this.calibrationVerification = calibrationVerification;
    }

    public int getResponseCount() {
        return responseCount;
    }

    public void incrementResponseCount() {
        this.responseCount++;
    }

    /**
     * Get display name for this ECU (cleaned up and user-friendly)
     */
    public String getDisplayName() {
        if (name != null && !name.trim().isEmpty()) {
            String cleaned = name.trim();

            // Replace common acronyms with full names
            cleaned = cleaned
                .replaceAll("(?i)ECM", "Engine Control Module")
                .replaceAll("(?i)TCM", "Transmission Control Module")
                .replaceAll("(?i)ABS", "Anti-lock Braking System")
                .replaceAll("(?i)SRS", "Airbag Control Module")
                .replaceAll("(?i)BCM", "Body Control Module")
                .replaceAll("(?i)PCM", "Powertrain Control Module")
                .replaceAll("(?i)FPC", "Fuel Pump Control");

            // Remove redundant text (e.g., "Transmission Control Module TransmisCtrl")
            // Keep only the first full description
            String[] parts = cleaned.split("\\s+");
            StringBuilder result = new StringBuilder();
            boolean foundFullName = false;

            for (String part : parts) {
                // If we find "Module" or "System", we've found the full name
                if (part.equalsIgnoreCase("Module") || part.equalsIgnoreCase("System")) {
                    result.append(part);
                    foundFullName = true;
                    break;
                }
                result.append(part).append(" ");
            }

            cleaned = result.toString().trim();

            // Ensure at least some text remains
            if (cleaned.isEmpty()) {
                cleaned = name.trim();
            }

            return cleaned;
        }
        // Fallback to generic name based on address
        return "ECU " + getAddressHex();
    }

    /**
     * Get ECU type/category based on address or name
     */
    public String getEcuType() {
        String addr = getAddressHex();
        String n = (name != null) ? name.toUpperCase() : "";

        // Priority 1: Check name for specific keywords (more reliable than address)
        if (n.contains("TCM") || n.contains("TRANSMISSION")) {
            return "Transmission";
        } else if (n.contains("ECM") || n.contains("ENGINE")) {
            return "Engine";
        } else if (n.contains("ABS") || n.contains("BRAKE")) {
            return "ABS/Brakes";
        } else if (n.contains("SRS") || n.contains("AIRBAG")) {
            return "Airbag";
        } else if (n.contains("BCM") || n.contains("BODY")) {
            return "Body Control";
        } else if (n.contains("PCM") || n.contains("POWERTRAIN")) {
            return "Powertrain";
        } else if (n.contains("FPC") || n.contains("FUEL")) {
            return "Fuel System";
        }

        // Priority 2: Fallback to common ECU address ranges (OBD-II standard)
        if (addr.equals("0x7E8")) {
            return "Engine";
        } else if (addr.equals("0x7E9")) {
            return "Transmission";
        } else if (addr.equals("0x7EA")) {
            return "ABS/Brakes";
        } else if (addr.equals("0x7EB")) {
            return "Airbag";
        }

        return "Control Module";
    }

    /**
     * Check if this ECU has meaningful data
     */
    public boolean hasData() {
        return name != null || calibrationId != null || calibrationVerification != null;
    }

    @NonNull
    @Override
    public String toString() {
        return String.format("ECU[addr=%s, name=%s, cal=%s, responses=%d]",
            getAddressHex(), name, calibrationId, responseCount);
    }
}
