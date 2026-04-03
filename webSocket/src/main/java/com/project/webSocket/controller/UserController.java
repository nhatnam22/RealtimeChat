package com.project.webSocket.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final RedisTemplate<String, Object> redisTemplate;
    private static final String ONLINE_USERS_KEY = "online_users";

    @GetMapping("/online")
    public ResponseEntity<Set<Object>> getOnlineUsers() {
        Set<Object> onlineUsers = redisTemplate.opsForSet().members(ONLINE_USERS_KEY);
        return ResponseEntity.ok(onlineUsers);
    }
}
