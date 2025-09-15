package gnu.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * RXTX-compatible SerialPort wrapper using jSerialComm
 * This allows the desktop application to work without RXTX library
 */
public class SerialPort extends CommPort {

    // Data bits constants
    public static final int DATABITS_5 = 5;
    public static final int DATABITS_6 = 6;
    public static final int DATABITS_7 = 7;
    public static final int DATABITS_8 = 8;

    // Stop bits constants
    public static final int STOPBITS_1 = com.fazecast.jSerialComm.SerialPort.ONE_STOP_BIT;
    public static final int STOPBITS_2 = com.fazecast.jSerialComm.SerialPort.TWO_STOP_BITS;
    public static final int STOPBITS_1_5 = com.fazecast.jSerialComm.SerialPort.ONE_POINT_FIVE_STOP_BITS;

    // Parity constants
    public static final int PARITY_NONE = com.fazecast.jSerialComm.SerialPort.NO_PARITY;
    public static final int PARITY_ODD = com.fazecast.jSerialComm.SerialPort.ODD_PARITY;
    public static final int PARITY_EVEN = com.fazecast.jSerialComm.SerialPort.EVEN_PARITY;
    public static final int PARITY_MARK = com.fazecast.jSerialComm.SerialPort.MARK_PARITY;
    public static final int PARITY_SPACE = com.fazecast.jSerialComm.SerialPort.SPACE_PARITY;

    // Flow control constants
    public static final int FLOWCONTROL_NONE = 0;
    public static final int FLOWCONTROL_RTSCTS_IN = 1;
    public static final int FLOWCONTROL_RTSCTS_OUT = 2;
    public static final int FLOWCONTROL_XONXOFF_IN = 4;
    public static final int FLOWCONTROL_XONXOFF_OUT = 8;

    private com.fazecast.jSerialComm.SerialPort jSerialCommPort;
    private SerialPortEventListener eventListener;
    private boolean notifyOnDataAvailable = false;

    public SerialPort(com.fazecast.jSerialComm.SerialPort port) {
        super();
        this.jSerialCommPort = port;
        this.name = port.getSystemPortName();
    }

    public void setSerialPortParams(int baudRate, int dataBits, int stopBits, int parity)
            throws UnsupportedCommOperationException {
        if (jSerialCommPort != null) {
            jSerialCommPort.setBaudRate(baudRate);
            jSerialCommPort.setNumDataBits(dataBits);
            jSerialCommPort.setNumStopBits(stopBits);
            jSerialCommPort.setParity(parity);
        }
    }

    public int getBaudRate() {
        return jSerialCommPort != null ? jSerialCommPort.getBaudRate() : 0;
    }

    public int getDataBits() {
        return jSerialCommPort != null ? jSerialCommPort.getNumDataBits() : 0;
    }

    public int getStopBits() {
        return jSerialCommPort != null ? jSerialCommPort.getNumStopBits() : 0;
    }

    public int getParity() {
        return jSerialCommPort != null ? jSerialCommPort.getParity() : 0;
    }

    public void setFlowControlMode(int flowControlMode) throws UnsupportedCommOperationException {
        if (jSerialCommPort != null) {
            int mode = com.fazecast.jSerialComm.SerialPort.FLOW_CONTROL_DISABLED;

            if ((flowControlMode & (FLOWCONTROL_RTSCTS_IN | FLOWCONTROL_RTSCTS_OUT)) != 0) {
                mode = com.fazecast.jSerialComm.SerialPort.FLOW_CONTROL_RTS_ENABLED |
                       com.fazecast.jSerialComm.SerialPort.FLOW_CONTROL_CTS_ENABLED;
            } else if ((flowControlMode & (FLOWCONTROL_XONXOFF_IN | FLOWCONTROL_XONXOFF_OUT)) != 0) {
                mode = com.fazecast.jSerialComm.SerialPort.FLOW_CONTROL_XONXOFF_IN_ENABLED |
                       com.fazecast.jSerialComm.SerialPort.FLOW_CONTROL_XONXOFF_OUT_ENABLED;
            }

            jSerialCommPort.setFlowControl(mode);
        }
    }

