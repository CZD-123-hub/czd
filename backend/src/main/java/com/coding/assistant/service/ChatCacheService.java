package com.coding.assistant.service;

import com.coding.assistant.ai.ChatMessage;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatCacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final Duration QA_TTL = Duration.ofHours(6);
    private static final Duration CONTEXT_TTL = Duration.ofMinutes(30);

    private String qaKey(String questionHash) {
        return "chat:qa:" + questionHash;
    }

    private String contextKey(Long conversationId) {
        return "chat:ctx:" + conversationId;
    }

    public Optional<QaCacheEntry> getCachedAnswer(String questionHash) {
        try {
            Object val = redisTemplate.opsForValue().get(qaKey(questionHash));
            if (val instanceof QaCacheEntry entry) {
                return Optional.of(entry);
            }
        } catch (Exception e) {
            log.warn("Redis getCachedAnswer failed: {}", e.getMessage());
        }
        return Optional.empty();
    }

    public void cacheAnswer(String questionHash, String answer, String sourcesJson) {
        try {
            QaCacheEntry entry = new QaCacheEntry(answer, sourcesJson);
            redisTemplate.opsForValue().set(qaKey(questionHash), entry, QA_TTL);
        } catch (Exception e) {
            log.warn("Redis cacheAnswer failed: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public Optional<List<ChatMessage>> getConversationContext(Long conversationId) {
        try {
            Object val = redisTemplate.opsForValue().get(contextKey(conversationId));
            if (val instanceof List<?> list) {
                try {
                    return Optional.of((List<ChatMessage>) list);
                } catch (ClassCastException ignored) {
                    return Optional.empty();
                }
            }
        } catch (Exception e) {
            log.warn("Redis getConversationContext failed: {}", e.getMessage());
        }
        return Optional.empty();
    }

    public void saveConversationContext(Long conversationId, List<ChatMessage> context) {
        try {
            redisTemplate.opsForValue().set(contextKey(conversationId), context, CONTEXT_TTL);
        } catch (Exception e) {
            log.warn("Redis saveConversationContext failed: {}", e.getMessage());
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QaCacheEntry {
        private String answer;
        private String sourcesJson;
    }
}
