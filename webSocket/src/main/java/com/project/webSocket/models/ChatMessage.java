package com.project.webSocket.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ChatMessage {
    private String id;
    private String sender;
    private String recipient; // for 1-1 chat
    private String groupId;   // for group chat
    private String content;
    private MessageType type;

    public enum MessageType {
        CHAT, JOIN, LEAVE, TYPING
    }
}
