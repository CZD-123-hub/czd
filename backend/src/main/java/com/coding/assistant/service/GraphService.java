package com.coding.assistant.service;

import com.coding.assistant.dto.EdgeVO;
import com.coding.assistant.dto.GraphVO;
import com.coding.assistant.dto.KnowledgeNodeVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Values;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Relationship;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class GraphService {

    private final Driver neo4jDriver;

    public GraphVO getOverview() {
        List<KnowledgeNodeVO> nodes = new ArrayList<>();
        List<EdgeVO> edges = new ArrayList<>();
        Set<String> nodeIds = new HashSet<>();

        try (Session session = neo4jDriver.session()) {
            // Get nodes
            String nodeCypher = "MATCH (n:Knowledge) RETURN n LIMIT 500";
            Result nodeResult = session.run(nodeCypher);
            while (nodeResult.hasNext()) {
                Record record = nodeResult.next();
                Node node = record.get("n").asNode();
                KnowledgeNodeVO vo = mapToKnowledgeNodeVO(node);
                nodes.add(vo);
                nodeIds.add(vo.getId());
            }

            // Get relationships
            String relCypher = "MATCH (a:Knowledge)-[r]->(b:Knowledge) RETURN a, r, b LIMIT 500";
            Result relResult = session.run(relCypher);
            while (relResult.hasNext()) {
                Record record = relResult.next();
                Node sourceNode = record.get("a").asNode();
                Node targetNode = record.get("b").asNode();
                Relationship rel = record.get("r").asRelationship();

                String sourceId = sourceNode.containsKey("id") ? sourceNode.get("id").asString() : String.valueOf(sourceNode.id());
                String targetId = targetNode.containsKey("id") ? targetNode.get("id").asString() : String.valueOf(targetNode.id());

                edges.add(EdgeVO.builder()
                        .source(sourceId)
                        .target(targetId)
                        .type(rel.type())
                        .build());
            }
        } catch (Exception e) {
            log.error("Error getting graph overview from Neo4j: {}", e.getMessage());
        }

        return GraphVO.builder()
                .nodes(nodes)
                .edges(edges)
                .build();
    }

    public KnowledgeNodeVO getNodeDetail(String id) {
        try (Session session = neo4jDriver.session()) {
            String cypher = "MATCH (n:Knowledge {id: $id}) RETURN n";
            Result result = session.run(cypher, Values.parameters("id", id));

            if (result.hasNext()) {
                Node node = result.next().get("n").asNode();
                return mapToKnowledgeNodeVO(node);
            }
        } catch (Exception e) {
            log.error("Error getting node detail from Neo4j: {}", e.getMessage());
            throw new RuntimeException("获取节点详情失败", e);
        }

        throw new RuntimeException("节点不存在: " + id);
    }

    public GraphVO getNeighbors(String id) {
        List<KnowledgeNodeVO> nodes = new ArrayList<>();
        List<EdgeVO> edges = new ArrayList<>();
        Set<String> addedNodeIds = new HashSet<>();

        try (Session session = neo4jDriver.session()) {
            String cypher = "MATCH (n:Knowledge {id: $id})-[r]-(m:Knowledge) RETURN n, r, m";
            Result result = session.run(cypher, Values.parameters("id", id));

            while (result.hasNext()) {
                Record record = result.next();
                Node centerNode = record.get("n").asNode();
                Node neighborNode = record.get("m").asNode();
                Relationship rel = record.get("r").asRelationship();

                // Add center node if not already added
                String centerId = centerNode.containsKey("id") ? centerNode.get("id").asString() : String.valueOf(centerNode.id());
                if (addedNodeIds.add(centerId)) {
                    nodes.add(mapToKnowledgeNodeVO(centerNode));
                }

                // Add neighbor node if not already added
                String neighborId = neighborNode.containsKey("id") ? neighborNode.get("id").asString() : String.valueOf(neighborNode.id());
                if (addedNodeIds.add(neighborId)) {
                    nodes.add(mapToKnowledgeNodeVO(neighborNode));
                }

                // Determine edge direction
                String startId = centerNode.id() == rel.startNodeId() ? centerId : neighborId;
                String endId = centerNode.id() == rel.startNodeId() ? neighborId : centerId;

                edges.add(EdgeVO.builder()
                        .source(startId)
                        .target(endId)
                        .type(rel.type())
                        .build());
            }
        } catch (Exception e) {
            log.error("Error getting neighbors from Neo4j: {}", e.getMessage());
        }

        return GraphVO.builder()
                .nodes(nodes)
                .edges(edges)
                .build();
    }

    public List<KnowledgeNodeVO> search(String keyword) {
        List<KnowledgeNodeVO> nodes = new ArrayList<>();

        try (Session session = neo4jDriver.session()) {
            String cypher = "MATCH (n:Knowledge) " +
                    "WHERE toLower(n.name) CONTAINS toLower($keyword) " +
                    "OR ANY(kw IN n.keywords WHERE toLower(kw) CONTAINS toLower($keyword)) " +
                    "RETURN n LIMIT 50";

            Result result = session.run(cypher, Values.parameters("keyword", keyword));
            while (result.hasNext()) {
                Node node = result.next().get("n").asNode();
                nodes.add(mapToKnowledgeNodeVO(node));
            }
        } catch (Exception e) {
            log.error("Error searching knowledge graph: {}", e.getMessage());
        }

        return nodes;
    }

    public GraphVO searchGraph(String keyword) {
        List<KnowledgeNodeVO> nodes = new ArrayList<>();
        List<EdgeVO> edges = new ArrayList<>();
        Set<String> nodeIds = new HashSet<>();

        try (Session session = neo4jDriver.session()) {
            // Find matching nodes and their direct relationships
            String cypher = "MATCH (n:Knowledge) " +
                    "WHERE toLower(n.name) CONTAINS toLower($keyword) " +
                    "OR toLower(n.category) CONTAINS toLower($keyword) " +
                    "OR ANY(kw IN n.keywords WHERE toLower(kw) CONTAINS toLower($keyword)) " +
                    "OPTIONAL MATCH (n)-[r]-(m:Knowledge) " +
                    "WHERE toLower(m.name) CONTAINS toLower($keyword) " +
                    "OR ANY(kw IN m.keywords WHERE toLower(kw) CONTAINS toLower($keyword)) " +
                    "RETURN n, r, m LIMIT 200";

            Result result = session.run(cypher, Values.parameters("keyword", keyword));
            while (result.hasNext()) {
                Record record = result.next();
                Node node = record.get("n").asNode();
                KnowledgeNodeVO vo = mapToKnowledgeNodeVO(node);
                if (nodeIds.add(vo.getId())) {
                    nodes.add(vo);
                }

                if (!record.get("m").isNull() && !record.get("r").isNull()) {
                    Node neighborNode = record.get("m").asNode();
                    KnowledgeNodeVO neighborVo = mapToKnowledgeNodeVO(neighborNode);
                    if (nodeIds.add(neighborVo.getId())) {
                        nodes.add(neighborVo);
                    }

                    Relationship rel = record.get("r").asRelationship();
                    String sourceId = node.containsKey("id") ? node.get("id").asString() : String.valueOf(node.id());
                    String targetId = neighborNode.containsKey("id") ? neighborNode.get("id").asString() : String.valueOf(neighborNode.id());

                    // Determine correct direction
                    if (node.id() != rel.startNodeId()) {
                        String temp = sourceId;
                        sourceId = targetId;
                        targetId = temp;
                    }

                    edges.add(EdgeVO.builder()
                            .source(sourceId)
                            .target(targetId)
                            .type(rel.type())
                            .build());
                }
            }
        } catch (Exception e) {
            log.error("Error searching knowledge graph: {}", e.getMessage());
        }

        return GraphVO.builder()
                .nodes(nodes)
                .edges(edges)
                .build();
    }

    private KnowledgeNodeVO mapToKnowledgeNodeVO(Node node) {
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
}