package com.coding.assistant.service;

import com.coding.assistant.dto.EdgeVO;
import com.coding.assistant.dto.GraphHealthVO;
import com.coding.assistant.dto.GraphNodeCreateRequest;
import com.coding.assistant.dto.GraphRelationCreateRequest;
import com.coding.assistant.dto.GraphVO;
import com.coding.assistant.dto.KnowledgeNodeVO;
import com.coding.assistant.dto.RelatedDocumentVO;
import com.coding.assistant.entity.KnowledgeDocument;
import com.coding.assistant.exception.BusinessException;
import com.coding.assistant.exception.ErrorCode;
import com.coding.assistant.mapper.KnowledgeDocumentMapper;
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
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class GraphService {

    private final Driver neo4jDriver;
    private final KnowledgeDocumentMapper documentMapper;

    private static final Map<String, String> NODE_DESCRIPTION_ZH = Map.ofEntries(
            Map.entry("java", "面向对象编程语言，常用于企业级后端开发。"),
            Map.entry("python", "语法简洁的通用语言，适合脚本、数据分析和 AI。"),
            Map.entry("javascript", "Web 前端核心语言，也可用于 Node.js 后端开发。"),
            Map.entry("typescript", "JavaScript 的类型增强版本，提升大型项目可维护性。"),
            Map.entry("spring-boot", "用于快速构建 Spring 应用的框架，约定优于配置。"),
            Map.entry("spring-mvc", "Spring Web 模块，用于构建 REST API 与 Web 应用。"),
            Map.entry("spring-security", "Spring 生态中的认证与授权框架。"),
            Map.entry("mybatis", "通过 SQL 映射实现数据持久化的框架。"),
            Map.entry("mybatis-plus", "MyBatis 增强工具，提供常用 CRUD 与分页能力。"),
            Map.entry("vue3", "渐进式前端框架，适合构建响应式界面。"),
            Map.entry("mysql", "主流关系型数据库系统。"),
            Map.entry("redis", "高性能内存数据库，常用于缓存和高并发场景。"),
            Map.entry("neo4j", "图数据库，适合存储和查询复杂关系网络。"),
            Map.entry("docker", "容器化平台，用于应用打包、分发和运行。"),
            Map.entry("kubernetes", "容器编排平台，用于自动化部署与集群管理。"),
            Map.entry("git", "分布式版本控制系统。"),
            Map.entry("rest-api", "基于 REST 风格设计网络 API 的方法。"),
            Map.entry("microservices", "将系统拆分为可独立部署服务的架构方式。"),
            Map.entry("jwt", "用于无状态认证的 JSON Web Token。"),
            Map.entry("ci-cd", "持续集成与持续交付实践。"),
            Map.entry("rag", "检索增强生成，通过检索外部知识提升回答准确性。")
    );

    private static final Set<String> ALLOWED_RELATION_TYPES = Set.of(
            "DEPENDS_ON",
            "CONTAINS",
            "RELATED_TO"
    );

    private static final Set<String> ALLOWED_NODE_CATEGORIES = Set.of(
            "language",
            "framework",
            "runtime",
            "database",
            "tool",
            "fundamental",
            "concept"
    );

    // 查询图谱总览，供页面首屏渲染使用。
    public GraphVO getOverview() {
        List<KnowledgeNodeVO> nodes = new ArrayList<>();
        List<EdgeVO> edges = new ArrayList<>();

        try (Session session = neo4jDriver.session()) {
            String nodeCypher = "MATCH (n:Knowledge) RETURN n LIMIT 500";
            Result nodeResult = session.run(nodeCypher);
            while (nodeResult.hasNext()) {
                Node node = nodeResult.next().get("n").asNode();
                nodes.add(mapToKnowledgeNodeVO(node));
            }

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

    // 按业务 id 查询单个节点详情。
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
            throw new BusinessException(
                    ErrorCode.INTERNAL_SERVER_ERROR,
                    ErrorCode.BIZ_INTERNAL_SERVER_ERROR,
                    "获取节点详情失败，请稍后重试"
            );
        }

        throw new BusinessException(
                ErrorCode.NOT_FOUND,
                ErrorCode.BIZ_NOT_FOUND,
                "节点不存在: " + id
        );
    }

    // 按深度（1..4）扩展某节点邻域，并返回子图。
    public GraphVO getNeighbors(String id, Integer depth) {
        List<KnowledgeNodeVO> nodes = new ArrayList<>();
        List<EdgeVO> edges = new ArrayList<>();
        Set<String> addedNodeIds = new HashSet<>();
        Set<String> addedEdgeKeys = new HashSet<>();
        int effectiveDepth = Math.max(1, Math.min(depth == null ? 1 : depth, 4));

        try (Session session = neo4jDriver.session()) {
            // 获取以中心节点为起点路径上出现的所有节点。
            String nodeCypher = String.format(
                    "MATCH (center:Knowledge {id: $id}) " +
                            "MATCH p=(center)-[*1..%d]-(n:Knowledge) " +
                            "UNWIND nodes(p) AS nd " +
                            "RETURN DISTINCT nd",
                    effectiveDepth
            );

            Result nodeResult = session.run(nodeCypher, Values.parameters("id", id));
            while (nodeResult.hasNext()) {
                Node node = nodeResult.next().get("nd").asNode();
                String nodeId = node.containsKey("id") ? node.get("id").asString() : String.valueOf(node.id());
                if (addedNodeIds.add(nodeId)) {
                    nodes.add(mapToKnowledgeNodeVO(node));
                }
            }

            // 即使没有邻居，也保证能返回中心节点本身。
            if (addedNodeIds.isEmpty()) {
                String centerCypher = "MATCH (n:Knowledge {id: $id}) RETURN n";
                Result centerResult = session.run(centerCypher, Values.parameters("id", id));
                if (centerResult.hasNext()) {
                    Node centerNode = centerResult.next().get("n").asNode();
                    String centerId = centerNode.containsKey("id") ? centerNode.get("id").asString() : String.valueOf(centerNode.id());
                    addedNodeIds.add(centerId);
                    nodes.add(mapToKnowledgeNodeVO(centerNode));
                }
            }

            // 仅拉取已纳入子图节点集合之间的关系。
            if (!addedNodeIds.isEmpty()) {
                String relCypher =
                        "MATCH (a:Knowledge)-[r]-(b:Knowledge) " +
                                "WHERE a.id IN $nodeIds AND b.id IN $nodeIds " +
                                "RETURN DISTINCT a, r, b";
                Result relResult = session.run(relCypher, Values.parameters("nodeIds", new ArrayList<>(addedNodeIds)));

                while (relResult.hasNext()) {
                    Record record = relResult.next();
                    Node sourceNode = record.get("a").asNode();
                    Node targetNode = record.get("b").asNode();
                    Relationship rel = record.get("r").asRelationship();

                    String sourceId = sourceNode.containsKey("id") ? sourceNode.get("id").asString() : String.valueOf(sourceNode.id());
                    String targetId = targetNode.containsKey("id") ? targetNode.get("id").asString() : String.valueOf(targetNode.id());

                    // 保持边方向与关系起点方向一致。
                    if (sourceNode.id() != rel.startNodeId()) {
                        String temp = sourceId;
                        sourceId = targetId;
                        targetId = temp;
                    }

                    String edgeKey = sourceId + "|" + rel.type() + "|" + targetId;
                    if (addedEdgeKeys.add(edgeKey)) {
                        edges.add(EdgeVO.builder()
                                .source(sourceId)
                                .target(targetId)
                                .type(rel.type())
                                .build());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error getting neighbors from Neo4j: {}", e.getMessage());
            throw new BusinessException(
                    ErrorCode.INTERNAL_SERVER_ERROR,
                    ErrorCode.BIZ_INTERNAL_SERVER_ERROR,
                    "获取关联节点失败，请稍后重试"
            );
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

    // 按名称/分类/关键词搜索图谱，并携带命中节点相关边。
    public GraphVO searchGraph(String keyword) {
        List<KnowledgeNodeVO> nodes = new ArrayList<>();
        List<EdgeVO> edges = new ArrayList<>();
        Set<String> nodeIds = new HashSet<>();

        try (Session session = neo4jDriver.session()) {
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

    // 创建 Knowledge 节点，并做参数校验与重复校验。
    public KnowledgeNodeVO createNode(GraphNodeCreateRequest request) {
        String nodeId = safeLower(request.getId());
        String nodeName = request.getName() == null ? "" : request.getName().trim();
        String category = safeLower(request.getCategory());
        String difficulty = request.getDifficulty() == null ? "beginner" : request.getDifficulty().trim().toLowerCase(Locale.ROOT);
        String description = request.getDescription() == null ? "" : request.getDescription().trim();
        List<String> keywords = request.getKeywords() == null
                ? List.of()
                : request.getKeywords().stream()
                .map(item -> item == null ? "" : item.trim())
                .filter(item -> !item.isBlank())
                .distinct()
                .limit(20)
                .toList();

        if (nodeId.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, ErrorCode.BIZ_BAD_REQUEST, "节点ID不能为空");
        }
        if (nodeName.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, ErrorCode.BIZ_BAD_REQUEST, "节点名称不能为空");
        }
        if (!ALLOWED_NODE_CATEGORIES.contains(category)) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    ErrorCode.BIZ_BAD_REQUEST,
                    "节点分类不合法，可选值：" + ALLOWED_NODE_CATEGORIES
            );
        }

        try (Session session = neo4jDriver.session()) {
            boolean exists = session.run(
                    "MATCH (n:Knowledge {id: $id}) RETURN count(n) > 0 AS exists",
                    Values.parameters("id", nodeId)
            ).single().get("exists").asBoolean(false);
            if (exists) {
                throw new BusinessException(
                        ErrorCode.BAD_REQUEST,
                        ErrorCode.BIZ_BAD_REQUEST,
                        "节点ID已存在：" + nodeId
                );
            }

            Node node = session.run(
                    "CREATE (n:Knowledge {id:$id, name:$name, category:$category, difficulty:$difficulty, description:$description, keywords:$keywords}) RETURN n",
                    Values.parameters(
                            "id", nodeId,
                            "name", nodeName,
                            "category", category,
                            "difficulty", difficulty,
                            "description", description,
                            "keywords", keywords
                    )
            ).single().get("n").asNode();
            return mapToKnowledgeNodeVO(node);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error creating graph node: {}", e.getMessage());
            throw new BusinessException(
                    ErrorCode.INTERNAL_SERVER_ERROR,
                    ErrorCode.BIZ_INTERNAL_SERVER_ERROR,
                    "新增图谱节点失败，请稍后重试"
            );
        }
    }

    // 在已存在节点之间创建一条有向关系。
    public EdgeVO createRelation(GraphRelationCreateRequest request) {
        String sourceId = safeLower(request.getSourceId());
        String targetId = safeLower(request.getTargetId());
        String relationType = normalizeRelationType(request.getRelationType());

        if (sourceId.isBlank() || targetId.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, ErrorCode.BIZ_BAD_REQUEST, "关系节点ID不能为空");
        }
        if (sourceId.equals(targetId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, ErrorCode.BIZ_BAD_REQUEST, "源节点和目标节点不能相同");
        }
        if (!ALLOWED_RELATION_TYPES.contains(relationType)) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    ErrorCode.BIZ_BAD_REQUEST,
                    "关系类型不合法，可选值：" + ALLOWED_RELATION_TYPES
            );
        }

        try (Session session = neo4jDriver.session()) {
            long sourceCount = readLong(
                    session,
                    "MATCH (n:Knowledge {id: $id}) RETURN count(n) AS c",
                    Values.parameters("id", sourceId),
                    "c"
            );
            long targetCount = readLong(
                    session,
                    "MATCH (n:Knowledge {id: $id}) RETURN count(n) AS c",
                    Values.parameters("id", targetId),
                    "c"
            );
            if (sourceCount == 0 || targetCount == 0) {
                throw new BusinessException(
                        ErrorCode.NOT_FOUND,
                        ErrorCode.BIZ_NOT_FOUND,
                        "源节点或目标节点不存在"
                );
            }

            String relationCypher = String.format(
                    "MATCH (a:Knowledge {id: $sourceId}), (b:Knowledge {id: $targetId}) MERGE (a)-[r:%s]->(b) RETURN a,b,r",
                    relationType
            );
            session.run(
                    relationCypher,
                    Values.parameters("sourceId", sourceId, "targetId", targetId)
            );
            return EdgeVO.builder()
                    .source(sourceId)
                    .target(targetId)
                    .type(relationType)
                    .build();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error creating graph relation: {}", e.getMessage());
            throw new BusinessException(
                    ErrorCode.INTERNAL_SERVER_ERROR,
                    ErrorCode.BIZ_INTERNAL_SERVER_ERROR,
                    "新增图谱关系失败，请稍后重试"
            );
        }
    }

    // 删除节点，并级联删除其关联关系。
    public void deleteNode(String nodeIdRaw) {
        String nodeId = safeLower(nodeIdRaw);
        if (nodeId.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, ErrorCode.BIZ_BAD_REQUEST, "节点ID不能为空");
        }

        try (Session session = neo4jDriver.session()) {
            long nodeCount = readLong(
                    session,
                    "MATCH (n:Knowledge {id: $id}) RETURN count(n) AS c",
                    Values.parameters("id", nodeId),
                    "c"
            );
            if (nodeCount <= 0) {
                throw new BusinessException(
                        ErrorCode.NOT_FOUND,
                        ErrorCode.BIZ_NOT_FOUND,
                        "节点不存在: " + nodeId
                );
            }

            session.run(
                    "MATCH (n:Knowledge {id: $id}) DETACH DELETE n",
                    Values.parameters("id", nodeId)
            );
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error deleting graph node {}: {}", nodeId, e.getMessage());
            throw new BusinessException(
                    ErrorCode.INTERNAL_SERVER_ERROR,
                    ErrorCode.BIZ_INTERNAL_SERVER_ERROR,
                    "删除图谱节点失败，请稍后重试"
            );
        }
    }

    // 删除一条有向关系，并严格校验关系类型。
    public void deleteRelation(GraphRelationCreateRequest request) {
        String sourceId = safeLower(request.getSourceId());
        String targetId = safeLower(request.getTargetId());
        String relationType = normalizeRelationType(request.getRelationType());

        if (sourceId.isBlank() || targetId.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, ErrorCode.BIZ_BAD_REQUEST, "关系节点ID不能为空");
        }
        if (!ALLOWED_RELATION_TYPES.contains(relationType)) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    ErrorCode.BIZ_BAD_REQUEST,
                    "关系类型不合法，可选值：" + ALLOWED_RELATION_TYPES
            );
        }

        try (Session session = neo4jDriver.session()) {
            String countCypher = String.format(
                    "MATCH (a:Knowledge {id: $sourceId})-[r:%s]->(b:Knowledge {id: $targetId}) RETURN count(r) AS c",
                    relationType
            );
            long relationCount = readLong(
                    session,
                    countCypher,
                    Values.parameters("sourceId", sourceId, "targetId", targetId),
                    "c"
            );
            if (relationCount <= 0) {
                throw new BusinessException(
                        ErrorCode.NOT_FOUND,
                        ErrorCode.BIZ_NOT_FOUND,
                        "关系不存在，无法删除"
                );
            }

            String deleteCypher = String.format(
                    "MATCH (a:Knowledge {id: $sourceId})-[r:%s]->(b:Knowledge {id: $targetId}) DELETE r",
                    relationType
            );
            session.run(
                    deleteCypher,
                    Values.parameters("sourceId", sourceId, "targetId", targetId)
            );
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error deleting graph relation {}->{}({}): {}", sourceId, targetId, relationType, e.getMessage());
            throw new BusinessException(
                    ErrorCode.INTERNAL_SERVER_ERROR,
                    ErrorCode.BIZ_INTERNAL_SERVER_ERROR,
                    "删除图谱关系失败，请稍后重试"
            );
        }
    }

    public List<RelatedDocumentVO> getRelatedDocuments(String nodeId, Integer limit) {
        int size = Math.max(1, Math.min(limit == null ? 6 : limit, 20));
        try (Session session = neo4jDriver.session()) {
            Result result = session.run(
                    "MATCH (n:Knowledge {id: $id}) RETURN n",
                    Values.parameters("id", nodeId)
            );
            if (!result.hasNext()) {
                throw new BusinessException(
                        ErrorCode.NOT_FOUND,
                        ErrorCode.BIZ_NOT_FOUND,
                        "节点不存在: " + nodeId
                );
            }

            Node node = result.next().get("n").asNode();
            String nodeName = node.containsKey("name") ? node.get("name").asString("") : "";
            String nodeCategory = node.containsKey("category") ? node.get("category").asString("") : "";
            List<String> nodeKeywords = node.containsKey("keywords")
                    ? node.get("keywords").asList(v -> v.asString(""))
                    : List.of();

            List<KnowledgeDocument> docs = documentMapper.selectList(null);
            List<ScoredRelatedDocument> scored = docs.stream()
                    .map(doc -> scoreRelatedDocument(doc, nodeId, nodeName, nodeCategory, nodeKeywords))
                    .filter(item -> item.score > 0.0)
                    .sorted(Comparator.comparingDouble((ScoredRelatedDocument item) -> item.score).reversed())
                    .limit(size)
                    .toList();

            return scored.stream()
                    .map(item -> RelatedDocumentVO.builder()
                            .id(item.document.getId())
                            .title(item.document.getTitle())
                            .category(item.document.getCategory())
                            .createdAt(item.document.getCreatedAt())
                            .score(item.score)
                            .reason(item.reason)
                            .build())
                    .toList();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error loading related documents for node {}: {}", nodeId, e.getMessage());
            throw new BusinessException(
                    ErrorCode.INTERNAL_SERVER_ERROR,
                    ErrorCode.BIZ_INTERNAL_SERVER_ERROR,
                    "加载关联知识文档失败，请稍后重试"
            );
        }
    }

    public GraphHealthVO getHealthOverview() {
        long totalNodes = 0L;
        long totalEdges = 0L;
        long relationTypeCount = 0L;
        long isolatedNodeCount = 0L;
        long selfLoopEdgeCount = 0L;
        long duplicateEdgeGroupCount = 0L;
        long duplicateEdgeExtraCount = 0L;
        long missingIdCount = 0L;
        long missingNameCount = 0L;
        long missingCategoryCount = 0L;
        long missingDescriptionCount = 0L;
        long missingDifficultyCount = 0L;
        long invalidRelationTypeCount = 0L;
        long invalidCategoryCount = 0L;
        boolean hasDependencyCycle = false;

        List<String> isolatedNodeSamples = new ArrayList<>();
        List<String> duplicateEdgeSamples = new ArrayList<>();
        List<String> cycleNodeSamples = new ArrayList<>();
        List<String> invalidRelationTypeSamples = new ArrayList<>();
        List<String> invalidCategorySamples = new ArrayList<>();

        try (Session session = neo4jDriver.session()) {
            totalNodes = readLong(session,
                    "MATCH (n:Knowledge) RETURN count(n) AS c",
                    "c"
            );

            totalEdges = readLong(session,
                    "MATCH (:Knowledge)-[r]->(:Knowledge) RETURN count(r) AS c",
                    "c"
            );

            relationTypeCount = readLong(session,
                    "MATCH ()-[r]->() RETURN count(DISTINCT type(r)) AS c",
                    "c"
            );

            Result isolatedResult = session.run(
                    "MATCH (n:Knowledge) " +
                            "WHERE NOT (n)--() " +
                            "RETURN count(n) AS c, collect(coalesce(n.id, toString(id(n))))[0..8] AS samples"
            );
            if (isolatedResult.hasNext()) {
                Record record = isolatedResult.next();
                isolatedNodeCount = record.get("c").asLong(0L);
                isolatedNodeSamples = record.get("samples").asList(v -> v.asString());
            }

            selfLoopEdgeCount = readLong(session,
                    "MATCH (n:Knowledge)-[r]->(n) RETURN count(r) AS c",
                    "c"
            );

            Result duplicateResult = session.run(
                    "MATCH (a:Knowledge)-[r]->(b:Knowledge) " +
                            "WITH coalesce(a.id, toString(id(a))) AS sourceId, type(r) AS relType, " +
                            "coalesce(b.id, toString(id(b))) AS targetId, count(r) AS c " +
                            "WHERE c > 1 " +
                            "RETURN count(*) AS groupCount, coalesce(sum(c - 1), 0) AS extraCount, " +
                            "collect(sourceId + '-[' + relType + ']->' + targetId + ' x' + toString(c))[0..8] AS samples"
            );
            if (duplicateResult.hasNext()) {
                Record record = duplicateResult.next();
                duplicateEdgeGroupCount = record.get("groupCount").asLong(0L);
                duplicateEdgeExtraCount = record.get("extraCount").asLong(0L);
                duplicateEdgeSamples = record.get("samples").asList(v -> v.asString());
            }

            Result missingFieldResult = session.run(
                    "MATCH (n:Knowledge) " +
                            "RETURN " +
                            "sum(CASE WHEN n.id IS NULL OR trim(toString(n.id)) = '' THEN 1 ELSE 0 END) AS missingIdCount, " +
                            "sum(CASE WHEN n.name IS NULL OR trim(toString(n.name)) = '' THEN 1 ELSE 0 END) AS missingNameCount, " +
                            "sum(CASE WHEN n.category IS NULL OR trim(toString(n.category)) = '' THEN 1 ELSE 0 END) AS missingCategoryCount, " +
                            "sum(CASE WHEN n.description IS NULL OR trim(toString(n.description)) = '' THEN 1 ELSE 0 END) AS missingDescriptionCount, " +
                            "sum(CASE WHEN n.difficulty IS NULL OR trim(toString(n.difficulty)) = '' THEN 1 ELSE 0 END) AS missingDifficultyCount"
            );
            if (missingFieldResult.hasNext()) {
                Record record = missingFieldResult.next();
                missingIdCount = record.get("missingIdCount").asLong(0L);
                missingNameCount = record.get("missingNameCount").asLong(0L);
                missingCategoryCount = record.get("missingCategoryCount").asLong(0L);
                missingDescriptionCount = record.get("missingDescriptionCount").asLong(0L);
                missingDifficultyCount = record.get("missingDifficultyCount").asLong(0L);
            }

            Result invalidRelationResult = session.run(
                    "MATCH ()-[r]->() " +
                            "WHERE NOT type(r) IN $allowedTypes " +
                            "RETURN count(r) AS c, collect(DISTINCT type(r))[0..8] AS samples",
                    Values.parameters("allowedTypes", new ArrayList<>(ALLOWED_RELATION_TYPES))
            );
            if (invalidRelationResult.hasNext()) {
                Record record = invalidRelationResult.next();
                invalidRelationTypeCount = record.get("c").asLong(0L);
                invalidRelationTypeSamples = record.get("samples").asList(v -> v.asString());
            }

            Result invalidCategoryResult = session.run(
                    "MATCH (n:Knowledge) " +
                            "WITH n, toLower(trim(coalesce(toString(n.category), ''))) AS normalizedCategory " +
                            "WHERE normalizedCategory <> '' AND NOT normalizedCategory IN $allowedCategories " +
                            "RETURN count(n) AS c, collect(coalesce(n.id, toString(id(n))) + ':' + normalizedCategory)[0..8] AS samples",
                    Values.parameters("allowedCategories", new ArrayList<>(ALLOWED_NODE_CATEGORIES))
            );
            if (invalidCategoryResult.hasNext()) {
                Record record = invalidCategoryResult.next();
                invalidCategoryCount = record.get("c").asLong(0L);
                invalidCategorySamples = record.get("samples").asList(v -> v.asString());
            }

            Result cycleResult = session.run(
                    "MATCH (n:Knowledge) " +
                            "WHERE EXISTS { MATCH (n)-[:DEPENDS_ON*1..8]->(n) } " +
                            "RETURN count(n) AS cycleNodeCount, collect(coalesce(n.id, toString(id(n))))[0..8] AS samples"
            );
            if (cycleResult.hasNext()) {
                Record record = cycleResult.next();
                hasDependencyCycle = record.get("cycleNodeCount").asLong(0L) > 0;
                cycleNodeSamples = record.get("samples").asList(v -> v.asString());
            }
        } catch (Exception e) {
            log.error("Error calculating graph health from Neo4j: {}", e.getMessage());
            throw new BusinessException(
                    ErrorCode.INTERNAL_SERVER_ERROR,
                    ErrorCode.BIZ_INTERNAL_SERVER_ERROR,
                    "图谱健康检查失败，请稍后重试"
            );
        }

        int healthScore = calculateHealthScore(
                totalNodes,
                totalEdges,
                isolatedNodeCount,
                selfLoopEdgeCount,
                duplicateEdgeExtraCount,
                missingIdCount + missingNameCount + missingCategoryCount + missingDescriptionCount + missingDifficultyCount,
                invalidRelationTypeCount,
                invalidCategoryCount,
                hasDependencyCycle
        );

        return GraphHealthVO.builder()
                .healthScore(healthScore)
                .totalNodes(totalNodes)
                .totalEdges(totalEdges)
                .relationTypeCount(relationTypeCount)
                .isolatedNodeCount(isolatedNodeCount)
                .selfLoopEdgeCount(selfLoopEdgeCount)
                .duplicateEdgeGroupCount(duplicateEdgeGroupCount)
                .duplicateEdgeExtraCount(duplicateEdgeExtraCount)
                .missingIdCount(missingIdCount)
                .missingNameCount(missingNameCount)
                .missingCategoryCount(missingCategoryCount)
                .missingDescriptionCount(missingDescriptionCount)
                .missingDifficultyCount(missingDifficultyCount)
                .invalidRelationTypeCount(invalidRelationTypeCount)
                .invalidCategoryCount(invalidCategoryCount)
                .hasDependencyCycle(hasDependencyCycle)
                .isolatedNodeSamples(isolatedNodeSamples)
                .duplicateEdgeSamples(duplicateEdgeSamples)
                .cycleNodeSamples(cycleNodeSamples)
                .invalidRelationTypeSamples(invalidRelationTypeSamples)
                .invalidCategorySamples(invalidCategorySamples)
                .build();
    }

    private ScoredRelatedDocument scoreRelatedDocument(
            KnowledgeDocument doc,
            String nodeId,
            String nodeName,
            String nodeCategory,
            List<String> nodeKeywords
    ) {
        String title = safeLower(doc.getTitle());
        String content = safeLower(doc.getContent());
        String category = safeLower(doc.getCategory());

        String normalizedNodeId = safeLower(nodeId);
        String normalizedNodeName = safeLower(nodeName);
        String normalizedNodeCategory = safeLower(nodeCategory);

        double score = 0.0;
        StringBuilder reason = new StringBuilder();

        if (!normalizedNodeName.isBlank() && title.contains(normalizedNodeName)) {
            score += 4.0;
            reason.append("标题命中节点名");
        } else if (!normalizedNodeName.isBlank() && content.contains(normalizedNodeName)) {
            score += 2.5;
            reason.append("内容命中节点名");
        }

        if (!normalizedNodeId.isBlank() && (title.contains(normalizedNodeId) || content.contains(normalizedNodeId))) {
            score += 1.5;
            if (!reason.isEmpty()) reason.append("；");
            reason.append("命中节点ID");
        }

        if (!normalizedNodeCategory.isBlank() && category.equals(normalizedNodeCategory)) {
            score += 2.0;
            if (!reason.isEmpty()) reason.append("；");
            reason.append("分类一致");
        }

        int keywordHits = 0;
        if (nodeKeywords != null) {
            for (String keyword : nodeKeywords) {
                String normalized = safeLower(keyword);
                if (normalized.length() < 2) continue;
                if (title.contains(normalized) || content.contains(normalized)) {
                    keywordHits++;
                }
            }
        }
        if (keywordHits > 0) {
            score += Math.min(3.0, keywordHits * 0.8);
            if (!reason.isEmpty()) reason.append("；");
            reason.append("关键词命中 ").append(keywordHits).append(" 个");
        }

        if (reason.isEmpty()) {
            reason.append("语义相关");
        }

        return new ScoredRelatedDocument(doc, score, reason.toString());
    }

    private String safeLower(String text) {
        return text == null ? "" : text.toLowerCase().trim();
    }

    private long readLong(Session session, String cypher, String key) {
        Result result = session.run(cypher);
        if (!result.hasNext()) return 0L;
        return result.next().get(key).asLong(0L);
    }

    private long readLong(Session session, String cypher, org.neo4j.driver.Value params, String key) {
        Result result = session.run(cypher, params);
        if (!result.hasNext()) return 0L;
        return result.next().get(key).asLong(0L);
    }

    private String normalizeRelationType(String relationType) {
        return relationType == null ? "" : relationType.trim().toUpperCase(Locale.ROOT);
    }

    private int calculateHealthScore(
            long totalNodes,
            long totalEdges,
            long isolatedNodeCount,
            long selfLoopEdgeCount,
            long duplicateEdgeExtraCount,
            long missingFieldCount,
            long invalidRelationTypeCount,
            long invalidCategoryCount,
            boolean hasDependencyCycle
    ) {
        int score = 100;
        score -= penaltyByRate(isolatedNodeCount, totalNodes, 25);
        score -= penaltyByRate(selfLoopEdgeCount, Math.max(1L, totalEdges), 15);
        score -= penaltyByRate(duplicateEdgeExtraCount, Math.max(1L, totalEdges), 20);
        score -= penaltyByRate(missingFieldCount, Math.max(1L, totalNodes * 5), 20);
        score -= penaltyByRate(invalidRelationTypeCount, Math.max(1L, totalEdges), 10);
        score -= penaltyByRate(invalidCategoryCount, Math.max(1L, totalNodes), 10);
        if (hasDependencyCycle) {
            score -= 10;
        }
        return Math.max(0, score);
    }

    private int penaltyByRate(long badCount, long totalCount, int maxPenalty) {
        if (badCount <= 0 || totalCount <= 0 || maxPenalty <= 0) return 0;
        double rate = (double) badCount / (double) totalCount;
        int penalty = (int) Math.round(rate * maxPenalty);
        if (penalty <= 0) penalty = 1;
        return Math.min(maxPenalty, penalty);
    }

    private KnowledgeNodeVO mapToKnowledgeNodeVO(Node node) {
        List<String> keywords = new ArrayList<>();
        if (node.containsKey("keywords")) {
            node.get("keywords").asList(v -> v.asString()).forEach(keywords::add);
        }
        String nodeId = node.containsKey("id") ? node.get("id").asString() : String.valueOf(node.id());
        String rawDescription = node.containsKey("description") ? node.get("description").asString() : "";
        String localizedDescription = localizeDescription(nodeId, rawDescription);

        return KnowledgeNodeVO.builder()
                .id(nodeId)
                .name(node.containsKey("name") ? node.get("name").asString() : "")
                .category(node.containsKey("category") ? node.get("category").asString() : "")
                .description(localizedDescription)
                .difficulty(node.containsKey("difficulty") ? node.get("difficulty").asString() : "")
                .keywords(keywords)
                .build();
    }

    private String localizeDescription(String nodeId, String rawDescription) {
        if (rawDescription == null || rawDescription.isBlank()) {
            return NODE_DESCRIPTION_ZH.getOrDefault(nodeId, "");
        }
        if (containsChinese(rawDescription)) {
            return rawDescription;
        }
        return NODE_DESCRIPTION_ZH.getOrDefault(nodeId, rawDescription);
    }

    private boolean containsChinese(String text) {
        for (int i = 0; i < text.length(); i++) {
            Character.UnicodeScript script = Character.UnicodeScript.of(text.charAt(i));
            if (script == Character.UnicodeScript.HAN) {
                return true;
            }
        }
        return false;
    }

    private static final class ScoredRelatedDocument {
        private final KnowledgeDocument document;
        private final double score;
        private final String reason;

        private ScoredRelatedDocument(KnowledgeDocument document, double score, String reason) {
            this.document = document;
            this.score = score;
            this.reason = reason;
        }
    }
}
