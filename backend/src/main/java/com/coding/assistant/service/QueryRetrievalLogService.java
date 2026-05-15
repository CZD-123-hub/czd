package com.coding.assistant.service;

import com.coding.assistant.dto.EdgeVO;
import com.coding.assistant.dto.KnowledgeNodeVO;
import com.coding.assistant.entity.KnowledgeDocument;
import com.coding.assistant.entity.QueryLog;
import com.coding.assistant.entity.RetrievalLog;
import com.coding.assistant.mapper.QueryLogMapper;
import com.coding.assistant.mapper.RetrievalLogMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueryRetrievalLogService {

    private final QueryLogMapper queryLogMapper;
    private final RetrievalLogMapper retrievalLogMapper;
    private final ElasticsearchChatLogService elasticsearchChatLogService;
    private final ObjectMapper objectMapper;

    /**
     * 命中 QA 缓存时记录检索日志：
     * 1) 先从 sourcesJson 反解每条证据；
     * 2) 再写入 query_log（状态=cache_hit）；
     * 3) 最后批量写 retrieval_log，并同步到 ES。
     */
    public void logCacheHit(Long userId,
                            Long conversationId,
                            Long userMessageId,
                            Long assistantMessageId,
                            String queryText,
                            String queryHash,
                            String sourcesJson) {
        try {
            List<RetrievalLog> retrievalLogs = buildLogsFromSourcesJson(
                    userId,
                    conversationId,
                    assistantMessageId,
                    sourcesJson
            );

            QueryLog queryLog = QueryLog.builder()
                    .userId(userId)
                    .conversationId(conversationId)
                    .userMessageId(userMessageId)
                    .assistantMessageId(assistantMessageId)
                    .queryText(queryText)
                    .queryHash(queryHash)
                    .expandedTerms("[]")
                    .retrievalStatus("cache_hit")
                    .sourceCount(retrievalLogs.size())
                    .createdAt(LocalDateTime.now())
                    .build();
            queryLogMapper.insert(queryLog);

            persistRetrievalLogs(queryLog.getId(), retrievalLogs);
            elasticsearchChatLogService.indexQueryLog(queryLog);
            elasticsearchChatLogService.indexRetrievalLogs(retrievalLogs);
        } catch (Exception e) {
            log.warn("Failed to persist cache-hit logs conversationId={}: {}", conversationId, e.getMessage());
        }
    }

    /**
     * 走完整检索链路时记录检索日志：
     * 1) 从 RetrievalResult 构建证据明细；
     * 2) 写 query_log（状态=retrieved）；
     * 3) 写 retrieval_log，并同步到 ES。
     */
    public void logRetrieved(Long userId,
                             Long conversationId,
                             Long userMessageId,
                             Long assistantMessageId,
                             String queryText,
                             String queryHash,
                             KnowledgeRetrievalService.RetrievalResult retrieval) {
        try {
            List<RetrievalLog> retrievalLogs = buildLogsFromRetrievalResult(
                    userId,
                    conversationId,
                    assistantMessageId,
                    retrieval
            );

            QueryLog queryLog = QueryLog.builder()
                    .userId(userId)
                    .conversationId(conversationId)
                    .userMessageId(userMessageId)
                    .assistantMessageId(assistantMessageId)
                    .queryText(queryText)
                    .queryHash(queryHash)
                    .expandedTerms(toJsonSafe(retrieval == null ? List.of() : retrieval.expandedTerms()))
                    .retrievalStatus("retrieved")
                    .sourceCount(retrievalLogs.size())
                    .createdAt(LocalDateTime.now())
                    .build();
            queryLogMapper.insert(queryLog);

            persistRetrievalLogs(queryLog.getId(), retrievalLogs);
            elasticsearchChatLogService.indexQueryLog(queryLog);
            elasticsearchChatLogService.indexRetrievalLogs(retrievalLogs);
        } catch (Exception e) {
            log.warn("Failed to persist retrieval logs conversationId={}: {}", conversationId, e.getMessage());
        }
    }

    /**
     * 将每条证据挂上 queryLogId 后入库 retrieval_log。
     */
    private void persistRetrievalLogs(Long queryLogId, List<RetrievalLog> retrievalLogs) {
        if (queryLogId == null || retrievalLogs == null || retrievalLogs.isEmpty()) {
            return;
        }
        for (RetrievalLog logItem : retrievalLogs) {
            logItem.setQueryLogId(queryLogId);
            retrievalLogMapper.insert(logItem);
        }
    }

    /**
     * 把检索结果对象拆解为可落库的证据日志（文档/节点/关系）。
     */
    private List<RetrievalLog> buildLogsFromRetrievalResult(Long userId,
                                                            Long conversationId,
                                                            Long assistantMessageId,
                                                            KnowledgeRetrievalService.RetrievalResult retrieval) {
        if (retrieval == null) {
            return List.of();
        }

        List<RetrievalLog> logs = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        // 文档证据：记录文档标题、摘要、分数、命中分块索引等信息
        for (KnowledgeDocument doc : retrieval.documents()) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("category", doc.getCategory());
            logs.add(RetrievalLog.builder()
                    .userId(userId)
                    .conversationId(conversationId)
                    .assistantMessageId(assistantMessageId)
                    .sourceType("document")
                    .sourceId(doc.getId() == null ? "" : String.valueOf(doc.getId()))
                    .sourceTitle(trim(doc.getTitle(), 255))
                    .excerpt(trim(doc.getContent(), 1000))
                    .score(doc.getHitScore())
                    .chunkIndex(doc.getHitChunkIndex())
                    .metadata(toJsonSafe(metadata))
                    .createdAt(now)
                    .build());
        }

        // 图节点证据：记录节点名称、描述、分类与关键词
        for (KnowledgeNodeVO node : retrieval.nodes()) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("category", node.getCategory());
            metadata.put("keywords", node.getKeywords() == null ? List.of() : node.getKeywords());
            logs.add(RetrievalLog.builder()
                    .userId(userId)
                    .conversationId(conversationId)
                    .assistantMessageId(assistantMessageId)
                    .sourceType("node")
                    .sourceId(node.getId() == null ? "" : node.getId())
                    .sourceTitle(trim(node.getName(), 255))
                    .excerpt(trim(node.getDescription(), 1000))
                    .metadata(toJsonSafe(metadata))
                    .createdAt(now)
                    .build());
        }

        // 图关系证据：记录 source/target/relationType，便于后续追溯
        for (EdgeVO edge : retrieval.edges()) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("source", edge.getSource());
            metadata.put("target", edge.getTarget());
            metadata.put("relationType", edge.getType());
            logs.add(RetrievalLog.builder()
                    .userId(userId)
                    .conversationId(conversationId)
                    .assistantMessageId(assistantMessageId)
                    .sourceType("relation")
                    .sourceId(buildRelationId(edge))
                    .sourceTitle(buildRelationId(edge))
                    .excerpt("Graph relation evidence")
                    .metadata(toJsonSafe(metadata))
                    .createdAt(now)
                    .build());
        }

        return logs;
    }

    /**
     * 从 assistant 消息里保存的 sources JSON 反序列化为 retrieval_log 明细。
     */
    private List<RetrievalLog> buildLogsFromSourcesJson(Long userId,
                                                        Long conversationId,
                                                        Long assistantMessageId,
                                                        String sourcesJson) {
        if (sourcesJson == null || sourcesJson.isBlank()) {
            return List.of();
        }

        List<RetrievalLog> logs = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        try {
            JsonNode root = objectMapper.readTree(sourcesJson);
            if (!root.isArray()) {
                return List.of();
            }
            for (JsonNode item : root) {
                if (!item.isObject()) {
                    continue;
                }
                String type = text(item, "type");
                String sourceId = text(item, "id");
                String title = text(item, "title");
                String excerpt = text(item, "excerpt");
                Integer chunkIndex = intOrNull(item, "chunkIndex");
                Double score = doubleOrNull(item, "score");

                Map<String, Object> metadata = objectMapper.convertValue(
                        item,
                        new TypeReference<Map<String, Object>>() {}
                );
                logs.add(RetrievalLog.builder()
                        .userId(userId)
                        .conversationId(conversationId)
                        .assistantMessageId(assistantMessageId)
                        .sourceType(type.isBlank() ? "document" : type)
                        .sourceId(trim(sourceId, 255))
                        .sourceTitle(trim(title, 255))
                        .excerpt(trim(excerpt, 1000))
                        .chunkIndex(chunkIndex)
                        .score(score)
                        .metadata(toJsonSafe(metadata))
                        .createdAt(now)
                        .build());
            }
        } catch (Exception e) {
            log.warn("Failed to parse sourcesJson for retrieval log: {}", e.getMessage());
        }
        return logs;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText("");
    }

    private Integer intOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isInt() || value.isLong()) {
            return value.asInt();
        }
        try {
            return Integer.parseInt(value.asText());
        } catch (Exception ignore) {
            return null;
        }
    }

    private Double doubleOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isFloat() || value.isDouble() || value.isInt() || value.isLong()) {
            return value.asDouble();
        }
        try {
            return Double.parseDouble(value.asText());
        } catch (Exception ignore) {
            return null;
        }
    }

    private String buildRelationId(EdgeVO edge) {
        return (edge.getSource() == null ? "" : edge.getSource())
                + "->"
                + (edge.getType() == null ? "" : edge.getType())
                + "->"
                + (edge.getTarget() == null ? "" : edge.getTarget());
    }

    private String toJsonSafe(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private String trim(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxLen) {
            return normalized;
        }
        return normalized.substring(0, maxLen) + "...";
    }
}
