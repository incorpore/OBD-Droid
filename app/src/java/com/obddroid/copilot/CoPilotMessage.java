package com.obddroid.copilot;

import com.obddroid.utils.OpenAiService;

/**
 * Represents a single message in the CoPilot conversation.
 */
final class CoPilotMessage {

    private final String role;
    private final String content;

    CoPilotMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }

    String getRole() {
        return role;
    }

    String getContent() {
        return content;
    }

    OpenAiService.ChatMessage toChatMessage() {
        return new OpenAiService.ChatMessage(role, content);
    }
}
