package com.webchat.backend.controller;

import com.webchat.backend.dto.UserProfileRequest;
import com.webchat.backend.dto.UserSettingsRequest;
import com.webchat.backend.entity.User;
import com.webchat.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentUser(Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName()).orElseThrow();
        return ResponseEntity.ok(Map.of(
                "id", user.getId(),
                "name", user.getName(),
                "email", user.getEmail(),
                "temperature", user.getTemperature(),
                "maxTokens", user.getMaxTokens()
        ));
    }

    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@RequestBody UserProfileRequest request, Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName()).orElseThrow();

        if (request.getName() != null && !request.getName().trim().isEmpty()) {
            user.setName(request.getName().trim());
        }

        if (request.getEmail() != null && !request.getEmail().trim().isEmpty() && !request.getEmail().equals(user.getEmail())) {
            Optional<User> existing = userRepository.findByEmail(request.getEmail().trim());
            if (existing.isPresent()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Email already in use"));
            }
            user.setEmail(request.getEmail().trim());
        }

        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }

        userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "Profile updated successfully"));
    }

    @PutMapping("/settings")
    public ResponseEntity<?> updateSettings(@RequestBody UserSettingsRequest request, Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName()).orElseThrow();

        if (request.getTemperature() != null) {
            user.setTemperature(request.getTemperature());
        }
        if (request.getMaxTokens() != null) {
            user.setMaxTokens(request.getMaxTokens());
        }

        userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "Settings updated successfully"));
    }
}
