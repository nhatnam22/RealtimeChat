package com.project.webSocket.configuration;

import com.project.webSocket.models.ChatMessage;
import com.project.webSocket.service.RedisMessagePublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketEventListener {

    private final SimpMessageSendingOperations messagingTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisMessagePublisher redisMessagePublisher;

    private static final String ONLINE_USERS_KEY = "online_users";
    private static final String STREAM_KEY = "offline_notifications";

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal principal = headerAccessor.getUser();
        
        if (principal != null) {
            String username = principal.getName();
            log.info("User Connected: {}", username);
            
            // 1. Thêm user vào Redis (Online Users Set) ngay khi vừa kết nối thành công
            redisTemplate.opsForSet().add(ONLINE_USERS_KEY, username);
            
            // 2. Publish thông báo JOIN cho mọi người biết
            ChatMessage chatMessage = ChatMessage.builder()
                    .type(ChatMessage.MessageType.JOIN)
                    .sender(username)
                    .build();
            redisMessagePublisher.publish(chatMessage);
        }
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal principal = headerAccessor.getUser();

        if (principal != null) {
            String username = principal.getName();
            log.info("User Disconnected : {}", username);
            
            // 1. Remove user from Redis online users set
            redisTemplate.opsForSet().remove(ONLINE_USERS_KEY, username);

            // 2. Notify others currently online via Pub/Sub (Realtime)
            ChatMessage chatMessage = ChatMessage.builder()
                    .type(ChatMessage.MessageType.LEAVE)
                    .sender(username)
                    .build();
            redisMessagePublisher.publish(chatMessage);
            // check thêm tính năng khi mất kết nối thì xử lý như thế nào?
            // 3. Push job to Redis Stream for Offline Notification Processing
            pushOfflineNotificationToStream(username);
        }
    }
    
    private void pushOfflineNotificationToStream(String username) {
        try {
            Map<String, String> payload = new HashMap<>();
            payload.put("username", username);
            payload.put("timestamp", String.valueOf(System.currentTimeMillis()));
            payload.put("reason", "disconnected");

            MapRecord<String, String, String> record = StreamRecords.newRecord()
                    .ofMap(payload)
                    .withStreamKey(STREAM_KEY);
            
            redisTemplate.opsForStream().add(record);
            log.info("Pushed offline event for {} to Redis Stream: {}", username, STREAM_KEY);
        } catch (Exception e) {
            log.error("Failed to push offline event to Redis stream", e);
        }
    }
}
