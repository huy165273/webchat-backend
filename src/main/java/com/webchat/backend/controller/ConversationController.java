package com.webchat.backend.controller;

import com.webchat.backend.dto.ConversationDto;
import com.webchat.backend.entity.Conversation;
import com.webchat.backend.entity.User;
import com.webchat.backend.repository.ConversationRepository;
import com.webchat.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationRepository conversationRepository;
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<List<ConversationDto>> getConversations(Authentication authentication) {
        String email = authentication.getName();
        User user = userRepository.findByEmail(email).orElseThrow();

        List<ConversationDto> conversations = conversationRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(c -> ConversationDto.builder()
                        .id(c.getId())
                        .title(c.getTitle())
                        .createdAt(c.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return ResponseEntity.ok(conversations);
    }

    @PostMapping
    public ResponseEntity<ConversationDto> createConversation(Authentication authentication, @RequestBody(required = false) ConversationDto request) {
        String email = authentication.getName();
        User user = userRepository.findByEmail(email).orElseThrow();

        String title = (request != null && request.getTitle() != null) ? request.getTitle() : "New Chat";

        Conversation conversation = Conversation.builder()
                .user(user)
                .title(title)
                .build();

        Conversation saved = conversationRepository.save(conversation);

        return ResponseEntity.ok(ConversationDto.builder()
                .id(saved.getId())
                .title(saved.getTitle())
                .createdAt(saved.getCreatedAt())
                .build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ConversationDto> updateConversationTitle(@PathVariable Long id, @RequestBody ConversationDto request, Authentication authentication) {
        String email = authentication.getName();
        User user = userRepository.findByEmail(email).orElseThrow();

        Conversation conversation = conversationRepository.findById(id).orElseThrow();
        if (!conversation.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).build();
        }

        if (request != null && request.getTitle() != null && !request.getTitle().trim().isEmpty()) {
            conversation.setTitle(request.getTitle().trim());
            conversation = conversationRepository.save(conversation);
        }

        return ResponseEntity.ok(ConversationDto.builder()
                .id(conversation.getId())
                .title(conversation.getTitle())
                .createdAt(conversation.getCreatedAt())
                .build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteConversation(@PathVariable Long id, Authentication authentication) {
        String email = authentication.getName();
        User user = userRepository.findByEmail(email).orElseThrow();

        Conversation conversation = conversationRepository.findById(id).orElseThrow();
        if (!conversation.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).build();
        }

        conversationRepository.delete(conversation);
        return ResponseEntity.ok().build();
    }
}
