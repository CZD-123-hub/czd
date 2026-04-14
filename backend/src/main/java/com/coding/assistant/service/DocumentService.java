package com.coding.assistant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.coding.assistant.entity.KnowledgeDocument;
import com.coding.assistant.mapper.KnowledgeDocumentMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final KnowledgeDocumentMapper documentMapper;
    private final EmbeddingService embeddingService;
    private final ObjectMapper objectMapper;
    private final QueryRewriteService queryRewriteService;

    /** 启动时加载所有文档语料，初始化 TF-IDF 词表 */
    @PostConstruct
    public void initCorpus() {
        try {
            List<KnowledgeDocument> all = documentMapper.selectList(
                    new LambdaQueryWrapper<KnowledgeDocument>()
                            .select(KnowledgeDocument::getTitle, KnowledgeDocument::getContent));
            for (KnowledgeDocument doc : all) {
                embeddingService.updateCorpus(doc.getTitle() + "\n" + doc.getContent());
            }
            log.info("TF-IDF corpus initialized with {} documents", all.size());
        } catch (Exception e) {
            log.warn("Failed to init corpus: {}", e.getMessage());
        }
    }

    /**
     * 新增文档并自动生成 embedding
     */
    public KnowledgeDocument addDocument(String title, String content, String category) {
        embeddingService.updateCorpus(title + "\n" + content);
        KnowledgeDocument doc = KnowledgeDocument.builder()
                .title(title)
                .content(content)
                .category(category)
                .createdAt(LocalDateTime.now())
                .build();

        List<Double> vector = embeddingService.embed(title + "\n" + content);
        if (!vector.isEmpty()) {
            try {
                doc.setEmbedding(objectMapper.writeValueAsString(vector));
            } catch (Exception e) {
                log.error("Failed to serialize embedding: {}", e.getMessage());
            }
        }

        documentMapper.insert(doc);
        return doc;
    }

    /**
     * 混合检索入口：语义检索 + 关键词检索融合去重
     */
    public List<KnowledgeDocument> search(String query, int topK) {
        List<String> expandedTerms = queryRewriteService.expandTerms(query);
        List<KnowledgeDocument> semanticDocs = searchSemantic(query, Math.max(topK * 2, 6));
        List<KnowledgeDocument> keywordDocs = searchByKeywords(expandedTerms, Math.max(topK * 2, 6));

        Map<Long, ScoredDocument> merged = new LinkedHashMap<>();

        for (int i = 0; i < semanticDocs.size(); i++) {
            KnowledgeDocument doc = semanticDocs.get(i);
            double score = scoreSemantic(query, doc) + rankBonus(i);
            merged.merge(doc.getId(), new ScoredDocument(copyDocument(doc), score),
                    (left, right) -> left.score >= right.score ? left : right);
        }

        for (int i = 0; i < keywordDocs.size(); i++) {
            KnowledgeDocument doc = keywordDocs.get(i);
            double score = scoreKeyword(doc, expandedTerms) + rankBonus(i);
            merged.merge(doc.getId(), new ScoredDocument(copyDocument(doc), score),
                    (left, right) -> {
                        left.score += right.score;
                        return left;
                    });
        }

        return merged.values().stream()
                .sorted(Comparator.comparingDouble((ScoredDocument d) -> d.score).reversed())
                .limit(topK)
                .map(scored -> scored.document)
                .collect(Collectors.toList());
    }

    public List<KnowledgeDocument> searchSemantic(String query, int topK) {
        List<Double> queryVector = embeddingService.embed(query);
        if (queryVector.isEmpty()) {
            return List.of();
        }

        List<KnowledgeDocument> docs = documentMapper.selectAllWithEmbedding();

        return docs.stream()
                .map(doc -> new ScoredDocument(copyDocument(doc), scoreSemantic(queryVector, doc)))
                .filter(doc -> doc.score > 0.08)
                .sorted(Comparator.comparingDouble((ScoredDocument d) -> d.score).reversed())
                .limit(topK)
                .map(scored -> scored.document)
                .collect(Collectors.toList());
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

    public List<KnowledgeDocument> listAll() {
        return documentMapper.selectList(
                new LambdaQueryWrapper<KnowledgeDocument>().select(
                        KnowledgeDocument::getId,
                        KnowledgeDocument::getTitle,
                        KnowledgeDocument::getCategory,
                        KnowledgeDocument::getCreatedAt
                )
        );
    }

    public void deleteDocument(Long id) {
        documentMapper.deleteById(id);
    }

    private double scoreSemantic(String query, KnowledgeDocument doc) {
        List<Double> queryVector = embeddingService.embed(query);
        return scoreSemantic(queryVector, doc);
    }

    private double scoreSemantic(List<Double> queryVector, KnowledgeDocument doc) {
        if (doc.getEmbedding() == null || doc.getEmbedding().isBlank()) {
            return 0.0;
        }
        try {
            List<Double> docVector = objectMapper.readValue(doc.getEmbedding(), new TypeReference<List<Double>>() {});
            return embeddingService.cosineSimilarity(queryVector, docVector);
        } catch (Exception e) {
            log.warn("Failed to parse embedding for doc {}: {}", doc.getId(), e.getMessage());
            return 0.0;
        }
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

    private String safe(String value) {
        return value == null ? "" : value;
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

    private static final class ScoredDocument {
        private final KnowledgeDocument document;
        private double score;

        private ScoredDocument(KnowledgeDocument document, double score) {
            this.document = document;
            this.score = score;
        }
    }
}
