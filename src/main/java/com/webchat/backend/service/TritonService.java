package com.webchat.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class TritonService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final ExecutorService executorService;
    private final String tritonUrl;

    public TritonService(@Value("${ai.triton.url}") String tritonUrl) {
        this.restClient = RestClient.builder().build();
        this.objectMapper = new ObjectMapper();
        this.executorService = Executors.newVirtualThreadPerTaskExecutor(); // Java 21 Virtual Threads
        this.tritonUrl = tritonUrl;
    }

    public void streamChat(List<Map<String, String>> messages, Float temperature, Integer maxTokens, SseEmitter emitter, Runnable onComplete) {
        executorService.submit(() -> {
            try {
                Map<String, Object> requestBody = Map.of(
                        "model", "gpt-oss-20b",
                        "messages", messages,
                        "temperature", temperature != null ? temperature : 0.7f,
                        "max_tokens", maxTokens != null ? maxTokens : 2048,
                        "stream", true
                );

                restClient.post()
                        .uri(tritonUrl)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .body(requestBody)
                        .exchange((clientRequest, clientResponse) -> {
                            if (!clientResponse.getStatusCode().is2xxSuccessful()) {
                                emitter.completeWithError(new RuntimeException("Triton API Error: " + clientResponse.getStatusCode()));
                                return null;
                            }

                            try (InputStream body = clientResponse.getBody();
                                 BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
                                
                                String line;
                                while ((line = reader.readLine()) != null) {
                                    if (line.isBlank()) continue;
                                    if (line.startsWith("data: ")) {
                                        String data = line.substring(6);
                                        if (data.equals("[DONE]")) {
                                            break;
                                        }
                                        
                                        try {
                                            JsonNode node = objectMapper.readTree(data);
                                            JsonNode delta = node.path("choices").path(0).path("delta");
                                            if (delta.has("content")) {
                                                String content = delta.get("content").asText();
                                                emitter.send(content);
                                            }
                                        } catch (Exception e) {
                                            // Ignore parsing errors for partial chunks or non-json lines
                                        }
                                    }
                                }
                            }
                            return null;
                        });

                onComplete.run();
                emitter.complete();

            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });
    }
}
