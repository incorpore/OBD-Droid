package com.obddroid.ecu.gui.application;

import com.obddroid.prot.TelegramListener;
import com.obddroid.prot.TelegramWriter;
import com.fazecast.jSerialComm.SerialPort;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Serial communication handler using jSerialComm library
 * Replaces the old SerialHandler that required native libraries
 */
public class JSerialCommHandler extends Thread implements TelegramWriter {

    private static final Logger log = Logger.getLogger(JSerialCommHandler.class.getName());

    private final SerialPort serialPort;
    private TelegramListener messageHandler;
    private InputStream inputStream;
    private OutputStream outputStream;
    private final StringBuilder messageBuffer = new StringBuilder();
    private volatile boolean running = false;
    private final Object bufferLock = new Object();

    public JSerialCommHandler(SerialPort port) throws IOException {
        this.serialPort = port;
        try {
            this.inputStream = port.getInputStream();
            this.outputStream = port.getOutputStream();
        } catch (Exception e) {
            log.log(Level.SEVERE, "Error setting up serial streams", e);
            throw new IOException("Failed to initialize serial streams", e);
        }
    }

    @Override
    public void run() {
        running = true;
        log.info("Serial handler started for port: " + serialPort.getSystemPortName());

        byte[] buffer = new byte[256];
        try {
            while (running && serialPort.isOpen()) {
                // Use blocking read instead of polling
                if (inputStream.available() > 0) {
                    int bytesRead = inputStream.read(buffer);
                    for (int i = 0; i < bytesRead; i++) {
                        processChar(buffer[i] & 0xFF);
                    }
                } else {
                    // Small sleep only when no data available
                    Thread.sleep(10);
                }
            }
        } catch (IOException e) {
            log.log(Level.SEVERE, "I/O error in serial read loop", e);
        } catch (InterruptedException e) {
            log.info("Serial handler interrupted");
            Thread.currentThread().interrupt();
        }

        log.info("Serial handler stopped");
    }

    private void processChar(int chr) {
        synchronized (bufferLock) {
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
    }

    @Override
    public int writeTelegram(char[] buffer) {
        return writeTelegram(buffer, 0, null);
    }

    @Override
    public int writeTelegram(char[] buffer, int type, Object id) {
        if (outputStream == null) {
            log.severe("Output stream is not initialized");
            return 0;
        }

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
        } catch (IOException e) {
            log.log(Level.SEVERE, "I/O error writing telegram", e);
            return 0;
        }
    }

    public void setMessageHandler(TelegramListener handler) {
        this.messageHandler = handler;
    }

    public void stopHandler() {
        running = false;

        // Close streams first
        try {
            if (inputStream != null) {
                inputStream.close();
            }
        } catch (IOException e) {
            log.log(Level.WARNING, "Error closing input stream", e);
        }

        try {
            if (outputStream != null) {
                outputStream.close();
            }
        } catch (IOException e) {
            log.log(Level.WARNING, "Error closing output stream", e);
        }

        // Then close the port
        if (serialPort != null && serialPort.isOpen()) {
            serialPort.closePort();
        }

        // Interrupt the thread if it's waiting
        if (this.isAlive()) {
            this.interrupt();
        }
    }

}