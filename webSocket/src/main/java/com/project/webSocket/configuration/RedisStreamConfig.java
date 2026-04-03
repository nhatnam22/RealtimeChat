package com.project.webSocket.configuration;

import com.project.webSocket.service.OfflineNotificationStreamListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.Subscription;

import java.time.Duration;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class RedisStreamConfig {

    private final RedisConnectionFactory redisConnectionFactory;
    private final OfflineNotificationStreamListener streamListener;
    private final RedisTemplate<String, Object> redisTemplate;

    public static final String STREAM_KEY = "offline_notifications";
    public static final String CONSUMER_GROUP = "notification_group";
    public static final String CONSUMER_NAME = "notification_worker_1";

    @Bean
    public Subscription subscription() {
        createConsumerGroupIfNotExists();

        StreamMessageListenerContainer.StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
                StreamMessageListenerContainer.StreamMessageListenerContainerOptions
                        .builder()
                        .pollTimeout(Duration.ofSeconds(1))
                        .build();

        StreamMessageListenerContainer<String, MapRecord<String, String, String>> container =
                StreamMessageListenerContainer.create(redisConnectionFactory, options);

        Subscription subscription = container.receive(
                Consumer.from(CONSUMER_GROUP, CONSUMER_NAME),
                StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed()),
                streamListener
        );

        container.start();
        return subscription;
    }

    private void createConsumerGroupIfNotExists() {
        try {
            redisTemplate.opsForStream().createGroup(STREAM_KEY, ReadOffset.from("0-0"), CONSUMER_GROUP);
            log.info("Created consumer group {} for stream {}", CONSUMER_GROUP, STREAM_KEY);
        } catch (Exception e) {
            // Group might already exist, which is fine
            log.info("Consumer group {} might already exist for stream {}. Message: {}", CONSUMER_GROUP, STREAM_KEY, e.getMessage());
        }
    }
}
