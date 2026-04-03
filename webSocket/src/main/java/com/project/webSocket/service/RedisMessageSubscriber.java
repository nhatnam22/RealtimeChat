package com.project.webSocket.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.webSocket.models.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisMessageSubscriber implements MessageListener {

    private final SimpMessageSendingOperations messagingTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            log.info("Received message from Redis: " + new String(message.getBody()));
            ChatMessage chatMessage = objectMapper.readValue(message.getBody(), ChatMessage.class);

            if (chatMessage.getGroupId() != null) {
                // Group chat
                messagingTemplate.convertAndSend("/topic/group/" + chatMessage.getGroupId(), chatMessage);
            } else if (chatMessage.getRecipient() != null) {
                // 1-1 chat
                messagingTemplate.convertAndSendToUser(
                        chatMessage.getRecipient(),
                        "/queue/messages",
                        chatMessage
                );
            } else {
                // Public/Broadcast (e.g., online/offline status)
                messagingTemplate.convertAndSend("/topic/public", chatMessage);
            }
        } catch (Exception e) {
            log.error("Error processing Redis message", e);
        }
    }
}
