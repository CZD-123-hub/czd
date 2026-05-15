package com.coding.assistant.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.DenseVectorIndexOptions;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.coding.assistant.config.VectorSearchConfig;
import com.coding.assistant.entity.KnowledgeChunk;
import com.coding.assistant.entity.KnowledgeDocument;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ElasticsearchVectorService {

    private final ElasticsearchClient client;
    private final VectorSearchConfig config;

    @PostConstruct
    public void init() {
        if (!config.isEnabled()) {
            log.info("Vector search disabled, skip Elasticsearch index init.");
            return;
        }
        ensureIndices();
    }

    public boolean isEnabled() {
        return config.isEnabled();
    }

    public void ensureIndices() {
        ensureDocumentIndex();
        ensureChunkIndex();
    }

    public void ensureDocumentIndex() {
        ensureIndex(config.getIndexName());
    }

    public void ensureChunkIndex() {
        ensureIndex(config.getChunkIndexName());
    }

    private void ensureIndex(String indexName) {
        try {
            boolean exists = client.indices().exists(e -> e.index(indexName)).value();
            if (exists) return;

            // 向量字段配置：
            // - dims: 必须与 EmbeddingService.VECTOR_DIM 一致（当前 512）
            // - similarity: 余弦相似度
            // - indexOptions: HNSW（近似最近邻）参数
            client.indices().create(c -> c
                    .index(indexName)
                    .mappings(m -> m
                            .properties("id", Property.of(p -> p.keyword(k -> k)))
                            .properties("title", Property.of(p -> p.text(t -> t)))
                            .properties("content", Property.of(p -> p.text(t -> t)))
                            .properties("category", Property.of(p -> p.keyword(k -> k)))
                            .properties("documentId", Property.of(p -> p.keyword(k -> k)))
                            .properties("chunkIndex", Property.of(p -> p.integer(n -> n)))
                            .properties("embedding", Property.of(p -> p
                                    .denseVector(v -> v
                                            .dims(EmbeddingService.VECTOR_DIM)
                                            .index(true)
                                            .similarity("cosine")
                                            .indexOptions(DenseVectorIndexOptions.of(io -> io
                                                    // HNSW 常见参数：m 控制图连边度，efConstruction 控制建图质量/成本
                                                    .type("hnsw")
                                                    .m(16)
                                                    .efConstruction(100)
                                            ))
                                    )))
                    )
            );
            log.info("Elasticsearch vector index created: {}", indexName);
        } catch (Exception e) {
            log.warn("Failed to ensure Elasticsearch index {}: {}", indexName, e.getMessage());
        }
    }

    public void upsertDocument(KnowledgeDocument doc, List<Double> embedding) {
        if (!isEnabled() || doc == null || doc.getId() == null || embedding == null || embedding.isEmpty()) {
            return;
        }

        try {
            DocIndexEntity entity = new DocIndexEntity();
            entity.id = String.valueOf(doc.getId());
            entity.title = safe(doc.getTitle());
            entity.content = safe(doc.getContent());
            entity.category = safe(doc.getCategory());
            entity.embedding = embedding;

            IndexRequest<DocIndexEntity> request = IndexRequest.of(i -> i
                    .index(config.getIndexName())
                    .id(entity.id)
                    .document(entity)
            );
            client.index(request);
        } catch (Exception e) {
            log.warn("Failed to upsert vector doc id={}: {}", doc.getId(), e.getMessage());
        }
    }

    public void upsertChunk(KnowledgeChunk chunk, KnowledgeDocument doc, List<Double> embedding) {
        if (!isEnabled() || chunk == null || chunk.getId() == null || embedding == null || embedding.isEmpty()) {
            return;
        }
        try {
            ChunkIndexEntity entity = new ChunkIndexEntity();
            entity.id = String.valueOf(chunk.getId());
            entity.documentId = chunk.getDocumentId() == null ? "" : String.valueOf(chunk.getDocumentId());
            entity.chunkIndex = chunk.getChunkIndex();
            entity.content = safe(chunk.getContent());
            entity.title = doc == null ? "" : safe(doc.getTitle());
            entity.category = doc == null ? "" : safe(doc.getCategory());
            entity.embedding = embedding;

            IndexRequest<ChunkIndexEntity> request = IndexRequest.of(i -> i
                    .index(config.getChunkIndexName())
                    .id(entity.id)
                    .document(entity)
            );
            client.index(request);
        } catch (Exception e) {
            log.warn("Failed to upsert vector chunk id={}: {}", chunk.getId(), e.getMessage());
        }
    }

    public List<Long> searchIds(List<Double> queryVector, int topK) {
        return searchIdsInIndex(config.getIndexName(), queryVector, topK);
    }

    public List<Long> searchChunkIds(List<Double> queryVector, int topK) {
        return searchIdsInIndex(config.getChunkIndexName(), queryVector, topK);
    }

    /**
     * KNN 向量检索：
     * 1) 用 queryVector 在 ES dense_vector 上做近邻搜索；
     * 2) 返回命中的文档/分块 ID（按相关性由高到低）。
     */
    private List<Long> searchIdsInIndex(String indexName, List<Double> queryVector, int topK) {
        if (!isEnabled() || queryVector == null || queryVector.isEmpty()) {
            return List.of();
        }

        Long k = Long.valueOf(Math.max(1, topK));
        // numCandidates 越大召回越全，但开销也更高。
        Long candidates = Long.valueOf(Math.max(k.intValue(), config.getNumCandidates()));
        List<Float> queryVectorFloat = toFloatVector(queryVector);

        try {
            SearchResponse<DocIndexEntity> response = client.search(s -> s
                            .index(indexName)
                            .knn(knn -> knn
                                    .field("embedding")
                                    .queryVector(queryVectorFloat)
                                    .k(k)
                                    .numCandidates(candidates)
                            )
                            .size(k.intValue()),
                    DocIndexEntity.class
            );

            List<Long> ids = new ArrayList<>();
            for (Hit<DocIndexEntity> hit : response.hits().hits()) {
                String id = hit.id();
                if (id == null && hit.source() != null) {
                    id = hit.source().id;
                }
                if (id == null || id.isBlank()) continue;
                try {
                    ids.add(Long.parseLong(id));
                } catch (NumberFormatException ignore) {
                    // ignore invalid id
                }
            }
            return ids;
        } catch (Exception e) {
            log.warn("Vector search failed on index={}, fallback to local search: {}", indexName, e.getMessage());
            return List.of();
        }
    }

    private List<Float> toFloatVector(List<Double> vector) {
        List<Float> result = new ArrayList<>(vector.size());
        for (Double v : vector) {
            result.add(v == null ? 0.0f : v.floatValue());
        }
        return result;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class DocIndexEntity {
        public String id;
        public String title;
        public String content;
        public String category;
        public List<Double> embedding;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class ChunkIndexEntity {
        public String id;
        public String documentId;
        public Integer chunkIndex;
        public String title;
        public String content;
        public String category;
        public List<Double> embedding;
    }
}
