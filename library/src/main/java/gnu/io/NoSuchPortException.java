package gnu.io;

/**
 * RXTX-compatible exception for port not found
 */
public class NoSuchPortException extends Exception {
    
    public NoSuchPortException() {
        super();
    }
    
    public NoSuchPortException(String message) {
        super(message);
    }
}