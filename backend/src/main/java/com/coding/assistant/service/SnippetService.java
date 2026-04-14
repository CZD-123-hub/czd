package com.coding.assistant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.coding.assistant.dto.*;
import com.coding.assistant.entity.CodeSnippet;
import com.coding.assistant.entity.Message;
import com.coding.assistant.exception.BusinessException;
import com.coding.assistant.mapper.CodeSnippetMapper;
import com.coding.assistant.mapper.MessageMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SnippetService {

    private final CodeSnippetMapper codeSnippetMapper;
    private final MessageMapper messageMapper;
    private final ObjectMapper objectMapper;

    @Transactional
    public SnippetVO create(Long userId, SnippetRequest request) {
        String tagsJson = null;
        if (request.getTags() != null && !request.getTags().isEmpty()) {
            try {
                tagsJson = objectMapper.writeValueAsString(request.getTags());
            } catch (JsonProcessingException e) {
                log.error("Error serializing tags: {}", e.getMessage());
            }
        }

        CodeSnippet snippet = CodeSnippet.builder()
                .userId(userId)
                .title(request.getTitle())
                .code(request.getCode())
                .language(request.getLanguage())
                .description(request.getDescription())
                .tags(tagsJson)
                .useCount(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        codeSnippetMapper.insert(snippet);

        log.info("Code snippet created: id={}, userId={}", snippet.getId(), userId);
        return toSnippetVO(snippet);
    }

    public PageResult<SnippetVO> list(Long userId, SnippetQueryRequest request) {
        Page<CodeSnippet> page = new Page<>(request.getPage(), request.getSize());
        Page<CodeSnippet> result = (Page<CodeSnippet>) codeSnippetMapper.selectByUserIdWithFilter(
                page, userId, request.getKeyword(), request.getTag());

        List<SnippetVO> records = result.getRecords().stream()
                .map(this::toSnippetVO)
                .collect(Collectors.toList());

        return PageResult.<SnippetVO>builder()
                .records(records)
                .total(result.getTotal())
                .page(request.getPage())
                .size(request.getSize())
                .build();
    }

    public List<SnippetVO> recommend(Long userId, Long conversationId) {
        // Get conversation keywords from messages
        List<Message> messages = messageMapper.selectByConversationIdOrderByCreatedAt(conversationId);
        if (messages.isEmpty()) {
            return List.of();
        }

        // Extract keywords from user messages
        String combinedContent = messages.stream()
                .filter(m -> "user".equals(m.getRole()))
                .map(Message::getContent)
                .collect(Collectors.joining(" "));

        List<String> keywords = extractKeywords(combinedContent);

        // Get user's snippets
        List<CodeSnippet> allSnippets = codeSnippetMapper.selectList(
                new LambdaQueryWrapper<CodeSnippet>()
                        .eq(CodeSnippet::getUserId, userId)
        );

        // Score and sort snippets by relevance
        return allSnippets.stream()
                .map(snippet -> {
                    int score = calculateRelevanceScore(snippet, keywords);
                    return new Object[]{snippet, score};
                })
                .filter(pair -> (int) pair[1] > 0)
                .sorted((a, b) -> {
                    int scoreCompare = Integer.compare((int) b[1], (int) a[1]);
                    if (scoreCompare != 0) return scoreCompare;
                    return Integer.compare(
                            ((CodeSnippet) b[0]).getUseCount(),
                            ((CodeSnippet) a[0]).getUseCount()
                    );
                })
                .limit(10)
                .map(pair -> toSnippetVO((CodeSnippet) pair[0]))
                .collect(Collectors.toList());
    }

    @Transactional
    public SnippetVO update(Long userId, Long id, SnippetRequest request) {
        CodeSnippet snippet = codeSnippetMapper.selectById(id);
        if (snippet == null || !snippet.getUserId().equals(userId)) {
            throw new BusinessException(404, "Snippet not found");
        }

        snippet.setTitle(request.getTitle());
        snippet.setCode(request.getCode());
        snippet.setLanguage(request.getLanguage());
        snippet.setDescription(request.getDescription());
        snippet.setUpdatedAt(LocalDateTime.now());

        if (request.getTags() != null) {
            try {
                snippet.setTags(objectMapper.writeValueAsString(request.getTags()));
            } catch (JsonProcessingException e) {
                log.error("Error serializing tags: {}", e.getMessage());
            }
        }

        codeSnippetMapper.updateById(snippet);
        log.info("Code snippet updated: id={}", id);
        return toSnippetVO(snippet);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        CodeSnippet snippet = codeSnippetMapper.selectById(id);
        if (snippet == null || !snippet.getUserId().equals(userId)) {
            throw new BusinessException(404, "Snippet not found");
        }

        codeSnippetMapper.deleteById(id);
        log.info("Code snippet deleted: id={}, userId={}", id, userId);
    }

    public String exportAll(Long userId) {
        List<CodeSnippet> snippets = codeSnippetMapper.selectList(
                new LambdaQueryWrapper<CodeSnippet>()
                        .eq(CodeSnippet::getUserId, userId)
                        .orderByDesc(CodeSnippet::getCreatedAt)
        );

        List<SnippetVO> vos = snippets.stream()
                .map(this::toSnippetVO)
                .collect(Collectors.toList());

        try {
            return objectMapper.writeValueAsString(vos);
        } catch (JsonProcessingException e) {
            log.error("Error exporting snippets: {}", e.getMessage());
            throw new BusinessException(500, "Failed to export snippets");
        }
    }

    @Transactional
    public ImportResultVO importFromJson(Long userId, String json) {
        int successCount = 0;
        int failCount = 0;
        List<String> errors = new ArrayList<>();

        try {
            List<SnippetRequest> requests = objectMapper.readValue(json,
                    new TypeReference<List<SnippetRequest>>() {});

            for (int i = 0; i < requests.size(); i++) {
                SnippetRequest request = requests.get(i);
                try {
                    if (request.getTitle() == null || request.getTitle().isBlank()) {
                        throw new IllegalArgumentException("Title is required");
                    }
                    if (request.getCode() == null || request.getCode().isBlank()) {
                        throw new IllegalArgumentException("Code is required");
                    }
                    if (request.getLanguage() == null || request.getLanguage().isBlank()) {
                        throw new IllegalArgumentException("Language is required");
                    }

                    create(userId, request);
                    successCount++;
                } catch (Exception e) {
                    failCount++;
                    errors.add("Item " + (i + 1) + ": " + e.getMessage());
                }
            }
        } catch (JsonProcessingException e) {
            throw new BusinessException(400, "Invalid JSON format: " + e.getMessage());
        }

        log.info("Snippet import completed for user {}: success={}, fail={}", userId, successCount, failCount);

        return ImportResultVO.builder()
                .successCount(successCount)
                .failCount(failCount)
                .errors(errors)
                .build();
    }

    private SnippetVO toSnippetVO(CodeSnippet snippet) {
        List<String> tags = new ArrayList<>();
        if (snippet.getTags() != null && !snippet.getTags().isEmpty()) {
            try {
                tags = objectMapper.readValue(snippet.getTags(),
                        new TypeReference<List<String>>() {});
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse tags for snippet {}: {}", snippet.getId(), e.getMessage());
            }
        }

        return SnippetVO.builder()
                .id(snippet.getId())
                .title(snippet.getTitle())
                .code(snippet.getCode())
                .language(snippet.getLanguage())
                .description(snippet.getDescription())
                .tags(tags)
                .useCount(snippet.getUseCount())
                .createdAt(snippet.getCreatedAt())
                .updatedAt(snippet.getUpdatedAt())
                .build();
    }

    private List<String> extractKeywords(String content) {
        String cleaned = content.replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fff\\s]", " ");
        return Arrays.stream(cleaned.split("\\s+"))
                .filter(word -> word.length() >= 2)
                .map(String::toLowerCase)
                .distinct()
                .collect(Collectors.toList());
    }

    private int calculateRelevanceScore(CodeSnippet snippet, List<String> keywords) {
        int score = 0;
        String title = snippet.getTitle() != null ? snippet.getTitle().toLowerCase() : "";
        String description = snippet.getDescription() != null ? snippet.getDescription().toLowerCase() : "";
        String tags = snippet.getTags() != null ? snippet.getTags().toLowerCase() : "";

        for (String keyword : keywords) {
            if (title.contains(keyword)) score += 3;
            if (tags.contains(keyword)) score += 2;
            if (description.contains(keyword)) score += 1;
        }
        return score;
    }
}