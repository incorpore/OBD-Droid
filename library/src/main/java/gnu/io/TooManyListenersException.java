package gnu.io;

/**
 * RXTX-compatible exception for too many listeners
 */
public class TooManyListenersException extends Exception {
    
    public TooManyListenersException() {
        super();
    }
    
    public TooManyListenersException(String message) {
        super(message);
    }
}