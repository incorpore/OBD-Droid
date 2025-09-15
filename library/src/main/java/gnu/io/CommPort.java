package gnu.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * RXTX-compatible CommPort base class
 */
public abstract class CommPort {
    
    protected String name;
    
    public String getName() {
        return name;
    }
    
    public abstract InputStream getInputStream() throws IOException;
    
    public abstract OutputStream getOutputStream() throws IOException;
    
    public abstract void close();
    
    public void enableReceiveThreshold(int thresh) throws UnsupportedCommOperationException {
        throw new UnsupportedCommOperationException("Not supported");
    }
    
    public void disableReceiveThreshold() {
        // Default implementation
    }
    
    public boolean isReceiveThresholdEnabled() {
        return false;
    }
    
    public int getReceiveThreshold() {
        return 0;
    }
    
    public void enableReceiveTimeout(int timeout) throws UnsupportedCommOperationException {
        throw new UnsupportedCommOperationException("Not supported");
    }
    
    public void disableReceiveTimeout() {
        // Default implementation
    }
    
    public boolean isReceiveTimeoutEnabled() {
        return false;
    }
    
    public int getReceiveTimeout() {
        return 0;
    }
    
    public void enableReceiveFraming(int framingByte) throws UnsupportedCommOperationException {
        throw new UnsupportedCommOperationException("Not supported");
    }
    
    public void disableReceiveFraming() {
        // Default implementation
    }
    
    public boolean isReceiveFramingEnabled() {
        return false;
    }
    
    public int getReceiveFramingByte() {
        return 0;
    }
    
    public void setInputBufferSize(int size) {
        // Default implementation
    }
    
    public int getInputBufferSize() {
        return 0;
    }
    
    public void setOutputBufferSize(int size) {
        // Default implementation
    }
    
    public int getOutputBufferSize() {
        return 0;
    }
}