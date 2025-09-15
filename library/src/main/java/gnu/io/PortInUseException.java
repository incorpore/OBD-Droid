package gnu.io;

/**
 * RXTX-compatible exception for port already in use
 */
public class PortInUseException extends Exception {
    
    public PortInUseException() {
        super();
    }
    
    public PortInUseException(String message) {
        super(message);
    }
}