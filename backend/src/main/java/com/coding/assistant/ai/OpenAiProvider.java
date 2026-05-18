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
// chatStream 方法：实现了 LlmProvider 接口的 chatStream 方法，接受用户消息列表和系统提示词，构建请求体并发送到 OpenAI 的 Chat Completions API，返回一个 Flux 流，流中的每个元素是模型生成的文本增量。
    @Override
    public Flux<String> chatStream(List<ChatMessage> messages, String systemPrompt) {
        ObjectNode requestBody = buildRequestBody(messages, systemPrompt, true);
        // 发送 POST 请求到 OpenAI 的 Chat Completions API，设置请求体为构建好的 JSON，接收流式响应并解析增量文本内容，过滤掉结束标志 "[DONE]"，最终返回一个 Flux 流供调用方订阅处理。
        return webClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody.toString())
                .retrieve()
                .bodyToFlux(String.class)// 接收 SSE 流式响应，每个元素是一个 JSON 字符串，包含增量文本内容或结束标志。
                .filter(data -> !data.equals("[DONE]"))
                .map(data -> {// 解析 SSE 数据，提取增量文本内容，构建成一个字符串返回。数据格式为 JSON，包含 choices 数组，每个 choice 包含 delta 对象，delta 对象包含 content 字段，即增量文本内容。
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
// chat 方法：实现了 LlmProvider 接口的 chat 方法，接受用户消息列表和系统提示词，构建请求体并发送到 OpenAI 的 Chat Completions API，等待完整响应并解析返回的文本内容，最终返回模型生成的完整回答字符串。
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