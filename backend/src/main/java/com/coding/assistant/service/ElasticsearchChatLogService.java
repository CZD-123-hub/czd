package com.coding.assistant.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import com.coding.assistant.config.VectorSearchConfig;
import com.coding.assistant.entity.QueryLog;
import com.coding.assistant.entity.RetrievalLog;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ElasticsearchChatLogService {

    private static final DateTimeFormatter ES_DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final ElasticsearchClient client;
    private final VectorSearchConfig config;

    @PostConstruct
    public void init() {
        if (!isEnabled()) {
            log.info("Query/retrieval Elasticsearch logging disabled.");
            return;
        }
        ensureIndices();
    }

    public boolean isEnabled() {
        return config.isEnabled() && config.isQueryLogEnabled();
    }

    public void ensureIndices() {
        ensureQueryLogIndex();
        ensureRetrievalLogIndex();
    }

    public void indexQueryLog(QueryLog queryLog) {
        if (!isEnabled() || queryLog == null || queryLog.getId() == null) {
            return;
        }
        try {
            client.index(i -> i
                    .index(config.getQueryLogIndexName())
                    .id(String.valueOf(queryLog.getId()))
                    .document(toQueryLogDoc(queryLog))
            );
        } catch (Exception e) {
            log.warn("Failed to index query log index={}, id={}, errorType={}, message={}",
                    config.getQueryLogIndexName(),
                    queryLog.getId(),
                    e.getClass().getSimpleName(),
                    e.getMessage());
        }
    }

    public void indexRetrievalLogs(List<RetrievalLog> logs) {
        if (!isEnabled() || logs == null || logs.isEmpty()) {
            return;
        }
        for (RetrievalLog logItem : logs) {
            if (logItem == null || logItem.getId() == null) {
                continue;
            }
            try {
                client.index(i -> i
                        .index(config.getRetrievalLogIndexName())
                        .id(String.valueOf(logItem.getId()))
                        .document(toRetrievalLogDoc(logItem))
                );
            } catch (Exception e) {
                log.warn("Failed to index retrieval log index={}, id={}, errorType={}, message={}",
                        config.getRetrievalLogIndexName(),
                        logItem.getId(),
                        e.getClass().getSimpleName(),
                        e.getMessage());
            }
        }
    }

    private void ensureQueryLogIndex() {
        ensureIndex(config.getQueryLogIndexName(), true);
    }

    private void ensureRetrievalLogIndex() {
        ensureIndex(config.getRetrievalLogIndexName(), false);
    }

    private void ensureIndex(String indexName, boolean queryLogIndex) {
        try {
            boolean exists = client.indices().exists(e -> e.index(indexName)).value();
            if (exists) {
                return;
            }

            client.indices().create(c -> c
                    .index(indexName)
                    .mappings(m -> {
                        if (queryLogIndex) {
                            return m.properties("id", Property.of(p -> p.keyword(k -> k)))
                                    .properties("userId", Property.of(p -> p.long_(n -> n)))
                                    .properties("conversationId", Property.of(p -> p.long_(n -> n)))
                                    .properties("userMessageId", Property.of(p -> p.long_(n -> n)))
                                    .properties("assistantMessageId", Property.of(p -> p.long_(n -> n)))
                                    .properties("queryText", Property.of(p -> p.text(t -> t)))
                                    .properties("queryHash", Property.of(p -> p.keyword(k -> k)))
                                    .properties("expandedTerms", Property.of(p -> p.text(t -> t)))
                                    .properties("retrievalStatus", Property.of(p -> p.keyword(k -> k)))
                                    .properties("sourceCount", Property.of(p -> p.integer(n -> n)))
                                    .properties("createdAt", Property.of(p -> p.date(d -> d)));
                        }
                        return m.properties("id", Property.of(p -> p.keyword(k -> k)))
                                .properties("queryLogId", Property.of(p -> p.long_(n -> n)))
                                .properties("userId", Property.of(p -> p.long_(n -> n)))
                                .properties("conversationId", Property.of(p -> p.long_(n -> n)))
                                .properties("assistantMessageId", Property.of(p -> p.long_(n -> n)))
                                .properties("sourceType", Property.of(p -> p.keyword(k -> k)))
                                .properties("sourceId", Property.of(p -> p.keyword(k -> k)))
                                .properties("sourceTitle", Property.of(p -> p.text(t -> t)))
                                .properties("excerpt", Property.of(p -> p.text(t -> t)))
                                .properties("score", Property.of(p -> p.double_(n -> n)))
                                .properties("chunkIndex", Property.of(p -> p.integer(n -> n)))
                                .properties("metadata", Property.of(p -> p.text(t -> t)))
                                .properties("createdAt", Property.of(p -> p.date(d -> d)));
                    })
            );
            log.info("Elasticsearch chat log index created: {}", indexName);
        } catch (Exception e) {
            log.warn("Failed to ensure Elasticsearch chat log index {}: {}", indexName, e.getMessage());
        }
    }

    private Map<String, Object> toQueryLogDoc(QueryLog queryLog) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("id", String.valueOf(queryLog.getId()));
        doc.put("userId", queryLog.getUserId());
        doc.put("conversationId", queryLog.getConversationId());
        doc.put("userMessageId", queryLog.getUserMessageId());
        doc.put("assistantMessageId", queryLog.getAssistantMessageId());
        doc.put("queryText", queryLog.getQueryText());
        doc.put("queryHash", queryLog.getQueryHash());
        doc.put("expandedTerms", queryLog.getExpandedTerms());
        doc.put("retrievalStatus", queryLog.getRetrievalStatus());
        doc.put("sourceCount", queryLog.getSourceCount());
        doc.put("createdAt", formatDateTime(queryLog.getCreatedAt()));
        return doc;
    }

    private Map<String, Object> toRetrievalLogDoc(RetrievalLog retrievalLog) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("id", String.valueOf(retrievalLog.getId()));
        doc.put("queryLogId", retrievalLog.getQueryLogId());
        doc.put("userId", retrievalLog.getUserId());
        doc.put("conversationId", retrievalLog.getConversationId());
        doc.put("assistantMessageId", retrievalLog.getAssistantMessageId());
        doc.put("sourceType", retrievalLog.getSourceType());
        doc.put("sourceId", retrievalLog.getSourceId());
        doc.put("sourceTitle", retrievalLog.getSourceTitle());
        doc.put("excerpt", retrievalLog.getExcerpt());
        doc.put("score", retrievalLog.getScore());
        doc.put("chunkIndex", retrievalLog.getChunkIndex());
        doc.put("metadata", retrievalLog.getMetadata());
        doc.put("createdAt", formatDateTime(retrievalLog.getCreatedAt()));
        return doc;
    }

    private String formatDateTime(LocalDateTime time) {
        return time == null ? null : time.format(ES_DATE_TIME_FORMATTER);
    }
}

