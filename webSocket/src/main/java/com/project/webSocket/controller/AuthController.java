package com.project.webSocket.controller;

import com.project.webSocket.configuration.jwt.JwtUtils;
import com.project.webSocket.models.User;
import com.project.webSocket.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody User user) {
        if (userService.existsByUsername(user.getUsername())) {
            return ResponseEntity.badRequest().body("Username is already taken");
        }
        
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        userService.saveUser(user);
        
        return ResponseEntity.ok("User registered successfully");
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody User loginRequest) {
        User user = userService.findByUsername(loginRequest.getUsername());
        
        if (user == null || !passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())) {
            return ResponseEntity.status(401).body("Invalid username or password");
        }
        
        // Authenticate manually for simplicity, though typically done via AuthenticationManager
        Authentication authentication = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        
        // Generate real JWT token containing username and email in payload
        String jwt = jwtUtils.generateJwtToken(authentication);
        
        return ResponseEntity.ok(jwt);
    }
}
