package com.obddroid.obd;

import com.obddroid.ecu.EcuCodeItem;
import com.obddroid.ecu.EcuDataPv;
import com.obddroid.common.ProcessVariables.PvList;

import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Manages freeze frame data with proper DTC correlation
 * Understands OBD-II freeze frame structure and PID 0x02 relationships
 *
 * @author Wal33D
 */
public class FreezeFrameManager {
    private static final Logger log = Logger.getLogger(FreezeFrameManager.class.getName());

    // PID 0x02 tells us which DTC triggered freeze frame 0
    private static final int PID_FREEZE_FRAME_DTC = 0x02;

    // Freeze frame data by frame ID
    private final ConcurrentHashMap<Integer, FreezeFrameData> freezeFrames = new ConcurrentHashMap<>();

    // Max freeze frames to cache
    private static final int MAX_FREEZE_FRAMES = 10;

    /**
     * Data structure for a single freeze frame
     */
    public static class FreezeFrameData {
        public final int frameId;
        public final String dtcCode;  // The DTC that triggered this frame
        public final PvList data;     // The actual freeze frame PIDs
        public final long timestamp;

        public FreezeFrameData(int frameId, String dtcCode, PvList data) {
            this.frameId = frameId;
            this.dtcCode = dtcCode;
            this.data = new PvList();
            if (data != null) {
                this.data.putAll(data);
            }
            this.timestamp = System.currentTimeMillis();
        }
    }

    /**
     * Process freeze frame data with DTC correlation
     * @param frameId The freeze frame ID (0 = most recent)
     * @param frameData The PID data for this frame
     * @param faultCodes Available fault codes for correlation
     */
    public void processFreezeFrame(int frameId, PvList frameData,
                                    ConcurrentHashMap<Integer, EcuCodeItem> faultCodes) {
        if (frameData == null || frameData.isEmpty()) {
            log.warning("No data for freeze frame " + frameId);
            return;
        }

        String dtcCode = null;

        // Frame 0 is special - it's the most recent fault
        if (frameId == 0) {
            // Check PID 0x02 to find which DTC triggered this frame
            Object pid02 = frameData.get(PID_FREEZE_FRAME_DTC);
            if (pid02 instanceof EcuDataPv) {
                EcuDataPv pv = (EcuDataPv) pid02;
                Integer dtcValue = (Integer) pv.get(EcuDataPv.FID_VALUE);
                if (dtcValue != null && dtcValue > 0) {
                    // Convert the value to DTC format
                    dtcCode = convertToDtcCode(dtcValue);
                    log.info("Frame 0 corresponds to DTC: " + dtcCode);
                }
            }

            // If we couldn't get it from PID 0x02, use the first fault code
            if (dtcCode == null && faultCodes != null && !faultCodes.isEmpty()) {
                // Get the most recent fault code (usually first)
                for (EcuCodeItem code : faultCodes.values()) {
                    String codeStr = (String) code.get(EcuCodeItem.FID_CODE);
                    if (codeStr != null && !"0".equals(codeStr)) {  // Skip "no codes" entry
                        dtcCode = codeStr;
                        log.info("Associating frame 0 with most recent DTC: " + dtcCode);
                        break;
                    }
                }
            }
        } else {
            // For other frames, try to correlate with fault codes by position
            // Note: This is less reliable as not all DTCs have freeze frames
            if (faultCodes != null && frameId <= faultCodes.size()) {
                int index = 0;
                for (EcuCodeItem code : faultCodes.values()) {
                    String codeStr = (String) code.get(EcuCodeItem.FID_CODE);
                    if (codeStr != null && !"0".equals(codeStr)) {  // Skip "no codes" entry
                        if (index == frameId) {
                            dtcCode = codeStr;
                            break;
                        }
                        index++;
                    }
                }
            }
        }

        // Store the freeze frame with its DTC association
        FreezeFrameData ffData = new FreezeFrameData(frameId, dtcCode, frameData);
        freezeFrames.put(frameId, ffData);

        // Enforce max size
        if (freezeFrames.size() > MAX_FREEZE_FRAMES) {
            // Remove oldest entry (highest frame ID typically)
            int maxId = freezeFrames.keySet().stream()
                .mapToInt(Integer::intValue)
                .max()
                .orElse(MAX_FREEZE_FRAMES);
            freezeFrames.remove(maxId);
        }

        log.info("Stored freeze frame " + frameId +
                 (dtcCode != null ? " for DTC " + dtcCode : " (no DTC association)") +
                 " with " + frameData.size() + " PIDs");
    }

    /**
     * Convert PID 0x02 value to DTC code format
     * The value contains the DTC in a specific format
     */
    private String convertToDtcCode(int value) {
        // OBD-II DTC format: First byte = type, next 2 bytes = code
        if (value == 0) {
            return null;
        }

        // Extract the DTC type (P, C, B, U)
        int type = (value >> 14) & 0x03;
        String prefix;
        switch (type) {
            case 0: prefix = "P"; break;  // Powertrain
            case 1: prefix = "C"; break;  // Chassis
            case 2: prefix = "B"; break;  // Body
            case 3: prefix = "U"; break;  // Network
            default: prefix = "P"; break;
        }

        // Extract the code number
        int code = value & 0x3FFF;
        return String.format("%s%04X", prefix, code);
    }

    /**
     * Get freeze frame by ID
     */
    public FreezeFrameData getFreezeFrame(int frameId) {
        return freezeFrames.get(frameId);
    }

    /**
     * Get freeze frame by DTC code
     */
    public FreezeFrameData getFreezeFrameByDtc(String dtcCode) {
        if (dtcCode == null) return null;

        for (FreezeFrameData frame : freezeFrames.values()) {
            if (dtcCode.equals(frame.dtcCode)) {
                return frame;
            }
        }
        return null;
    }

    /**
     * Get all cached freeze frames
     */
    public ConcurrentHashMap<Integer, FreezeFrameData> getAllFreezeFrames() {
        return new ConcurrentHashMap<>(freezeFrames);
    }

    /**
     * Clear all freeze frames
     */
    public void clear() {
        freezeFrames.clear();
        log.info("Cleared all freeze frame data");
    }

    /**
     * Get the number of cached freeze frames
     */
    public int size() {
        return freezeFrames.size();
    }
}