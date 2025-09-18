package com.obddroid.ecu.prot.obd;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.logging.Logger;

/**
 * Demo Mode Data Provider
 * Manages realistic demo data for testing and demonstration purposes
 *
 * @author Wal33D
 */
public class DemoDataProvider {
    private static final Logger log = Logger.getLogger(DemoDataProvider.class.getName());

    // Singleton instance
    private static DemoDataProvider instance;

    // Demo fault codes - realistic common codes
    private static final String[][] DEFAULT_FAULT_CODES = {
        {"0171", "System Too Lean (Bank 1)"},           // P0171
        {"0301", "Cylinder 1 Misfire Detected"},        // P0301
        {"0420", "Catalyst Efficiency Below Threshold"}, // P0420
        {"0442", "EVAP System Leak Detected (Small)"},  // P0442
        {"0128", "Coolant Temp Below Thermostat"},      // P0128
        {"0101", "MAF Sensor Range/Performance"}        // P0101
    };

    // Current active fault codes
    private List<FaultCode> activeFaultCodes;
    private boolean faultCodesCleared = false;
    private boolean milOn = true;
    private int numCodes = 5;  // Default to 5 codes

    // Demo PIDs with realistic values
    private int engineRPM = 800;
    private int vehicleSpeed = 0;
    private int coolantTemp = 75;
    private int intakeTemp = 25;
    private float fuelLevel = 65.0f;
    private int throttlePosition = 15;

    // Random for realistic variations
    private Random random = new Random();

    // Fault code structure
    public static class FaultCode {
        public final String code;
        public final String description;
        public final boolean pending;

        public FaultCode(String code, String description, boolean pending) {
            this.code = code;
            this.description = description;
            this.pending = pending;
        }

        public String toHexString() {
            // Convert Pxxxx code to hex format
            // P0171 -> 0171 hex
            return code;
        }
    }

    private DemoDataProvider() {
        reset();
    }

    /**
     * Get singleton instance
     */
    public static synchronized DemoDataProvider getInstance() {
        if (instance == null) {
            instance = new DemoDataProvider();
        }
        return instance;
    }

    /**
     * Reset demo data to initial state
     */
    public void reset() {
        log.info("DEMO: Resetting demo data provider");

        // Reset fault codes
        activeFaultCodes = new ArrayList<>();
        faultCodesCleared = false;
        milOn = true;

        // Add default fault codes (5 codes)
        for (int i = 0; i < Math.min(5, DEFAULT_FAULT_CODES.length); i++) {
            activeFaultCodes.add(new FaultCode(
                DEFAULT_FAULT_CODES[i][0],
                DEFAULT_FAULT_CODES[i][1],
                false  // Not pending
            ));
        }

        // Reset PID values to defaults
        engineRPM = 800;
        vehicleSpeed = 0;
        coolantTemp = 75;
        intakeTemp = 25;
        fuelLevel = 65.0f;
        throttlePosition = 15;

        log.info("DEMO: Reset complete - " + activeFaultCodes.size() + " fault codes active");
    }

    /**
     * Clear fault codes (demo mode only)
     */
    public void clearFaultCodes() {
        log.info("DEMO: Clearing fault codes");
        faultCodesCleared = true;
        activeFaultCodes.clear();
        milOn = false;
        numCodes = 0;
    }

    /**
     * Get fault codes response for service 03/07/0A
     * @param service The OBD service (03=stored, 07=pending, 0A=permanent)
     * @return Formatted response strings
     */
    public String[] getFaultCodesResponse(int service) {
        List<String> responses = new ArrayList<>();

        if (faultCodesCleared || activeFaultCodes.isEmpty()) {
            // No codes set
            responses.add(String.format("4%X0100000000", service));
            log.info("DEMO: No fault codes to report");
        } else {
            // Calculate number of codes and MIL status
            int statusByte = activeFaultCodes.size();
            if (milOn) {
                statusByte |= 0x80;  // Set MIL bit
            }

            // First response: number of codes + MIL status
            responses.add(String.format("4%X01%02X000000", service, statusByte));

            // Format codes in groups of 3 (6 bytes per response)
            StringBuilder currentResponse = new StringBuilder();
            int codeCount = 0;

            for (FaultCode code : activeFaultCodes) {
                if (codeCount % 3 == 0 && codeCount > 0) {
                    // Complete previous response
                    responses.add(String.format("4%X%s", service, currentResponse.toString()));
                    currentResponse = new StringBuilder();
                }
                currentResponse.append(code.toHexString());
                codeCount++;
            }

            // Add remaining codes if any
            if (currentResponse.length() > 0) {
                // Pad with zeros if needed
                while (currentResponse.length() < 12) {
                    currentResponse.append("00");
                }
                responses.add(String.format("4%X%s", service, currentResponse.toString()));
            }

            log.info("DEMO: Reporting " + activeFaultCodes.size() + " fault codes");
        }

        return responses.toArray(new String[0]);
    }

