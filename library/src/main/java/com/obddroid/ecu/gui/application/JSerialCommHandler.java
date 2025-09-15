package com.obddroid.ecu.gui.application;

import com.obddroid.prot.TelegramListener;
import com.obddroid.prot.TelegramWriter;
import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Serial communication handler using jSerialComm library
 * Replaces the old SerialHandler that required native libraries
 */
public class JSerialCommHandler extends Thread implements TelegramWriter, SerialPortDataListener {

    private static final Logger log = Logger.getLogger(JSerialCommHandler.class.getName());

    private final SerialPort serialPort;
    private TelegramListener messageHandler;
    private InputStream inputStream;
    private OutputStream outputStream;
    private StringBuilder messageBuffer = new StringBuilder();
    private volatile boolean running = false;

    public JSerialCommHandler(SerialPort port) {
        this.serialPort = port;
        try {
            this.inputStream = port.getInputStream();
            this.outputStream = port.getOutputStream();
            // Add data listener for incoming data
            port.addDataListener(this);
        } catch (Exception e) {
            log.log(Level.SEVERE, "Error setting up serial streams", e);
        }
    }

    @Override
    public void run() {
        running = true;
        log.info("Serial handler started for port: " + serialPort.getSystemPortName());

        try {
            while (running && serialPort.isOpen()) {
                if (inputStream.available() > 0) {
                    int chr = inputStream.read();
                    if (chr >= 0) {
                        processChar(chr);
                    }
                }
                Thread.sleep(10); // Small delay to prevent CPU spinning
            }
        } catch (Exception e) {
            log.log(Level.SEVERE, "Error in serial read loop", e);
        }

        log.info("Serial handler stopped");
    }

    private void processChar(int chr) {
        switch (chr) {
            case 13: // CR
            case 32: // Space
                // Ignore these characters
                break;

            case '>': // Prompt character
                messageBuffer.append((char) chr);
                // Fall through to process message
            case 10: // LF
                if (messageHandler != null && messageBuffer.length() > 0) {
                    String message = messageBuffer.toString();
                    messageHandler.handleTelegram(message.toCharArray());
                }
                messageBuffer.setLength(0);
                break;

            default:
                messageBuffer.append((char) chr);
        }
    }

    @Override
    public int writeTelegram(char[] buffer) {
        return writeTelegram(buffer, 0, null);
    }

    @Override
    public int writeTelegram(char[] buffer, int type, Object id) {
        try {
            String message = new String(buffer);
            if (!message.endsWith("\r")) {
                message += "\r"; // ELM327 needs CR termination
            }
            byte[] bytes = message.getBytes();
            outputStream.write(bytes);
            outputStream.flush();

            log.fine("Sent: " + message.trim());
            return buffer.length;
        } catch (Exception e) {
            log.log(Level.SEVERE, "Error writing telegram", e);
            return 0;
        }
    }

    public void setMessageHandler(TelegramListener handler) {
        this.messageHandler = handler;
    }

    public void stopHandler() {
        running = false;
        if (serialPort != null && serialPort.isOpen()) {
            serialPort.closePort();
        }
    }

    // SerialPortDataListener implementation
    @Override
    public int getListeningEvents() {
        return SerialPort.LISTENING_EVENT_DATA_AVAILABLE;
    }

    @Override
    public void serialEvent(SerialPortEvent event) {
        // Data available event is handled in the run loop
        // This is here for future event-driven processing if needed
    }
}