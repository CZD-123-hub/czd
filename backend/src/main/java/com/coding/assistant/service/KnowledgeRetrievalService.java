package com.coding.assistant.service;

import com.coding.assistant.dto.KnowledgeNodeVO;
import com.coding.assistant.entity.KnowledgeDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Values;
import org.neo4j.driver.types.Node;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeRetrievalService {

    private final Driver neo4jDriver;
    private final DocumentService documentService;
    private final QueryRewriteService queryRewriteService;

    /**
     * RAG 检索：混合检索 + 简单重排
     */
    public RetrievalResult retrieve(String question) {
        List<String> expandedTerms = queryRewriteService.expandTerms(question);
        List<KnowledgeDocument> docs = documentService.search(question, 4);
        List<KnowledgeNodeVO> nodes = retrieveFromNeo4j(expandedTerms);
        return new RetrievalResult(docs, nodes, expandedTerms);
    }

    /**
     * 构建注入 LLM 的上下文字符串
     */
    public String buildContext(RetrievalResult result) {
        StringBuilder context = new StringBuilder();

        if (!result.documents().isEmpty()) {
            context.append("【知识库检索结果】\n");
            for (int i = 0; i < result.documents().size(); i++) {
                KnowledgeDocument doc = result.documents().get(i);
                context.append(String.format("%d. 标题：%s\n", i + 1, doc.getTitle()));
                if (doc.getCategory() != null && !doc.getCategory().isBlank()) {
                    context.append("   分类：").append(doc.getCategory()).append("\n");
                }
                context.append("   内容：").append(trimContent(doc.getContent(), 420)).append("\n\n");
            }
        }

        if (!result.nodes().isEmpty()) {
            context.append("【知识图谱关联】\n");
            for (KnowledgeNodeVO node : result.nodes()) {
                context.append("- ").append(node.getName());
                if (node.getCategory() != null && !node.getCategory().isBlank()) {
                    context.append(" [").append(node.getCategory()).append("]");
                }
                if (node.getDescription() != null && !node.getDescription().isBlank()) {
                    context.append("：").append(node.getDescription());
                }
                if (node.getKeywords() != null && !node.getKeywords().isEmpty()) {
                    context.append("；关键词：").append(String.join("、", node.getKeywords()));
                }
                context.append("\n");
            }
            context.append("\n");
        }

        if (!result.expandedTerms().isEmpty()) {
            context.append("【检索关键词】").append(String.join("、", result.expandedTerms())).append("\n");
        }

        return context.toString();
    }

    /** 兼容旧接口 */
    public String buildContext(List<KnowledgeNodeVO> nodes) {
        if (nodes == null || nodes.isEmpty()) return "";
        StringBuilder context = new StringBuilder("相关知识点：\n\n");
        for (int i = 0; i < nodes.size(); i++) {
            KnowledgeNodeVO node = nodes.get(i);
            context.append(String.format("%d. **%s** [%s]\n", i + 1, node.getName(), node.getCategory()));
            if (node.getDescription() != null && !node.getDescription().isEmpty()) {
                context.append("   ").append(node.getDescription()).append("\n");
            }
        }
        return context.toString();
    }

    private List<KnowledgeNodeVO> retrieveFromNeo4j(List<String> expandedTerms) {
        if (expandedTerms == null || expandedTerms.isEmpty()) {
            return List.of();
        }

        Map<String, KnowledgeNodeVO> rankedNodes = new LinkedHashMap<>();
        try (Session session = neo4jDriver.session()) {
            String cypher = "MATCH (n:Knowledge) " +
                    "WHERE ANY(k IN $keywords WHERE toLower(n.name) CONTAINS toLower(k) " +
                    "OR toLower(coalesce(n.category, '')) CONTAINS toLower(k) " +
                    "OR ANY(kw IN coalesce(n.keywords, []) WHERE toLower(kw) CONTAINS toLower(k))) " +
                    "RETURN n LIMIT 12";
            Result result = session.run(cypher, Values.parameters("keywords", expandedTerms));
            while (result.hasNext()) {
                Record record = result.next();
                KnowledgeNodeVO vo = mapToVO(record.get("n").asNode());
                rankedNodes.putIfAbsent(vo.getId(), vo);
            }
        } catch (Exception e) {
            log.warn("Neo4j retrieval failed: {}", e.getMessage());
        }
        return rankedNodes.values().stream().limit(5).collect(Collectors.toList());
    }

    private KnowledgeNodeVO mapToVO(Node node) {
        List<String> keywords = new ArrayList<>();
        if (node.containsKey("keywords")) {
            node.get("keywords").asList(v -> v.asString()).forEach(keywords::add);
        }
        return KnowledgeNodeVO.builder()
                .id(node.containsKey("id") ? node.get("id").asString() : String.valueOf(node.id()))
                .name(node.containsKey("name") ? node.get("name").asString() : "")
                .category(node.containsKey("category") ? node.get("category").asString() : "")
                .description(node.containsKey("description") ? node.get("description").asString() : "")
                .difficulty(node.containsKey("difficulty") ? node.get("difficulty").asString() : "")
                .keywords(keywords)
                .build();
    }

    private String trimContent(String content, int maxLength) {
        if (content == null) {
            return "";
        }
        String normalized = content.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }

    public record RetrievalResult(List<KnowledgeDocument> documents, List<KnowledgeNodeVO> nodes, List<String> expandedTerms) {}
}
