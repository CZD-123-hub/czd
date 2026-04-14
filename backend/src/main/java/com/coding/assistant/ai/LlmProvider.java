package com.coding.assistant.ai;

import reactor.core.publisher.Flux;

import java.util.List;

public interface LlmProvider {

    /**
     * Stream chat completion responses
     */
    Flux<String> chatStream(List<ChatMessage> messages, String systemPrompt);

    /**
     * Synchronous chat completion
     */
    String chat(List<ChatMessage> messages, String systemPrompt);
}