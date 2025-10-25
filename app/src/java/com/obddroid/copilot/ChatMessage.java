package com.obddroid.copilot;

/**
 * Represents a UI message in the CoPilot chat interface.
 * This is separate from CoPilotMessage which is used internally for API communication.
 */
public class ChatMessage {

    private final String content;
    private final boolean isFromUser;
    private final long timestamp;

    public ChatMessage(String content, boolean isFromUser, long timestamp) {
        this.content = content;
        this.isFromUser = isFromUser;
        this.timestamp = timestamp;
    }

    public String getContent() {
        return content;
    }

    public boolean isFromUser() {
        return isFromUser;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
