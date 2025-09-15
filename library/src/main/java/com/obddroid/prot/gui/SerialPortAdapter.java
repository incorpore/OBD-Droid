package com.obddroid.prot.gui;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Adapter to use jSerialComm instead of RXTX
 * Provides compatibility layer for the desktop application
 */
public class SerialPortAdapter {

    // Flow control constants matching RXTX
    public static final int FLOWCONTROL_NONE = 0;
    public static final int FLOWCONTROL_RTSCTS_IN = 1;
    public static final int FLOWCONTROL_RTSCTS_OUT = 2;
    public static final int FLOWCONTROL_XONXOFF_IN = 4;
    public static final int FLOWCONTROL_XONXOFF_OUT = 8;

    // Data bits constants
    public static final int DATABITS_5 = 5;
    public static final int DATABITS_6 = 6;
    public static final int DATABITS_7 = 7;
    public static final int DATABITS_8 = 8;

    // Stop bits constants
    public static final int STOPBITS_1 = SerialPort.ONE_STOP_BIT;
    public static final int STOPBITS_2 = SerialPort.TWO_STOP_BITS;
    public static final int STOPBITS_1_5 = SerialPort.ONE_POINT_FIVE_STOP_BITS;

    // Parity constants
    public static final int PARITY_NONE = SerialPort.NO_PARITY;
    public static final int PARITY_ODD = SerialPort.ODD_PARITY;
    public static final int PARITY_EVEN = SerialPort.EVEN_PARITY;
    public static final int PARITY_MARK = SerialPort.MARK_PARITY;
    public static final int PARITY_SPACE = SerialPort.SPACE_PARITY;

    private SerialPort serialPort;
    private String portName;

    public SerialPortAdapter(String portName) {
        this.portName = portName;
        this.serialPort = SerialPort.getCommPort(portName);
    }

    public static List<String> getAvailablePorts() {
        List<String> portNames = new ArrayList<>();
        SerialPort[] ports = SerialPort.getCommPorts();
        for (SerialPort port : ports) {
            portNames.add(port.getSystemPortName());
        }
        return portNames;
    }

    public boolean open() {
        if (serialPort != null) {
            return serialPort.openPort();
        }
        return false;
    }

    public void close() {
        if (serialPort != null && serialPort.isOpen()) {
            serialPort.closePort();
        }
    }

    public void setSerialPortParams(int baudRate, int dataBits, int stopBits, int parity) {
        if (serialPort != null) {
            serialPort.setBaudRate(baudRate);
            serialPort.setNumDataBits(dataBits);
            serialPort.setNumStopBits(stopBits);
            serialPort.setParity(parity);
        }
    }

    public void setFlowControlMode(int flowControlMode) {
        if (serialPort != null) {
            if (flowControlMode == FLOWCONTROL_NONE) {
                serialPort.setFlowControl(SerialPort.FLOW_CONTROL_DISABLED);
            } else if ((flowControlMode & (FLOWCONTROL_RTSCTS_IN | FLOWCONTROL_RTSCTS_OUT)) != 0) {
                serialPort.setFlowControl(SerialPort.FLOW_CONTROL_RTS_ENABLED | SerialPort.FLOW_CONTROL_CTS_ENABLED);
            } else if ((flowControlMode & (FLOWCONTROL_XONXOFF_IN | FLOWCONTROL_XONXOFF_OUT)) != 0) {
                serialPort.setFlowControl(SerialPort.FLOW_CONTROL_XONXOFF_IN_ENABLED | SerialPort.FLOW_CONTROL_XONXOFF_OUT_ENABLED);
            }
        }
    }

    public InputStream getInputStream() throws IOException {
        if (serialPort != null && serialPort.isOpen()) {
            return serialPort.getInputStream();
        }
        throw new IOException("Serial port not open");
    }

    public OutputStream getOutputStream() throws IOException {
        if (serialPort != null && serialPort.isOpen()) {
            return serialPort.getOutputStream();
        }
        throw new IOException("Serial port not open");
    }

    public void addEventListener(SerialPortEventListener listener) {
        if (serialPort != null) {
            serialPort.addDataListener(new SerialPortDataListener() {
                @Override
                public int getListeningEvents() {
                    return SerialPort.LISTENING_EVENT_DATA_AVAILABLE;
                }

                @Override
                public void serialEvent(SerialPortEvent event) {
                    if (event.getEventType() == SerialPort.LISTENING_EVENT_DATA_AVAILABLE) {
                        listener.serialEvent(new SerialPortEventAdapter(event));
                    }
                }
            });
        }
    }

    public void removeEventListener() {
        if (serialPort != null) {
            serialPort.removeDataListener();
        }
    }

    public void notifyOnDataAvailable(boolean enable) {
        // Handled by addEventListener
    }

    public void enableReceiveTimeout(int timeout) {
        if (serialPort != null) {
            serialPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, timeout, 0);
        }
    }

    public String getName() {
        return portName;
    }

    public boolean isOpen() {
        return serialPort != null && serialPort.isOpen();
    }

    // Event listener interface
    public interface SerialPortEventListener {
        void serialEvent(SerialPortEventAdapter event);
    }

    // Event adapter class
    public static class SerialPortEventAdapter {
        public static final int DATA_AVAILABLE = 1;
        private final SerialPortEvent event;

        public SerialPortEventAdapter(SerialPortEvent event) {
            this.event = event;
        }

        public int getEventType() {
            if (event.getEventType() == SerialPort.LISTENING_EVENT_DATA_AVAILABLE) {
                return DATA_AVAILABLE;
            }
            return 0;
        }
    }

    // Port identifier helper
    public static class CommPortIdentifier {
        public static SerialPortAdapter getPortIdentifier(String portName) throws Exception {
            SerialPort[] ports = SerialPort.getCommPorts();
            for (SerialPort port : ports) {
                if (port.getSystemPortName().equals(portName)) {
                    return new SerialPortAdapter(portName);
                }
            }
            throw new Exception("Port not found: " + portName);
        }

        public static List<SerialPortAdapter> getPortIdentifiers() {
            List<SerialPortAdapter> adapters = new ArrayList<>();
            SerialPort[] ports = SerialPort.getCommPorts();
            for (SerialPort port : ports) {
                adapters.add(new SerialPortAdapter(port.getSystemPortName()));
            }
            return adapters;
        }
    }
}