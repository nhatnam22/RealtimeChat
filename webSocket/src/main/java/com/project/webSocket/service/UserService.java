package com.project.webSocket.service;

import com.project.webSocket.models.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class UserService {

    // Using an in-memory map for demonstration.
    // In a real application, replace this with a Spring Data JPA Repository (e.g., UserRepository)
    private final Map<String, User> userRepository = new HashMap<>();

    public void saveUser(User user) {
        user.setId(UUID.randomUUID().toString());
        userRepository.put(user.getUsername(), user);
    }

    public User findByUsername(String username) {
        return userRepository.get(username);
    }

    public boolean existsByUsername(String username) {
        return userRepository.containsKey(username);
    }
    
    // Phương thức mới để cập nhật lastSeen
    public void updateLastSeen(String username, Date lastSeenTime) {
        User user = userRepository.get(username);
        if (user != null) {
            user.setLastSeen(lastSeenTime);
            log.info("Updated lastSeen for user {}: {}", username, lastSeenTime);
            // Trong thực tế, bạn sẽ gọi userRepository.save(user) nếu dùng JPA
        } else {
            log.warn("Cannot update lastSeen. User not found: {}", username);
        }
    }
}
