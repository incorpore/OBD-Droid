package gnu.io;

/**
 * RXTX-compatible exception for unsupported comm operations
 */
public class UnsupportedCommOperationException extends Exception {
    
    public UnsupportedCommOperationException() {
        super();
    }
    
    public UnsupportedCommOperationException(String message) {
        super(message);
    }
    
    public UnsupportedCommOperationException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public UnsupportedCommOperationException(Throwable cause) {
        super(cause);
    }
}