package com.coding.assistant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.coding.assistant.dto.ImportResultVO;
import com.coding.assistant.dto.PageResult;
import com.coding.assistant.dto.SnippetQueryRequest;
import com.coding.assistant.dto.SnippetRequest;
import com.coding.assistant.dto.SnippetVO;
import com.coding.assistant.entity.CodeSnippet;
import com.coding.assistant.entity.Conversation;
import com.coding.assistant.entity.Message;
import com.coding.assistant.exception.BusinessException;
import com.coding.assistant.exception.ErrorCode;
import com.coding.assistant.mapper.CodeSnippetMapper;
import com.coding.assistant.mapper.ConversationMapper;
import com.coding.assistant.mapper.MessageMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class SnippetService {

    private static final Pattern EN_TOKEN_PATTERN = Pattern.compile("[a-zA-Z][a-zA-Z0-9_+#.-]{1,31}");
    private static final Pattern CJK_SEGMENT_PATTERN = Pattern.compile("[\\p{IsHan}]{2,}");
    private static final int MAX_KEYWORDS = 48;
    private static final int MAX_CONTEXT_MESSAGES = 6;
    private static final int USEFUL_FEEDBACK_WEIGHT = 8;
    private static final int USELESS_FEEDBACK_WEIGHT = -10;

    private final CodeSnippetMapper codeSnippetMapper;
    private final MessageMapper messageMapper;
    private final ConversationMapper conversationMapper;
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, Object> redisTemplate;

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
                page,
                userId,
                request.getKeyword(),
                request.getTag(),
                request.getLanguage()
        );

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
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null || !conversation.getUserId().equals(userId)) {
            throw new BusinessException(
                    ErrorCode.NOT_FOUND,
                    ErrorCode.BIZ_CONVERSATION_NOT_FOUND,
                    "Conversation not found"
            );
        }

        List<Message> messages = messageMapper.selectByConversationIdOrderByCreatedAt(conversationId);
        if (messages.isEmpty()) {
            return List.of();
        }

        List<String> userContents = messages.stream()
                .filter(m -> "user".equals(m.getRole()))
                .map(Message::getContent)
                .filter(content -> content != null && !content.isBlank())
                .collect(Collectors.toList());

        String combinedContent = userContents.stream()
                .skip(Math.max(0, userContents.size() - MAX_CONTEXT_MESSAGES))
                .collect(Collectors.joining(" "));
        if (combinedContent.isBlank()) {
            combinedContent = messages.stream()
                    .map(Message::getContent)
                    .filter(content -> content != null && !content.isBlank())
                    .collect(Collectors.joining(" "));
        }
        final String contextText = combinedContent;

        List<String> keywords = extractKeywords(contextText);
        String languageHint = detectLanguageHint(contextText);

        List<CodeSnippet> allSnippets = codeSnippetMapper.selectList(
                new LambdaQueryWrapper<CodeSnippet>().eq(CodeSnippet::getUserId, userId)
        );
        if (allSnippets.isEmpty()) {
            return List.of();
        }

        int maxUseCount = allSnippets.stream()
                .map(CodeSnippet::getUseCount)
                .filter(v -> v != null && v > 0)
                .max(Integer::compareTo)
                .orElse(0);
        Map<Long, Integer> feedbackScores = loadFeedbackScores(userId, allSnippets);
        LocalDateTime now = LocalDateTime.now();

        List<RecommendationCandidate> ranked = allSnippets.stream()
                .map(snippet -> buildRecommendation(
                        snippet,
                        keywords,
                        languageHint,
                        contextText,
                        maxUseCount,
                        now,
                        feedbackScores.getOrDefault(snippet.getId(), 0)
                ))
                .filter(candidate -> candidate.score() > 0)
                .sorted((a, b) -> {
                    int scoreCompare = Integer.compare(b.score(), a.score());
                    if (scoreCompare != 0) {
                        return scoreCompare;
                    }
                    int bUse = b.snippet().getUseCount() == null ? 0 : b.snippet().getUseCount();
                    int aUse = a.snippet().getUseCount() == null ? 0 : a.snippet().getUseCount();
                    return Integer.compare(bUse, aUse);
                })
                .toList();

        if (ranked.isEmpty()) {
            return fallbackRecommendByUsage(allSnippets);
        }

        return ranked.stream()
                .limit(10)
                .map(candidate -> {
                    SnippetVO vo = toSnippetVO(candidate.snippet());
                    vo.setMatchScore(candidate.score());
                    vo.setRecommendReason(candidate.reason());
                    return vo;
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public SnippetVO update(Long userId, Long id, SnippetRequest request) {
        CodeSnippet snippet = codeSnippetMapper.selectById(id);
        if (snippet == null || !snippet.getUserId().equals(userId)) {
            throw new BusinessException(
                    ErrorCode.NOT_FOUND,
                    ErrorCode.BIZ_SNIPPET_NOT_FOUND,
                    "Snippet not found"
            );
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
            throw new BusinessException(
                    ErrorCode.NOT_FOUND,
                    ErrorCode.BIZ_SNIPPET_NOT_FOUND,
                    "Snippet not found"
            );
        }

        codeSnippetMapper.deleteById(id);
        log.info("Code snippet deleted: id={}, userId={}", id, userId);
    }

    @Transactional
    public SnippetVO markUsed(Long userId, Long id) {
        int affectedRows = codeSnippetMapper.incrementUseCount(id, userId);
        if (affectedRows == 0) {
            throw new BusinessException(
                    ErrorCode.NOT_FOUND,
                    ErrorCode.BIZ_SNIPPET_NOT_FOUND,
                    "Snippet not found"
            );
        }

        CodeSnippet snippet = codeSnippetMapper.selectById(id);
        if (snippet == null || !snippet.getUserId().equals(userId)) {
            throw new BusinessException(
                    ErrorCode.NOT_FOUND,
                    ErrorCode.BIZ_SNIPPET_NOT_FOUND,
                    "Snippet not found"
            );
        }
        return toSnippetVO(snippet);
    }

    public SnippetVO feedback(Long userId, Long id, String rating) {
        CodeSnippet snippet = codeSnippetMapper.selectById(id);
        if (snippet == null || !snippet.getUserId().equals(userId)) {
            throw new BusinessException(
                    ErrorCode.NOT_FOUND,
                    ErrorCode.BIZ_SNIPPET_NOT_FOUND,
                    "Snippet not found"
            );
        }

        String normalizedRating = safeLower(rating);
        if (!"useful".equals(normalizedRating) && !"useless".equals(normalizedRating)) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    ErrorCode.BIZ_BAD_REQUEST,
                    "Unsupported snippet feedback rating"
            );
        }

        saveRecommendationFeedback(userId, id, normalizedRating);
        return toSnippetVO(snippet);
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
            throw new BusinessException(
                    ErrorCode.INTERNAL_SERVER_ERROR,
                    ErrorCode.BIZ_EXPORT_FAILED,
                    "Failed to export snippets"
            );
        }
    }

    @Transactional
    public ImportResultVO importFromJson(Long userId, String json) {
        int successCount = 0;
        int failCount = 0;
        List<String> errors = new ArrayList<>();

        try {
            List<SnippetRequest> requests = objectMapper.readValue(
                    json,
                    new TypeReference<List<SnippetRequest>>() {
                    }
            );

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
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    ErrorCode.BIZ_BAD_REQUEST,
                    "Invalid JSON format: " + e.getMessage()
            );
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
                tags = objectMapper.readValue(snippet.getTags(), new TypeReference<List<String>>() {
                });
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
                .matchScore(null)
                .recommendReason(null)
                .createdAt(snippet.getCreatedAt())
                .updatedAt(snippet.getUpdatedAt())
                .build();
    }

    private List<String> extractKeywords(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        Set<String> keywords = new HashSet<>();
        String lower = content.toLowerCase(Locale.ROOT);

        Matcher enMatcher = EN_TOKEN_PATTERN.matcher(lower);
        while (enMatcher.find()) {
            keywords.add(enMatcher.group());
            if (keywords.size() >= MAX_KEYWORDS) {
                return new ArrayList<>(keywords);
            }
        }

        Matcher cjkMatcher = CJK_SEGMENT_PATTERN.matcher(content);
        while (cjkMatcher.find()) {
            String segment = cjkMatcher.group();
            if (segment.length() >= 2) {
                keywords.add(segment);
            }
            for (int i = 0; i < segment.length() - 1; i++) {
                keywords.add(segment.substring(i, i + 2));
            }
            for (int i = 0; i < segment.length() - 2; i++) {
                keywords.add(segment.substring(i, i + 3));
            }
            if (keywords.size() >= MAX_KEYWORDS) {
                break;
            }
        }

        return keywords.stream()
                .filter(token -> token.length() >= 2)
                .limit(MAX_KEYWORDS)
                .collect(Collectors.toList());
    }

    private RecommendationCandidate buildRecommendation(
            CodeSnippet snippet,
            List<String> keywords,
            String languageHint,
            String userContext,
            int maxUseCount,
            LocalDateTime now,
            int feedbackScore
    ) {
        List<String> parsedTags = parseTagList(snippet.getTags());
        String combinedText = buildSnippetCombinedText(snippet, parsedTags);

        double keywordScore = calculateKeywordScore(snippet, parsedTags, keywords);
        double semanticScore = calculateSemanticScore(userContext, combinedText);
        double usageScore = calculateUsageScore(snippet.getUseCount(), maxUseCount);
        double recencyScore = calculateRecencyScore(snippet.getUpdatedAt(), now);

        boolean languageMatched = false;
        if (languageHint != null && !languageHint.isBlank()) {
            String snippetLanguage = safeLower(snippet.getLanguage());
            languageMatched = snippetLanguage.contains(languageHint);
        }

        double totalScore = keywordScore * 45.0
                + semanticScore * 30.0
                + usageScore * 15.0
                + recencyScore * 10.0
                + (languageMatched ? 6.0 : 0.0)
                + feedbackScore;

        List<String> reasons = new ArrayList<>();
        if (keywordScore >= 0.35) {
            reasons.add("High keyword match");
        } else if (keywordScore >= 0.18) {
            reasons.add("Partial keyword match");
        }

        if (semanticScore >= 0.30) {
            reasons.add("Strong semantic relevance");
        } else if (semanticScore >= 0.15) {
            reasons.add("Moderate semantic relevance");
        }

        if (languageMatched && snippet.getLanguage() != null && !snippet.getLanguage().isBlank()) {
            reasons.add("Language preference: " + snippet.getLanguage());
        }

        int useCount = snippet.getUseCount() == null ? 0 : snippet.getUseCount();
        if (useCount >= 3) {
            reasons.add("Used " + useCount + " times");
        }

        if (recencyScore >= 0.65) {
            reasons.add("Recently updated");
        }

        if (feedbackScore > 0) {
            reasons.add("User marked useful before");
        } else if (feedbackScore < 0) {
            reasons.add("Previously marked not suitable");
        }

        if (reasons.isEmpty()) {
            reasons.add("Potentially relevant to current context");
        }

        int roundedScore = Math.max(1, (int) Math.round(totalScore));
        return new RecommendationCandidate(snippet, roundedScore, String.join(" / ", reasons));
    }

    private Map<Long, Integer> loadFeedbackScores(Long userId, List<CodeSnippet> snippets) {
        Map<Long, Integer> scores = new HashMap<>();
        if (userId == null || snippets == null || snippets.isEmpty()) {
            return scores;
        }

        String key = feedbackScoreKey(userId);
        try {
            for (CodeSnippet snippet : snippets) {
                if (snippet.getId() == null) continue;
                Object raw = redisTemplate.opsForHash().get(key, String.valueOf(snippet.getId()));
                scores.put(snippet.getId(), parseFeedbackScore(raw));
            }
        } catch (Exception e) {
            log.warn("Failed to load snippet recommendation feedback: {}", e.getMessage());
        }
        return scores;
    }

    private void saveRecommendationFeedback(Long userId, Long snippetId, String rating) {
        String lastKey = feedbackLastKey(userId);
        String scoreKey = feedbackScoreKey(userId);
        String field = String.valueOf(snippetId);

        try {
            Object oldRatingRaw = redisTemplate.opsForHash().get(lastKey, field);
            String oldRating = oldRatingRaw == null ? "" : String.valueOf(oldRatingRaw);
            int delta = ratingWeight(rating) - ratingWeight(oldRating);
            if (delta != 0) {
                redisTemplate.opsForHash().increment(scoreKey, field, delta);
            }
            redisTemplate.opsForHash().put(lastKey, field, rating);
        } catch (Exception e) {
            log.warn("Failed to save snippet recommendation feedback: {}", e.getMessage());
        }
    }

    private int ratingWeight(String rating) {
        if ("useful".equals(rating)) {
            return USEFUL_FEEDBACK_WEIGHT;
        }
        if ("useless".equals(rating)) {
            return USELESS_FEEDBACK_WEIGHT;
        }
        return 0;
    }

    private int parseFeedbackScore(Object raw) {
        if (raw == null) {
            return 0;
        }
        if (raw instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(raw));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private String feedbackScoreKey(Long userId) {
        return "snippet:recommend:score:" + userId;
    }

    private String feedbackLastKey(Long userId) {
        return "snippet:recommend:last:" + userId;
    }

    private double calculateKeywordScore(CodeSnippet snippet, List<String> parsedTags, List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return 0.0;
        }

        String title = safeLower(snippet.getTitle());
        String description = safeLower(snippet.getDescription());
        String language = safeLower(snippet.getLanguage());
        String code = safeLower(snippet.getCode());
        String tagText = safeLower(String.join(" ", parsedTags));

        double raw = 0.0;
        for (String keyword : keywords) {
            String normalized = safeLower(keyword);
            if (normalized.isBlank()) continue;
            if (title.contains(normalized)) raw += 1.7;
            if (tagText.contains(normalized)) raw += 1.3;
            if (description.contains(normalized)) raw += 1.0;
            if (language.contains(normalized)) raw += 0.9;
            if (code.contains(normalized)) raw += 0.6;
        }

        double denominator = Math.max(4.0, keywords.size() * 2.6);
        return Math.min(1.0, raw / denominator);
    }

    private double calculateSemanticScore(String query, String target) {
        if (query == null || query.isBlank() || target == null || target.isBlank()) {
            return 0.0;
        }

        Set<String> queryTokens = tokenizeSemantic(query);
        Set<String> targetTokens = tokenizeSemantic(target);
        if (queryTokens.isEmpty() || targetTokens.isEmpty()) {
            return 0.0;
        }

        int intersection = 0;
        for (String token : queryTokens) {
            if (targetTokens.contains(token)) {
                intersection++;
            }
        }

        int union = queryTokens.size() + targetTokens.size() - intersection;
        double jaccard = union == 0 ? 0.0 : (double) intersection / union;
        double ngramCosine = cosineSimilarity(buildCharNgramVector(query), buildCharNgramVector(target));

        return Math.min(1.0, jaccard * 0.45 + ngramCosine * 0.55);
    }

    private double calculateUsageScore(Integer useCount, int maxUseCount) {
        int usage = useCount == null ? 0 : Math.max(0, useCount);
        if (usage <= 0 || maxUseCount <= 0) {
            return 0.0;
        }
        return Math.min(1.0, Math.log1p(usage) / Math.log1p(maxUseCount));
    }

    private double calculateRecencyScore(LocalDateTime updatedAt, LocalDateTime now) {
        if (updatedAt == null || now == null) {
            return 0.0;
        }
        long days = Math.max(0, ChronoUnit.DAYS.between(updatedAt, now));
        return Math.exp(-days / 30.0);
    }

    private List<String> parseTagList(String tagsJson) {
        if (tagsJson == null || tagsJson.isBlank()) {
            return List.of();
        }
        try {
            List<String> tags = objectMapper.readValue(tagsJson, new TypeReference<List<String>>() {
            });
            return tags == null ? List.of() : tags;
        } catch (Exception e) {
            return List.of();
        }
    }

    private String buildSnippetCombinedText(CodeSnippet snippet, List<String> parsedTags) {
        return String.join(" ",
                safeText(snippet.getTitle()),
                safeText(snippet.getDescription()),
                safeText(snippet.getLanguage()),
                String.join(" ", parsedTags),
                safeText(snippet.getCode())
        );
    }

    private Set<String> tokenizeSemantic(String text) {
        Set<String> result = new HashSet<>();
        if (text == null || text.isBlank()) {
            return result;
        }

        String lower = text.toLowerCase(Locale.ROOT);
        Matcher enMatcher = EN_TOKEN_PATTERN.matcher(lower);
        while (enMatcher.find()) {
            result.add(enMatcher.group());
        }

        Matcher cjkMatcher = CJK_SEGMENT_PATTERN.matcher(text);
        while (cjkMatcher.find()) {
            String segment = cjkMatcher.group();
            if (segment.length() >= 2) {
                result.add(segment);
            }
            for (int i = 0; i < segment.length() - 1; i++) {
                result.add(segment.substring(i, i + 2));
            }
        }

        return result;
    }

    private Map<String, Integer> buildCharNgramVector(String rawText) {
        Map<String, Integer> vector = new HashMap<>();
        if (rawText == null || rawText.isBlank()) {
            return vector;
        }

        String normalized = rawText
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .replaceAll("[^a-z0-9\\p{IsHan}]+", " ")
                .trim();

        if (normalized.length() < 2) {
            return vector;
        }

        int n = normalized.length() < 3 ? 2 : 3;
        for (int i = 0; i <= normalized.length() - n; i++) {
            String gram = normalized.substring(i, i + n);
            if (gram.isBlank()) continue;
            vector.merge(gram, 1, Integer::sum);
        }
        return vector;
    }

    private double cosineSimilarity(Map<String, Integer> left, Map<String, Integer> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return 0.0;
        }

        double dot = 0.0;
        for (Map.Entry<String, Integer> entry : left.entrySet()) {
            Integer other = right.get(entry.getKey());
            if (other != null) {
                dot += entry.getValue() * other;
            }
        }

        double leftNorm = 0.0;
        for (int value : left.values()) {
            leftNorm += value * value;
        }

        double rightNorm = 0.0;
        for (int value : right.values()) {
            rightNorm += value * value;
        }

        if (leftNorm <= 0 || rightNorm <= 0) {
            return 0.0;
        }

        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private List<SnippetVO> fallbackRecommendByUsage(List<CodeSnippet> snippets) {
        return snippets.stream()
                .sorted(Comparator
                        .comparing((CodeSnippet item) -> item.getUseCount() == null ? 0 : item.getUseCount())
                        .reversed()
                        .thenComparing((CodeSnippet item) -> item.getUpdatedAt() == null ? LocalDateTime.MIN : item.getUpdatedAt(), Comparator.reverseOrder()))
                .limit(6)
                .map(snippet -> {
                    SnippetVO vo = toSnippetVO(snippet);
                    vo.setMatchScore(Math.max(1, (snippet.getUseCount() == null ? 0 : snippet.getUseCount())));
                    vo.setRecommendReason("Reusable high-frequency snippet (low context match)");
                    return vo;
                })
                .collect(Collectors.toList());
    }

    private String safeText(String text) {
        return text == null ? "" : text.trim();
    }

    private String safeLower(String text) {
        return safeText(text).toLowerCase(Locale.ROOT);
    }
    private String detectLanguageHint(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String lower = content.toLowerCase(Locale.ROOT);
        if (lower.contains("typescript") || lower.contains("ts") || lower.contains("vue") || lower.contains("react")) {
            return "typescript";
        }
        if (lower.contains("javascript") || lower.contains("js")) {
            return "javascript";
        }
        if (lower.contains("python")) {
            return "python";
        }
        if (lower.contains("java") || lower.contains("spring")) {
            return "java";
        }
        if (lower.contains("mysql") || lower.contains("sql")) {
            return "sql";
        }
        if (lower.contains("docker")) {
            return "docker";
        }
        return "";
    }

    private record RecommendationCandidate(CodeSnippet snippet, int score, String reason) {
    }
}
