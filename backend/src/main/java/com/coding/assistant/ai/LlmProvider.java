package com.coding.assistant.ai;

import reactor.core.publisher.Flux;

import java.util.List;

public interface LlmProvider {

    // 异步聊天完成，响应流式传输
    Flux<String> chatStream(List<ChatMessage> messages, String systemPrompt);

    // 同步聊天完成，返回完整响应
    String chat(List<ChatMessage> messages, String systemPrompt);
}