    public int getFlowControlMode() {
        if (jSerialCommPort == null) return FLOWCONTROL_NONE;

        int flowControl = jSerialCommPort.getFlowControlSettings();
        int mode = FLOWCONTROL_NONE;

        if ((flowControl & com.fazecast.jSerialComm.SerialPort.FLOW_CONTROL_RTS_ENABLED) != 0) {
            mode |= FLOWCONTROL_RTSCTS_OUT;
        }
        if ((flowControl & com.fazecast.jSerialComm.SerialPort.FLOW_CONTROL_CTS_ENABLED) != 0) {
            mode |= FLOWCONTROL_RTSCTS_IN;
        }
        if ((flowControl & com.fazecast.jSerialComm.SerialPort.FLOW_CONTROL_XONXOFF_OUT_ENABLED) != 0) {
            mode |= FLOWCONTROL_XONXOFF_OUT;
        }
        if ((flowControl & com.fazecast.jSerialComm.SerialPort.FLOW_CONTROL_XONXOFF_IN_ENABLED) != 0) {
            mode |= FLOWCONTROL_XONXOFF_IN;
        }

        return mode;
    }

    public void addEventListener(SerialPortEventListener listener) throws TooManyListenersException {
        this.eventListener = listener;

        if (jSerialCommPort != null) {
            jSerialCommPort.addDataListener(new com.fazecast.jSerialComm.SerialPortDataListener() {
                @Override
                public int getListeningEvents() {
                    return com.fazecast.jSerialComm.SerialPort.LISTENING_EVENT_DATA_AVAILABLE;
                }

                @Override
                public void serialEvent(com.fazecast.jSerialComm.SerialPortEvent event) {
                    if (notifyOnDataAvailable && eventListener != null &&
                        event.getEventType() == com.fazecast.jSerialComm.SerialPort.LISTENING_EVENT_DATA_AVAILABLE) {
                        eventListener.serialEvent(new SerialPortEvent(SerialPort.this,
                                                                      SerialPortEvent.DATA_AVAILABLE,
                                                                      false, true));
                    }
                }
            });
        }
    }

    public void removeEventListener() {
        this.eventListener = null;
        if (jSerialCommPort != null) {
            jSerialCommPort.removeDataListener();
        }
    }

    public void notifyOnDataAvailable(boolean enable) {
        this.notifyOnDataAvailable = enable;
    }

    public void enableReceiveTimeout(int timeout) throws UnsupportedCommOperationException {
        if (jSerialCommPort != null) {
            jSerialCommPort.setComPortTimeouts(
                com.fazecast.jSerialComm.SerialPort.TIMEOUT_READ_SEMI_BLOCKING,
                timeout, 0);
        }
    }

    public void disableReceiveTimeout() {
        if (jSerialCommPort != null) {
            jSerialCommPort.setComPortTimeouts(
                com.fazecast.jSerialComm.SerialPort.TIMEOUT_READ_BLOCKING,
                0, 0);
        }
    }

    public boolean isDSR() {
        return jSerialCommPort != null && jSerialCommPort.getDSR();
    }

    public boolean isCTS() {
        return jSerialCommPort != null && jSerialCommPort.getCTS();
    }

    public boolean isCD() {
        return jSerialCommPort != null && jSerialCommPort.getDCD();
    }

    public boolean isDTR() {
        return jSerialCommPort != null && jSerialCommPort.getDTR();
    }

    public boolean isRTS() {
        return jSerialCommPort != null && jSerialCommPort.getRTS();
    }

    public void setDTR(boolean state) {
        if (jSerialCommPort != null) {
            if (state) {
                jSerialCommPort.setDTR();
            } else {
                jSerialCommPort.clearDTR();
            }
        }
    }

    public void setRTS(boolean state) {
        if (jSerialCommPort != null) {
            if (state) {
                jSerialCommPort.setRTS();
            } else {
                jSerialCommPort.clearRTS();
            }
        }
    }

    @Override
    public InputStream getInputStream() throws IOException {
        if (jSerialCommPort != null && jSerialCommPort.isOpen()) {
            return jSerialCommPort.getInputStream();
        }
        throw new IOException("Port not open");
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        if (jSerialCommPort != null && jSerialCommPort.isOpen()) {
            return jSerialCommPort.getOutputStream();
        }
        throw new IOException("Port not open");
    }

    @Override
    public void close() {
        if (jSerialCommPort != null && jSerialCommPort.isOpen()) {
            jSerialCommPort.closePort();
        }
    }

    public boolean isOpen() {
        return jSerialCommPort != null && jSerialCommPort.isOpen();
    }

    com.fazecast.jSerialComm.SerialPort getJSerialCommPort() {
        return jSerialCommPort;
    }
}