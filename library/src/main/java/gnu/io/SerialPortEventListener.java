package gnu.io;

import java.util.EventListener;

/**
 * RXTX-compatible serial port event listener interface
 */
public interface SerialPortEventListener extends EventListener {
    
    /**
     * Called when a serial port event occurs
     * @param event The serial port event
     */
    void serialEvent(SerialPortEvent event);
}