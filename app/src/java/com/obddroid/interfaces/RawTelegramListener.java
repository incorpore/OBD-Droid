package com.obddroid.interfaces;

/**
 * Listener for RAW telegram data BEFORE protocol header processing.
 *
 * This interface allows listeners to receive complete responses including
 * CAN headers (when headers are enabled with ATH1), which are normally
 * stripped by the protocol layer before reaching standard TelegramListeners.
 *
 * USE CASE: ECU discovery where we need to know which ECU sent which Mode 9 data.
 *
 * Example raw telegram with headers enabled:
 *   "7E8 03 49 02 01 34 4A 47 44 41..."
 *   ^^^ ECU address (0x7E8)
 *       ^^ Length
 *          ^^^^^ Mode 9 response (49 02 = response to PID 02)
 *
 * Without headers (standard TelegramListener receives):
 *   "49 02 01 34 4A 47 44 41..."
 *   ← No ECU address!
 *
 */
public interface RawTelegramListener {

    /**
     * Handle incoming raw telegram BEFORE header stripping.
     *
     * This is called by ElmProt BEFORE the standard protocol processing
     * that strips CAN headers. Listeners can extract ECU addresses,
     * parse multi-ECU responses, and perform other operations requiring
     * full telegram context.
     *
     * IMPORTANT: This is called on the Bluetooth receive thread. Keep
     * processing fast or delegate to a background thread.
     *
     * @param buffer Raw telegram buffer including CAN headers (if enabled)
     * @return Number of bytes processed (typically buffer.length)
     */
    int handleRawTelegram(char[] buffer);
}
