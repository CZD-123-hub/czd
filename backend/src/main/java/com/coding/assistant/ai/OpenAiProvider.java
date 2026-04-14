package com.coding.assistant.ai;

import com.coding.assistant.config.LlmConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component("openaiProvider")
@ConditionalOnProperty(name = "llm.openai.api-key")
public class OpenAiProvider implements LlmProvider {

    private final WebClient webClient;
    private final LlmConfig.ProviderConfig config;
    private final ObjectMapper objectMapper;

    public OpenAiProvider(LlmConfig llmConfig, ObjectMapper objectMapper) {
        this.config = llmConfig.getOpenai();
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .baseUrl(config.getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + config.getApiKey())
                .defaultHeader("Content-Type", "application/json")
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
    }

    @Override
    public Flux<String> chatStream(List<ChatMessage> messages, String systemPrompt) {
        ObjectNode requestBody = buildRequestBody(messages, systemPrompt, true);

        return webClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody.toString())
                .retrieve()
                .bodyToFlux(String.class)
                .filter(data -> !data.equals("[DONE]"))
                .map(data -> {
                    try {
                        JsonNode node = objectMapper.readTree(data);
                        JsonNode delta = node.path("choices").path(0).path("delta").path("content");
                        return delta.isMissingNode() ? "" : delta.asText();
                    } catch (Exception e) {
                        log.error("Error parsing SSE data: {}", e.getMessage());
                        return "";
                    }
                })
                .filter(s -> !s.isEmpty());
    }

    @Override
    public String chat(List<ChatMessage> messages, String systemPrompt) {
        ObjectNode requestBody = buildRequestBody(messages, systemPrompt, false);

        String responseBody = webClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody.toString())
                .retrieve()
                .bodyToMono(String.class)
                .block();

        try {
            JsonNode node = objectMapper.readTree(responseBody);
            return node.path("choices").path(0).path("message").path("content").asText();
        } catch (Exception e) {
            log.error("Error parsing chat response: {}", e.getMessage());
            return "Sorry, an error occurred while processing your request.";
        }
    }

    private ObjectNode buildRequestBody(List<ChatMessage> messages, String systemPrompt, boolean stream) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", config.getModel());
        root.put("stream", stream);

        ArrayNode messagesArray = root.putArray("messages");

        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            ObjectNode systemMsg = objectMapper.createObjectNode();
            systemMsg.put("role", "system");
            systemMsg.put("content", systemPrompt);
            messagesArray.add(systemMsg);
        }

        for (ChatMessage msg : messages) {
            ObjectNode msgNode = objectMapper.createObjectNode();
            msgNode.put("role", msg.getRole());
            msgNode.put("content", msg.getContent());
            messagesArray.add(msgNode);
        }

        return root;
    }
}