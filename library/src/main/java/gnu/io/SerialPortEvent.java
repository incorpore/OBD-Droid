package gnu.io;

import java.util.EventObject;

/**
 * RXTX-compatible serial port event
 */
public class SerialPortEvent extends EventObject {
    
    public static final int DATA_AVAILABLE = 1;
    public static final int OUTPUT_BUFFER_EMPTY = 2;
    public static final int CTS = 3;
    public static final int DSR = 4;
    public static final int RI = 5;
    public static final int CD = 6;
    public static final int OE = 7;
    public static final int PE = 8;
    public static final int FE = 9;
    public static final int BI = 10;
    
    private int eventType;
    private boolean oldValue;
    private boolean newValue;
    
    public SerialPortEvent(SerialPort srcPort, int eventType, boolean oldValue, boolean newValue) {
        super(srcPort);
        this.eventType = eventType;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }
    
    public int getEventType() {
        return eventType;
    }
    
    public boolean getNewValue() {
        return newValue;
    }
    
    public boolean getOldValue() {
        return oldValue;
    }
}