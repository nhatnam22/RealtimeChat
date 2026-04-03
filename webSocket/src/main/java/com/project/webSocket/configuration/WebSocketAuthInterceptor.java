package com.project.webSocket.configuration;

import com.project.webSocket.configuration.jwt.JwtUtils;
import com.project.webSocket.service.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Objects;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtUtils jwtUtils;
    private final UserDetailsServiceImpl userDetailsService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authorizationHeader = accessor.getFirstNativeHeader("Authorization");
            String token = null;

            if (StringUtils.hasText(authorizationHeader) && authorizationHeader.startsWith("Bearer ")) {
                token = authorizationHeader.substring(7);
            } else {
                // Thử lấy token từ param gửi kèm nếu Frontend dùng SockJS không gửi được header
                String tokenParam = accessor.getFirstNativeHeader("token");
                if (StringUtils.hasText(tokenParam)) {
                    token = tokenParam;
                }
            }

            if (StringUtils.hasText(token)) {
                try {
                    if (jwtUtils.validateJwtToken(token)) {
                        String username = jwtUtils.getUserNameFromJwtToken(token);
                        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                        
                        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities());
                        
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                        accessor.setUser(authentication);
                    } else {
                         throw new IllegalArgumentException("Invalid JWT Token");
                    }
                } catch (Exception e) {
                    log.error("Failed to authenticate STOMP connection: {}", e.getMessage());
                    throw new IllegalArgumentException("Authentication Failed", e); 
                }
            } 
            // Nếu không gửi token lúc connect thì từ chối (Bạn có thể bỏ else nếu muốn cho phép nặc danh)
            else {
                 log.warn("No JWT token found in STOMP CONNECT header");
            }
        }
        return message;
    }
}
