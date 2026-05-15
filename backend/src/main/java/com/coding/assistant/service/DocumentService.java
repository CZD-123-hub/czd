package com.coding.assistant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.coding.assistant.dto.KnowledgeDocumentVO;
import com.coding.assistant.dto.RelatedGraphNodeVO;
import com.coding.assistant.entity.KnowledgeChunk;
import com.coding.assistant.entity.KnowledgeDocument;
import com.coding.assistant.entity.KnowledgeDocumentFavorite;
import com.coding.assistant.exception.BusinessException;
import com.coding.assistant.exception.ErrorCode;
import com.coding.assistant.mapper.KnowledgeChunkMapper;
import com.coding.assistant.mapper.KnowledgeDocumentFavoriteMapper;
import com.coding.assistant.mapper.KnowledgeDocumentMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Values;
import org.neo4j.driver.types.Node;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private static final int CHUNK_SIZE = 500;
    private static final int CHUNK_OVERLAP = 120;

    private final KnowledgeDocumentMapper documentMapper;
    private final Driver neo4jDriver;
    private final KnowledgeChunkMapper chunkMapper;
    private final KnowledgeDocumentFavoriteMapper knowledgeDocumentFavoriteMapper;
    private final EmbeddingService embeddingService;
    private final ObjectMapper objectMapper;
    private final QueryRewriteService queryRewriteService;
    private final ElasticsearchVectorService elasticsearchVectorService;
    private final RetrievalMetricsService retrievalMetricsService;

    /** 应用启动后初始化语料统计，并在开启 ES 向量检索时预热文档/分块索引。 */
    @PostConstruct
    public void initCorpus() {
        try {
            List<KnowledgeDocument> all = documentMapper.selectList(
                    new LambdaQueryWrapper<KnowledgeDocument>()
                            .select(KnowledgeDocument::getId,
                                    KnowledgeDocument::getTitle,
                                    KnowledgeDocument::getContent,
                                    KnowledgeDocument::getCategory,
                                    KnowledgeDocument::getEmbedding)
            );
            for (KnowledgeDocument doc : all) {
                embeddingService.updateCorpus(doc.getTitle() + "\n" + doc.getContent());
            }
            log.info("TF-IDF corpus initialized with {} documents", all.size());

            if (elasticsearchVectorService.isEnabled()) {
                elasticsearchVectorService.ensureIndices();
                warmDocumentVectors(all);
                warmChunkVectors(all);
            }
        } catch (Exception e) {
            log.warn("Failed to init corpus: {}", e.getMessage());
        }
    }

    /**
     * 新增知识文档：写入文档、生成 embedding、建立 chunk 与向量索引。
     */
    public KnowledgeDocument addDocument(String title, String content, String category) {
        // 先更新语料统计（DF/总文档数），供后续 TF-IDF 权重计算使用。
        embeddingService.updateCorpus(title + "\n" + content);
        KnowledgeDocument doc = KnowledgeDocument.builder()
                .title(title)
                .content(content)
                .category(category)
                .createdAt(LocalDateTime.now())
                .build();

        // 文档级 embedding：用于文档向量检索与本地相似度兜底。
        List<Double> vector = embeddingService.embed(title + "\n" + content);
        if (!vector.isEmpty()) {
            try {
                doc.setEmbedding(objectMapper.writeValueAsString(vector));
            } catch (Exception e) {
                log.error("Failed to serialize embedding: {}", e.getMessage());
            }
        }

        documentMapper.insert(doc);
        if (!vector.isEmpty()) {
            elasticsearchVectorService.upsertDocument(doc, vector);
        }

        // 同时构建 chunk 级 embedding（更细粒度检索）。
        upsertChunksForDocument(doc);
        return doc;
    }

    /** 混合检索：语义召回 + 关键词召回，并做分数融合后排序。 */
    public List<KnowledgeDocument> search(String query, int topK) {
        long startNs = System.nanoTime();
        List<String> expandedTerms = queryRewriteService.expandTerms(query);
        List<KnowledgeDocument> semanticDocs = searchSemantic(query, Math.max(topK * 2, 6));
        List<KnowledgeDocument> keywordDocs = searchByKeywords(expandedTerms, Math.max(topK * 2, 6));

        Map<Long, ScoredDocument> merged = new LinkedHashMap<>();

        // 语义侧打分：向量相似度 + 排名加权
        for (int i = 0; i < semanticDocs.size(); i++) {
            KnowledgeDocument doc = semanticDocs.get(i);
            double score = scoreSemantic(query, doc) + rankBonus(i);
            merged.merge(doc.getId(), new ScoredDocument(copyDocument(doc), score),
                    (left, right) -> left.score >= right.score ? left : right);
        }

        // 关键词侧打分：标题/分类/正文匹配加权 + 排名加权
        for (int i = 0; i < keywordDocs.size(); i++) {
            KnowledgeDocument doc = keywordDocs.get(i);
            double score = scoreKeyword(doc, expandedTerms) + rankBonus(i);
            merged.merge(doc.getId(), new ScoredDocument(copyDocument(doc), score),
                    (left, right) -> {
                        left.score += right.score;
                        return left;
                    });
        }

        List<KnowledgeDocument> result = merged.values().stream()
                .sorted(Comparator.comparingDouble((ScoredDocument d) -> d.score).reversed())
                .limit(topK)
                .map(scored -> scored.document)
                .collect(Collectors.toList());

        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
        retrievalMetricsService.recordSummary(semanticDocs.size(), keywordDocs.size(), result.size(), elapsedMs);
        log.info("RAG_RETRIEVE_SUMMARY query='{}' topK={} expandedTerms={} semanticHits={} keywordHits={} finalHits={} elapsedMs={} hitDocs={}",
                trimLog(query, 80),
                topK,
                expandedTerms.size(),
                semanticDocs.size(),
                keywordDocs.size(),
                result.size(),
                elapsedMs,
                summarizeHits(result));

        return result;
    }

    /**
     * 语义检索主入口：
     * 优先走 chunk 级召回（引用更精确），若失败则回退文档向量检索，再回退本地 embedding 打分。
     * 返回仍是文档对象，但 content 会替换为命中的 chunk 摘要。
     */
    public List<KnowledgeDocument> searchSemantic(String query, int topK) {
        long startNs = System.nanoTime();
        // 查询文本先向量化，后续用于向量召回与余弦打分。
        List<Double> queryVector = embeddingService.embed(query);
        if (queryVector.isEmpty()) {
            return List.of();
        }

        List<String> expandedTerms = queryRewriteService.expandTerms(query);
        List<KnowledgeDocument> byChunk = searchSemanticByChunk(queryVector, expandedTerms, topK);
        if (!byChunk.isEmpty()) {
            logSemanticTrace("chunk", query, topK, byChunk, startNs);
            return byChunk;
        }

        // 回退 1：文档级向量检索（ES）
        List<Long> ids = elasticsearchVectorService.searchIds(queryVector, topK);
        if (!ids.isEmpty()) {
            List<KnowledgeDocument> docs = documentMapper.selectBatchIds(ids);
            Map<Long, KnowledgeDocument> byId = docs.stream()
                    .collect(Collectors.toMap(KnowledgeDocument::getId, d -> d, (a, b) -> a));
            List<KnowledgeDocument> result = ids.stream()
                    .map(byId::get)
                    .filter(d -> d != null)
                    .map(this::copyDocument)
                    .limit(topK)
                    .collect(Collectors.toList());
            logSemanticTrace("doc_vector_fallback", query, topK, result, startNs);
            return result;
        }

        // 回退 2：本地 embedding 相似度计算（兜底）
        List<KnowledgeDocument> docs = documentMapper.selectAllWithEmbedding();

        List<KnowledgeDocument> result = docs.stream()
                .map(doc -> new ScoredDocument(copyDocument(doc), scoreSemantic(queryVector, doc)))
                .filter(doc -> doc.score > 0.08)
                .sorted(Comparator.comparingDouble((ScoredDocument d) -> d.score).reversed())
                .limit(topK)
                .map(scored -> scored.document)
                .collect(Collectors.toList());
        logSemanticTrace("local_tfidf_fallback", query, topK, result, startNs);
        return result;
    }

    /**
     * chunk 级语义检索：
     * 1) 先查 ES chunk 向量候选；2) 用语义+关键词重排；3) 按文档去重取 topK。
     */
    private List<KnowledgeDocument> searchSemanticByChunk(List<Double> queryVector, List<String> expandedTerms, int topK) {
        int candidateSize = Math.max(topK * 4, 16);

        List<Long> chunkIds = elasticsearchVectorService.searchChunkIds(queryVector, candidateSize);
        List<ScoredChunk> scoredChunks;

        if (!chunkIds.isEmpty()) {
            List<KnowledgeChunk> selected = chunkMapper.selectBatchIds(chunkIds);
            Map<Long, KnowledgeChunk> byId = selected.stream()
                    .collect(Collectors.toMap(KnowledgeChunk::getId, c -> c, (a, b) -> a));
            scoredChunks = new ArrayList<>();
            for (int i = 0; i < chunkIds.size(); i++) {
                KnowledgeChunk chunk = byId.get(chunkIds.get(i));
                if (chunk == null) {
                    continue;
                }
                // 重排分数：语义为主，关键词为辅，再叠加排名奖励
                double semantic = scoreChunkSemantic(queryVector, chunk);
                double keyword = scoreChunkKeyword(chunk, expandedTerms);
                double rerankScore = semantic * 0.7 + keyword * 0.3 + rankBonus(i) * 0.2;
                scoredChunks.add(new ScoredChunk(chunk, rerankScore));
            }
        } else {
            List<KnowledgeChunk> all = chunkMapper.selectAllWithEmbedding();
            scoredChunks = all.stream()
                    .map(chunk -> {
                        double semantic = scoreChunkSemantic(queryVector, chunk);
                        double keyword = scoreChunkKeyword(chunk, expandedTerms);
                        double rerankScore = semantic * 0.7 + keyword * 0.3;
                        return new ScoredChunk(chunk, rerankScore);
                    })
                    .filter(sc -> sc.score > 0.08)
                    .sorted(Comparator.comparingDouble((ScoredChunk c) -> c.score).reversed())
                    .limit(candidateSize)
                    .collect(Collectors.toList());
        }

        if (scoredChunks.isEmpty()) {
            return List.of();
        }

        scoredChunks.sort(Comparator.comparingDouble((ScoredChunk c) -> c.score).reversed());

        Map<Long, KnowledgeDocument> docMap = new LinkedHashMap<>();
        for (ScoredChunk scoredChunk : scoredChunks) {
            KnowledgeChunk chunk = scoredChunk.chunk;
            Long docId = chunk.getDocumentId();
            if (docId == null || docMap.containsKey(docId)) {
                continue;
            }
            KnowledgeDocument doc = documentMapper.selectById(docId);
            if (doc == null) {
                continue;
            }
            // 保留命中 chunk 的文本与索引，便于回答引用更“贴片段”
            KnowledgeDocument copy = copyDocument(doc);
            copy.setContent(trimChunkContent(chunk.getContent(), 420));
            copy.setHitChunkIndex(chunk.getChunkIndex());
            copy.setHitScore(scoredChunk.score);
            docMap.put(docId, copy);
            if (docMap.size() >= topK) {
                break;
            }
        }

        return new ArrayList<>(docMap.values());
    }

    public List<KnowledgeDocument> searchByKeywords(List<String> keywords, int topK) {
        if (keywords == null || keywords.isEmpty()) {
            return List.of();
        }

        List<KnowledgeDocument> docs = documentMapper.selectList(
                new LambdaQueryWrapper<KnowledgeDocument>()
                        .select(KnowledgeDocument::getId,
                                KnowledgeDocument::getTitle,
                                KnowledgeDocument::getContent,
                                KnowledgeDocument::getCategory,
                                KnowledgeDocument::getCreatedAt)
        );

        return docs.stream()
                .map(doc -> new ScoredDocument(copyDocument(doc), scoreKeyword(doc, keywords)))
                .filter(doc -> doc.score > 0)
                .sorted(Comparator.comparingDouble((ScoredDocument d) -> d.score).reversed())
                .limit(topK)
                .map(scored -> scored.document)
                .collect(Collectors.toList());
    }

    public List<KnowledgeDocumentVO> listAll(Long userId, boolean savedOnly) {
        List<KnowledgeDocument> docs;
        Set<Long> savedIds;

        if (savedOnly) {
            List<Long> docIds = safeSelectFavoriteDocIds(userId);
            if (docIds == null || docIds.isEmpty()) {
                return List.of();
            }

            List<KnowledgeDocument> selected = documentMapper.selectBatchIds(docIds);
            Map<Long, KnowledgeDocument> byId = selected.stream()
                    .collect(Collectors.toMap(KnowledgeDocument::getId, d -> d, (a, b) -> a));
            docs = docIds.stream()
                    .map(byId::get)
                    .filter(d -> d != null)
                    .collect(Collectors.toList());
            savedIds = new HashSet<>(docIds);
        } else {
            docs = documentMapper.selectList(
                    new LambdaQueryWrapper<KnowledgeDocument>().select(
                                    KnowledgeDocument::getId,
                                    KnowledgeDocument::getTitle,
                                    KnowledgeDocument::getContent,
                                    KnowledgeDocument::getCategory,
                                    KnowledgeDocument::getCreatedAt
                            )
                            .orderByDesc(KnowledgeDocument::getCreatedAt)
                            .orderByDesc(KnowledgeDocument::getId)
            );
            List<Long> savedDocIds = safeSelectFavoriteDocIds(userId);
            savedIds = new HashSet<>(savedDocIds == null ? List.of() : savedDocIds);
        }

        return docs.stream()
                .map(doc -> toVO(doc, savedIds.contains(doc.getId())))
                .collect(Collectors.toList());
    }

    private List<Long> safeSelectFavoriteDocIds(Long userId) {
        if (userId == null) {
            return List.of();
        }
        try {
            List<Long> ids = knowledgeDocumentFavoriteMapper.selectDocumentIdsByUserId(userId);
            return ids == null ? List.of() : ids;
        } catch (Exception ex) {
            log.warn("Failed to query favorite documents for user {}, fallback to empty favorites: {}", userId, ex.getMessage());
            return List.of();
        }
    }

    public void toggleFavorite(Long userId, Long documentId, boolean favorite) {
        KnowledgeDocument doc = documentMapper.selectById(documentId);
        if (doc == null) {
            throw new BusinessException(
                    ErrorCode.NOT_FOUND,
                    ErrorCode.BIZ_DOCUMENT_NOT_FOUND,
                    "Knowledge document not found"
            );
        }

        KnowledgeDocumentFavorite existing = knowledgeDocumentFavoriteMapper
                .selectByUserIdAndDocumentId(userId, documentId);

        if (favorite) {
            if (existing == null) {
                knowledgeDocumentFavoriteMapper.insert(KnowledgeDocumentFavorite.builder()
                        .userId(userId)
                        .documentId(documentId)
                        .createdAt(LocalDateTime.now())
                        .build());
            }
            return;
        }

        if (existing != null) {
            knowledgeDocumentFavoriteMapper.deleteByUserIdAndDocumentId(userId, documentId);
        }
    }

    public List<RelatedGraphNodeVO> getRelatedNodes(Long documentId, Integer limit) {
        int size = Math.max(1, Math.min(limit == null ? 8 : limit, 20));
        KnowledgeDocument doc = documentMapper.selectById(documentId);
        if (doc == null) {
            throw new BusinessException(
                    ErrorCode.NOT_FOUND,
                    ErrorCode.BIZ_DOCUMENT_NOT_FOUND,
                    "Knowledge document not found"
            );
        }

        String title = safe(doc.getTitle());
        String content = safe(doc.getContent());
        String category = safe(doc.getCategory());
        String text = (title + "\n" + content).replaceAll("\\s+", " ").trim();
        if (text.length() > 4000) {
            text = text.substring(0, 4000);
        }

        try (Session session = neo4jDriver.session()) {
            Result result = session.run(
                    "MATCH (n:Knowledge) " +
                            "WITH n, toLower($text) AS text, toLower(trim(coalesce(toString(n.name), ''))) AS nodeName, " +
                            "toLower(trim(coalesce(toString(n.category), ''))) AS nodeCategory, " +
                            "[kw IN coalesce(n.keywords, []) | toLower(trim(toString(kw)))] AS nodeKeywords " +
                            "WITH n, nodeName, nodeCategory, nodeKeywords, " +
                            "CASE WHEN nodeName <> '' AND text CONTAINS nodeName THEN 4 ELSE 0 END + " +
                            "CASE WHEN $category <> '' AND nodeCategory = toLower($category) THEN 2 ELSE 0 END + " +
                            "reduce(score = 0, kw IN nodeKeywords | score + CASE WHEN size(kw) >= 2 AND text CONTAINS kw THEN 1 ELSE 0 END) AS score " +
                            "WHERE score > 0 " +
                            "RETURN n, score " +
                            "ORDER BY score DESC, n.name ASC " +
                            "LIMIT $limit",
                    Values.parameters(
                            "text", text,
                            "category", category,
                            "limit", size
                    )
            );

            List<RelatedGraphNodeVO> nodes = new ArrayList<>();
            while (result.hasNext()) {
                Record record = result.next();
                Node node = record.get("n").asNode();
                nodes.add(RelatedGraphNodeVO.builder()
                        .id(node.containsKey("id") ? node.get("id").asString("") : String.valueOf(node.id()))
                        .name(node.containsKey("name") ? node.get("name").asString("") : "")
                        .category(node.containsKey("category") ? node.get("category").asString("") : "")
                        .difficulty(node.containsKey("difficulty") ? node.get("difficulty").asString("") : "")
                        .score(record.get("score").asDouble(0.0))
                        .build());
            }
            return nodes;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to load related graph nodes for doc {}: {}", documentId, e.getMessage());
            throw new BusinessException(
                    ErrorCode.INTERNAL_SERVER_ERROR,
                    ErrorCode.BIZ_INTERNAL_SERVER_ERROR,
                    "加载关联图谱节点失败，请稍后重试"
            );
        }
    }

    public void deleteDocument(Long id) {
        int affectedRows = documentMapper.deleteById(id);
        if (affectedRows == 0) {
            throw new BusinessException(
                    ErrorCode.NOT_FOUND,
                    ErrorCode.BIZ_DOCUMENT_NOT_FOUND,
                    "Knowledge document not found or already deleted"
            );
        }
    }

    private void warmDocumentVectors(List<KnowledgeDocument> all) {
        for (KnowledgeDocument doc : all) {
            List<Double> vector = resolveDocumentVectorForIndex(doc);
            if (!vector.isEmpty()) {
                elasticsearchVectorService.upsertDocument(doc, vector);
            }
        }
        log.info("Document vector index warm-up finished for {} documents", all.size());
    }

    private void warmChunkVectors(List<KnowledgeDocument> all) {
        for (KnowledgeDocument doc : all) {
            upsertChunksForDocument(doc);
        }
        log.info("Chunk vector index warm-up finished for {} documents", all.size());
    }

    private void upsertChunksForDocument(KnowledgeDocument doc) {
        if (doc == null || doc.getId() == null) {
            return;
        }

        // 先删除旧 chunk，确保文档更新后分块与向量索引一致
        chunkMapper.delete(new LambdaQueryWrapper<KnowledgeChunk>()
                .eq(KnowledgeChunk::getDocumentId, doc.getId()));

        List<String> chunks = splitToChunks(doc.getContent(), CHUNK_SIZE, CHUNK_OVERLAP);
        LocalDateTime now = LocalDateTime.now();

        for (int i = 0; i < chunks.size(); i++) {
            String chunkText = chunks.get(i);
            KnowledgeChunk chunk = KnowledgeChunk.builder()
                    .documentId(doc.getId())
                    .chunkIndex(i)
                    .content(chunkText)
                    .createdAt(now)
                    .build();

            // chunk 级 embedding：用于优先检索命中文档片段，提升引用精度。
            List<Double> vector = embeddingService.embed(chunkText);
            if (!vector.isEmpty()) {
                try {
                    chunk.setEmbedding(objectMapper.writeValueAsString(vector));
                } catch (Exception e) {
                    log.warn("Failed to serialize chunk embedding docId={}, idx={}: {}", doc.getId(), i, e.getMessage());
                }
            }
            chunkMapper.insert(chunk);

            // 同步分块向量到 ES，用于后续 chunk 检索
            if (!vector.isEmpty()) {
                elasticsearchVectorService.upsertChunk(chunk, doc, vector);
            }
        }
    }

    private List<String> splitToChunks(String content, int chunkSize, int overlap) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        String normalized = content.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= chunkSize) {
            return List.of(normalized);
        }

        List<String> result = new ArrayList<>();
        int start = 0;
        int step = Math.max(1, chunkSize - overlap);

        while (start < normalized.length()) {
            int end = Math.min(normalized.length(), start + chunkSize);
            result.add(normalized.substring(start, end));
            if (end >= normalized.length()) {
                break;
            }
            start += step;
        }
        return result;
    }

    private double scoreSemantic(String query, KnowledgeDocument doc) {
        List<Double> queryVector = embeddingService.embed(query);
        return scoreSemantic(queryVector, doc);
    }

    private double scoreSemantic(List<Double> queryVector, KnowledgeDocument doc) {
        List<Double> docVector = parseEmbedding(doc.getEmbedding());
        if (docVector.isEmpty()) {
            return 0.0;
        }
        // 文档级相似度：余弦相似度（方向越接近，分数越高）
        return embeddingService.cosineSimilarity(queryVector, docVector);
    }

    private double scoreChunkSemantic(List<Double> queryVector, KnowledgeChunk chunk) {
        List<Double> chunkVector = parseEmbedding(chunk.getEmbedding());
        if (chunkVector.isEmpty()) {
            return 0.0;
        }
        // 分块级相似度：与 query 向量做余弦比较
        return embeddingService.cosineSimilarity(queryVector, chunkVector);
    }

    private double scoreChunkKeyword(KnowledgeChunk chunk, List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return 0.0;
        }
        String content = safe(chunk.getContent()).toLowerCase();
        double score = 0.0;
        for (String rawKeyword : keywords) {
            String keyword = rawKeyword == null ? "" : rawKeyword.toLowerCase().trim();
            if (keyword.isBlank()) {
                continue;
            }
            if (content.contains(keyword)) {
                score += 1.0;
            }
        }
        return score;
    }

    private List<Double> parseEmbedding(String embeddingJson) {
        if (embeddingJson == null || embeddingJson.isBlank()) {
            return List.of();
        }
        try {
            // 数据库存储的是 JSON 数组，这里反序列化成向量列表。
            return objectMapper.readValue(embeddingJson, new TypeReference<List<Double>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * 确保写入 ES 的向量维度固定一致。
     * 若历史 embedding 维度不匹配，则重新生成。
     */
    private List<Double> resolveDocumentVectorForIndex(KnowledgeDocument doc) {
        List<Double> vector = parseEmbedding(doc.getEmbedding());
        if (vector.size() == EmbeddingService.VECTOR_DIM) {
            return vector;
        }

        if (!vector.isEmpty()) {
            log.info("Regenerating document embedding for docId={} due to dimension mismatch: {} -> {}",
                    doc.getId(), vector.size(), EmbeddingService.VECTOR_DIM);
        }

        return embeddingService.embed(doc.getTitle() + "\n" + doc.getContent());
    }

    private double scoreKeyword(KnowledgeDocument doc, List<String> keywords) {
        String title = safe(doc.getTitle()).toLowerCase();
        String category = safe(doc.getCategory()).toLowerCase();
        String content = safe(doc.getContent()).toLowerCase();
        double score = 0.0;

        for (String rawKeyword : keywords) {
            String keyword = rawKeyword.toLowerCase();
            if (keyword.isBlank()) {
                continue;
            }
            if (title.contains(keyword)) {
                score += 4;
            }
            if (category.contains(keyword)) {
                score += 2;
            }
            if (content.contains(keyword)) {
                score += 1;
            }
        }
        return score;
    }

    private double rankBonus(int index) {
        return Math.max(0.0, 1.0 - index * 0.05);
    }

    private String trimChunkContent(String content, int maxLength) {
        if (content == null) {
            return "";
        }
        String normalized = content.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private void logSemanticTrace(String path, String query, int topK, List<KnowledgeDocument> result, long startNs) {
        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
        retrievalMetricsService.recordSemanticPath(path, result.size(), elapsedMs);
        log.info("RAG_SEMANTIC_TRACE path={} query='{}' topK={} hitCount={} elapsedMs={} hitDocs={}",
                path,
                trimLog(query, 80),
                topK,
                result.size(),
                elapsedMs,
                summarizeHits(result));
    }

    private String summarizeHits(List<KnowledgeDocument> docs) {
        if (docs == null || docs.isEmpty()) {
            return "[]";
        }
        return docs.stream()
                .limit(5)
                .map(doc -> {
                    String chunkIndex = doc.getHitChunkIndex() == null ? "-" : String.valueOf(doc.getHitChunkIndex());
                    String score = doc.getHitScore() == null ? "-" : String.format("%.4f", doc.getHitScore());
                    return doc.getId() + "#c" + chunkIndex + "@" + score;
                })
                .collect(Collectors.joining(",", "[", "]"));
    }

    private String trimLog(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }

    private KnowledgeDocument copyDocument(KnowledgeDocument source) {
        return KnowledgeDocument.builder()
                .id(source.getId())
                .title(source.getTitle())
                .content(source.getContent())
                .category(source.getCategory())
                .embedding(source.getEmbedding())
                .createdAt(source.getCreatedAt())
                .build();
    }

    private KnowledgeDocumentVO toVO(KnowledgeDocument doc, boolean saved) {
        return KnowledgeDocumentVO.builder()
                .id(doc.getId())
                .title(doc.getTitle())
                .content(doc.getContent())
                .category(doc.getCategory())
                .saved(saved)
                .createdAt(doc.getCreatedAt())
                .build();
    }

    private static final class ScoredDocument {
        private final KnowledgeDocument document;
        private double score;

        private ScoredDocument(KnowledgeDocument document, double score) {
            this.document = document;
            this.score = score;
        }
    }

    private static final class ScoredChunk {
        private final KnowledgeChunk chunk;
        private final double score;

        private ScoredChunk(KnowledgeChunk chunk, double score) {
            this.chunk = chunk;
            this.score = score;
        }
    }
}
