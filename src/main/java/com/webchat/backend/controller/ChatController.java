package com.webchat.backend.controller;

import com.webchat.backend.dto.ChatRequest;
import com.webchat.backend.dto.MessageDto;
import com.webchat.backend.entity.Conversation;
import com.webchat.backend.entity.Message;
import com.webchat.backend.entity.User;
import com.webchat.backend.repository.ConversationRepository;
import com.webchat.backend.repository.MessageRepository;
import com.webchat.backend.repository.UserRepository;
import com.webchat.backend.service.TritonService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final TritonService tritonService;

    @GetMapping("/{conversationId}/messages")
    public ResponseEntity<List<MessageDto>> getMessages(@PathVariable Long conversationId, Authentication authentication) {
        verifyOwnership(conversationId, authentication);

        List<MessageDto> messages = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId)
                .stream()
                .map(m -> MessageDto.builder()
                        .id(m.getId())
                        .role(m.getRole())
                        .content(m.getContent())
                        .createdAt(m.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return ResponseEntity.ok(messages);
    }

    @PostMapping(value = "/{conversationId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@PathVariable Long conversationId, @RequestBody ChatRequest request, Authentication authentication) {
        Conversation conversation = verifyOwnership(conversationId, authentication);

        // Save User Message
        Message userMessage = Message.builder()
                .conversation(conversation)
                .role("user")
                .content(request.getMessage())
                .build();
        messageRepository.save(userMessage);

        // Fetch History for Context
        List<Message> history = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
        List<Map<String, String>> messagesForAi = new ArrayList<>();
        
        // System prompt (optional)
        messagesForAi.add(Map.of("role", "system", "content", "You are a helpful AI assistant."));
        
        for (Message m : history) {
            messagesForAi.add(Map.of("role", m.getRole(), "content", m.getContent()));
        }

        // Accumulate AI response to save to DB when stream completes
        return handleStreaming(messagesForAi, conversation, conversation.getUser().getTemperature(), conversation.getUser().getMaxTokens());
    }
    
    private SseEmitter handleStreaming(List<Map<String, String>> messagesForAi, Conversation conversation, Float temperature, Integer maxTokens) {
        SseEmitter emitter = new SseEmitter(180_000L);
        StringBuilder aiResponseBuilder = new StringBuilder();

        tritonService.streamChat(messagesForAi, temperature, maxTokens, new SseEmitter(180_000L) {
            @Override
            public void send(Object object) throws java.io.IOException {
                if (object instanceof String) {
                    aiResponseBuilder.append(object);
                }
                emitter.send(object);
            }
            
            @Override
            public void send(SseEventBuilder builder) throws java.io.IOException {
                emitter.send(builder);
            }

            @Override
            public void complete() {
                Message aiMessage = Message.builder()
                        .conversation(conversation)
                        .role("assistant")
                        .content(aiResponseBuilder.toString())
                        .build();
                messageRepository.save(aiMessage);
                emitter.complete();
            }

            @Override
            public void completeWithError(Throwable ex) {
                emitter.completeWithError(ex);
            }
        }, () -> {});
        
        return emitter;
    }

    private Conversation verifyOwnership(Long conversationId, Authentication authentication) {
        String email = authentication.getName();
        User user = userRepository.findByEmail(email).orElseThrow();

        Conversation conversation = conversationRepository.findById(conversationId).orElseThrow();
        if (!conversation.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Unauthorized");
        }
        return conversation;
    }
}
