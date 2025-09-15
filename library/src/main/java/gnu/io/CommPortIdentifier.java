package gnu.io;

import java.util.Enumeration;
import java.util.Vector;

/**
 * RXTX-compatible CommPortIdentifier for port discovery and management
 */
public class CommPortIdentifier {
    
    public static final int PORT_SERIAL = 1;
    public static final int PORT_PARALLEL = 2;
    public static final int PORT_I2C = 3;
    public static final int PORT_RS485 = 4;
    public static final int PORT_RAW = 5;
    
    private String portName;
    private int portType;
    private com.fazecast.jSerialComm.SerialPort jSerialCommPort;
    private CommPort commPort;
    private boolean currentlyOwned = false;
    private String owner;
    
    private CommPortIdentifier(String portName, com.fazecast.jSerialComm.SerialPort port) {
        this.portName = portName;
        this.portType = PORT_SERIAL;
        this.jSerialCommPort = port;
    }
    
    /**
     * Get an enumeration of all available ports
     */
    public static Enumeration<CommPortIdentifier> getPortIdentifiers() {
        Vector<CommPortIdentifier> ports = new Vector<>();
        
        com.fazecast.jSerialComm.SerialPort[] serialPorts = com.fazecast.jSerialComm.SerialPort.getCommPorts();
        for (com.fazecast.jSerialComm.SerialPort port : serialPorts) {
            ports.add(new CommPortIdentifier(port.getSystemPortName(), port));
        }
        
        return ports.elements();
    }
    
    /**
     * Get a specific port by name
     */
    public static CommPortIdentifier getPortIdentifier(String portName) throws NoSuchPortException {
        com.fazecast.jSerialComm.SerialPort[] serialPorts = com.fazecast.jSerialComm.SerialPort.getCommPorts();
        for (com.fazecast.jSerialComm.SerialPort port : serialPorts) {
            if (port.getSystemPortName().equals(portName)) {
                return new CommPortIdentifier(portName, port);
            }
        }
        throw new NoSuchPortException();
    }
    
    /**
     * Get a specific port by CommPort
     */
    public static CommPortIdentifier getPortIdentifier(CommPort port) throws NoSuchPortException {
        if (port instanceof SerialPort) {
            SerialPort serialPort = (SerialPort) port;
            return new CommPortIdentifier(port.getName(), serialPort.getJSerialCommPort());
        }
        throw new NoSuchPortException();
    }
    
    /**
     * Open the port
     */
    public synchronized CommPort open(String appName, int timeout) throws PortInUseException {
        if (currentlyOwned) {
            throw new PortInUseException();
        }
        
        if (jSerialCommPort != null) {
            if (jSerialCommPort.openPort()) {
                currentlyOwned = true;
                owner = appName;
                commPort = new SerialPort(jSerialCommPort);
                return commPort;
            }
        }
        
        throw new PortInUseException();
    }
    
    /**
     * Close the port
     */
    public synchronized void close() {
        if (commPort != null) {
            commPort.close();
            commPort = null;
        }
        currentlyOwned = false;
        owner = null;
    }
    
    public String getName() {
        return portName;
    }
    
    public int getPortType() {
        return portType;
    }
    
    public boolean isCurrentlyOwned() {
        return currentlyOwned;
    }
    
    public String getCurrentOwner() {
        return owner;
    }
}