    /**
     * Handle clear codes service (04)
     * @return Response string
     */
    public String handleClearCodes() {
        clearFaultCodes();
        // Return successful clear response
        return "44";  // Service 04 acknowledgement
    }

    /**
     * Get demo PID value with realistic variations
     * @param pid The PID to get value for
     * @param service The service (01=live, 02=freeze frame)
     * @return Formatted response string
     */
    public String getPidValue(int pid, int service) {
        String response;

        // Add small random variations for realism
        switch (pid) {
            case 0x00:  // PIDs supported 01-20
                response = String.format("4%X00FFFFFFFF", service);
                break;

            case 0x01:  // Monitor status
                int status = milOn ? 0x8C07FF00 : 0x0C07FF00;
                response = String.format("4%X01%08X", service, status);
                break;

            case 0x04:  // Engine load
                int load = 35 + random.nextInt(10);
                response = String.format("4%X04%02X", service, load);
                break;

            case 0x05:  // Coolant temperature
                coolantTemp = Math.min(105, coolantTemp + random.nextInt(3) - 1);
                response = String.format("4%X05%02X", service, coolantTemp + 40);
                break;

            case 0x0C:  // Engine RPM
                engineRPM = 750 + random.nextInt(100);
                int rpmValue = engineRPM * 4;
                response = String.format("4%X0C%04X", service, rpmValue);
                break;

            case 0x0D:  // Vehicle speed
                vehicleSpeed = Math.max(0, vehicleSpeed + random.nextInt(5) - 2);
                response = String.format("4%X0D%02X", service, vehicleSpeed);
                break;

            case 0x0F:  // Intake temperature
                intakeTemp = Math.max(15, Math.min(40, intakeTemp + random.nextInt(3) - 1));
                response = String.format("4%X0F%02X", service, intakeTemp + 40);
                break;

            case 0x10:  // MAF air flow
                int maf = 15 + random.nextInt(5);
                response = String.format("4%X10%04X", service, maf * 100);
                break;

            case 0x11:  // Throttle position
                throttlePosition = Math.max(10, Math.min(25, throttlePosition + random.nextInt(3) - 1));
                response = String.format("4%X11%02X", service, throttlePosition * 255 / 100);
                break;

            case 0x20:  // PIDs supported 21-40
                response = String.format("4%X20FFFFFFFF", service);
                break;

            case 0x2F:  // Fuel level
                fuelLevel = Math.max(10, fuelLevel - 0.01f);
                response = String.format("4%X2F%02X", service, (int)(fuelLevel * 255 / 100));
                break;

            case 0x40:  // PIDs supported 41-60
                response = String.format("4%X40FFFFFFFF", service);
                break;

            case 0x46:  // Ambient temperature
                response = String.format("4%X46%02X", service, 22 + 40);  // 22°C
                break;

            case 0x60:  // PIDs supported 61-80
                response = String.format("4%X60FFFFFFFF", service);
                break;

            case 0x80:  // PIDs supported 81-A0
                response = String.format("4%X80FFFFFFFE", service);
                break;

            default:
                // Generic response with some value
                response = String.format("4%X%02X%02X%02X%02X%02X",
                    service, pid, 0x00, 0x00, 0x00, 0x00);
                break;
        }

        return response;
    }

    /**
     * Set number of demo fault codes (for testing)
     */
    public void setNumFaultCodes(int num) {
        if (num < 0 || num > DEFAULT_FAULT_CODES.length) {
            return;
        }

        activeFaultCodes.clear();
        faultCodesCleared = false;

        if (num == 0) {
            milOn = false;
            numCodes = 0;
        } else {
            milOn = true;
            numCodes = num;
            for (int i = 0; i < num; i++) {
                activeFaultCodes.add(new FaultCode(
                    DEFAULT_FAULT_CODES[i][0],
                    DEFAULT_FAULT_CODES[i][1],
                    false
                ));
            }
        }

        log.info("DEMO: Set " + num + " fault codes");
    }

    /**
     * Check if MIL is on
     */
    public boolean isMilOn() {
        return milOn && !activeFaultCodes.isEmpty();
    }

    /**
     * Get active fault codes
     */
    public List<FaultCode> getActiveFaultCodes() {
        return new ArrayList<>(activeFaultCodes);
    }

    /**
     * Get number of active fault codes
     */
    public int getNumFaultCodes() {
        return activeFaultCodes.isEmpty() ? 0 : activeFaultCodes.size();
    }
}