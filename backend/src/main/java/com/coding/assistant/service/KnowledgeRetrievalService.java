package com.coding.assistant.service;

import com.coding.assistant.dto.EdgeVO;
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
     * RAG 检索入口：同时检索文档证据 + 图节点证据 + 图关系证据。
     */
    public RetrievalResult retrieve(String question) {
        List<String> expandedTerms = queryRewriteService.expandTerms(question);
        List<KnowledgeDocument> documents = documentService.search(question, 4);
        List<KnowledgeNodeVO> nodes = retrieveNodesFromNeo4j(expandedTerms);
        List<EdgeVO> edges = retrieveRelationsFromNeo4j(nodes);
        return new RetrievalResult(documents, nodes, edges, expandedTerms);
    }

    /**
     * 将检索结果拼成可注入 LLM 的上下文文本。
     */
    public String buildContext(RetrievalResult result) {
        StringBuilder context = new StringBuilder();

        // 文档证据段：标题/分类/摘要
        if (!result.documents().isEmpty()) {
            context.append("[Document Evidence]\n");
            for (int i = 0; i < result.documents().size(); i++) {
                KnowledgeDocument doc = result.documents().get(i);
                context.append(i + 1).append(". Title: ").append(doc.getTitle()).append("\n");
                if (doc.getCategory() != null && !doc.getCategory().isBlank()) {
                    context.append("   Category: ").append(doc.getCategory()).append("\n");
                }
                context.append("   Excerpt: ").append(trimContent(doc.getContent(), 420)).append("\n\n");
            }
        }

        // 图节点证据段：节点名、分类、描述、关键词
        if (!result.nodes().isEmpty()) {
            context.append("[Graph Node Evidence]\n");
            for (KnowledgeNodeVO node : result.nodes()) {
                context.append("- ").append(node.getName());
                if (node.getCategory() != null && !node.getCategory().isBlank()) {
                    context.append(" [").append(node.getCategory()).append("]");
                }
                if (node.getDescription() != null && !node.getDescription().isBlank()) {
                    context.append(" | ").append(node.getDescription());
                }
                if (node.getKeywords() != null && !node.getKeywords().isEmpty()) {
                    context.append(" | keywords: ").append(String.join(", ", node.getKeywords()));
                }
                context.append("\n");
            }
            context.append("\n");
        }

        // 图关系证据段：source --type--> target
        if (!result.edges().isEmpty()) {
            Map<String, String> nameById = result.nodes().stream()
                    .collect(Collectors.toMap(KnowledgeNodeVO::getId, KnowledgeNodeVO::getName, (a, b) -> a));
            context.append("[Graph Relation Evidence]\n");
            for (EdgeVO edge : result.edges()) {
                String source = resolveNodeName(nameById, edge.getSource());
                String target = resolveNodeName(nameById, edge.getTarget());
                context.append("- ").append(source)
                        .append(" --").append(edge.getType()).append("--> ")
                        .append(target)
                        .append("\n");
            }
            context.append("\n");
        }

        // 扩展词段：告诉模型本轮检索用到了哪些同义/相关词
        if (!result.expandedTerms().isEmpty()) {
            context.append("[Expanded Terms] ").append(String.join(", ", result.expandedTerms())).append("\n");
        }

        return context.toString();
    }

    /**
     * 兼容旧调用：仅基于节点构造上下文。
     */
    public String buildContext(List<KnowledgeNodeVO> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return "";
        }
        StringBuilder context = new StringBuilder("Related knowledge nodes:\n\n");
        for (int i = 0; i < nodes.size(); i++) {
            KnowledgeNodeVO node = nodes.get(i);
            context.append(i + 1)
                    .append(". **")
                    .append(node.getName())
                    .append("** [")
                    .append(node.getCategory())
                    .append("]\n");
            if (node.getDescription() != null && !node.getDescription().isEmpty()) {
                context.append("   ").append(node.getDescription()).append("\n");
            }
        }
        return context.toString();
    }

    private List<KnowledgeNodeVO> retrieveNodesFromNeo4j(List<String> expandedTerms) {
        if (expandedTerms == null || expandedTerms.isEmpty()) {
            return List.of();
        }

        Map<String, KnowledgeNodeVO> rankedNodes = new LinkedHashMap<>();
        try (Session session = neo4jDriver.session()) {
            // 通过名称/分类/关键词匹配扩展词，召回候选知识节点
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
            log.warn("Neo4j node retrieval failed: {}", e.getMessage());
        }
        return rankedNodes.values().stream().limit(5).collect(Collectors.toList());
    }

    private List<EdgeVO> retrieveRelationsFromNeo4j(List<KnowledgeNodeVO> nodes) {
        if (nodes == null || nodes.size() < 2) {
            return List.of();
        }

        List<String> nodeIds = nodes.stream()
                .map(KnowledgeNodeVO::getId)
                .distinct()
                .toList();
        if (nodeIds.isEmpty()) {
            return List.of();
        }

        Map<String, EdgeVO> rankedEdges = new LinkedHashMap<>();
        try (Session session = neo4jDriver.session()) {
            // 仅在已召回节点子图中查询关系，避免引入无关噪声
            String cypher = "MATCH (a:Knowledge)-[r]->(b:Knowledge) " +
                    "WHERE a.id IN $nodeIds AND b.id IN $nodeIds " +
                    "RETURN a.id AS source, b.id AS target, type(r) AS type " +
                    "LIMIT 20";
            Result result = session.run(cypher, Values.parameters("nodeIds", nodeIds));
            while (result.hasNext()) {
                Record record = result.next();
                String source = record.get("source").asString();
                String target = record.get("target").asString();
                String type = record.get("type").asString();
                String edgeKey = source + "->" + type + "->" + target;
                rankedEdges.putIfAbsent(edgeKey, EdgeVO.builder()
                        .source(source)
                        .target(target)
                        .type(type)
                        .build());
            }
        } catch (Exception e) {
            log.warn("Neo4j relation retrieval failed: {}", e.getMessage());
        }
        return new ArrayList<>(rankedEdges.values());
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

    private String resolveNodeName(Map<String, String> nameById, String nodeId) {
        String name = nameById.get(nodeId);
        if (name == null || name.isBlank()) {
            return nodeId;
        }
        return name;
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

    public record RetrievalResult(
            List<KnowledgeDocument> documents,
            List<KnowledgeNodeVO> nodes,
            List<EdgeVO> edges,
            List<String> expandedTerms
    ) {}
}
