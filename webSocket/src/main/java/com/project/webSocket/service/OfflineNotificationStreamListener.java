package com.project.webSocket.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class OfflineNotificationStreamListener implements StreamListener<String, MapRecord<String, String, String>> {

    private final RedisTemplate<String, Object> redisTemplate;
    private final UserService userService; // Inject UserService để cập nhật DB
    
    public static final String STREAM_KEY = "offline_notifications";
    public static final String CONSUMER_GROUP = "notification_group";

    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        try {
            Map<String, String> payload = message.getValue();
            String offlineUser = payload.get("username");
            String timestampStr = payload.get("timestamp");
            long timestampMillis = Long.parseLong(timestampStr);

            log.info("Processing offline event from stream. User: {}, Disconnect Time: {}", offlineUser, timestampStr);

            // 1. THỰC THI KỊCH BẢN: CẬP NHẬT LAST SEEN VÀO DATABASE
            Date lastSeenTime = new Date(timestampMillis);
            userService.updateLastSeen(offlineUser, lastSeenTime);
            
            // TODO: (Có thể thêm kịch bản gửi Push Notification ở đây nếu cần)

            // 2. Acknowledge (ACK) the message so it's not processed again
            redisTemplate.opsForStream().acknowledge(STREAM_KEY, CONSUMER_GROUP, message.getId());
            log.info("Successfully updated database and acknowledged message ID: {}", message.getId());

        } catch (Exception e) {
            log.error("Error processing stream message", e);
            // In case of error, DO NOT acknowledge. It will remain in Pending Entries List (PEL) for retry.
        }
    }
}
