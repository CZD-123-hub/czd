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
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class GraphService {

    private final Driver neo4jDriver;
    private static final Map<String, String> NODE_DESCRIPTION_ZH = Map.ofEntries(
            Map.entry("java", "一种广泛使用的面向对象编程语言，强调可移植性与稳定性能。"),
            Map.entry("python", "一种高级解释型语言，语法简洁，适合脚本开发、数据分析与 AI。"),
            Map.entry("javascript", "Web 开发核心语言，可用于前端交互与 Node.js 后端开发。"),
            Map.entry("typescript", "JavaScript 的类型化超集，提供静态类型检查并编译为 JavaScript。"),
            Map.entry("sql", "用于关系型数据库查询与管理的结构化查询语言。"),
            Map.entry("spring-boot", "用于快速构建生产级 Spring 应用的框架，约定优于配置。"),
            Map.entry("spring-mvc", "Spring 体系中的 Web 框架，用于构建 REST API 和 Web 应用。"),
            Map.entry("spring-security", "提供认证与授权能力的安全框架，用于保护 Spring 应用。"),
            Map.entry("spring-data", "通过仓储抽象简化数据访问，支持关系型与 NoSQL 数据库。"),
            Map.entry("spring-cloud", "构建微服务体系的一组组件，涵盖注册发现、配置与网关等能力。"),
            Map.entry("mybatis", "通过 SQL 映射进行持久化的框架，便于精细控制数据库操作。"),
            Map.entry("mybatis-plus", "MyBatis 增强工具，提供通用 CRUD、分页和代码生成能力。"),
            Map.entry("jpa", "Java 持久化规范，用于对象关系映射（ORM）。"),
            Map.entry("maven", "Java 项目的构建与依赖管理工具，基于 pom.xml。"),
            Map.entry("vue3", "渐进式前端框架，适合构建响应式用户界面。"),
            Map.entry("react", "基于组件的前端库，用于构建交互式用户界面。"),
            Map.entry("angular", "基于 TypeScript 的完整前端框架，适合大型单页应用。"),
            Map.entry("nodejs", "基于 Chrome V8 的 JavaScript 运行时，常用于服务端开发。"),
            Map.entry("vite", "现代前端构建工具与开发服务器，启动快、构建高效。"),
            Map.entry("element-plus", "面向 Vue 3 的企业级 UI 组件库。"),
            Map.entry("vue-router", "Vue 官方路由库，负责页面路由与导航管理。"),
            Map.entry("pinia", "Vue 官方推荐状态管理库，轻量且类型友好。"),
            Map.entry("mysql", "开源关系型数据库管理系统。"),
            Map.entry("redis", "内存数据结构数据库，常用于缓存、消息与高并发场景。"),
            Map.entry("neo4j", "图数据库，以节点和关系形式存储与查询数据。"),
            Map.entry("mongodb", "面向文档的 NoSQL 数据库，使用类 JSON 文档存储。"),
            Map.entry("postgresql", "功能强大的开源对象关系型数据库。"),
            Map.entry("docker", "容器化平台，用于应用打包、分发与运行。"),
            Map.entry("kubernetes", "容器编排平台，用于容器集群管理与自动化运维。"),
            Map.entry("git", "分布式版本控制系统，用于代码协作与版本追踪。"),
            Map.entry("linux", "开源操作系统家族，广泛用于服务器与云环境。"),
            Map.entry("nginx", "高性能 Web 服务器与反向代理。"),
            Map.entry("jenkins", "开源 CI/CD 自动化服务器，用于持续集成与交付流程。"),
            Map.entry("data-structures", "数据组织基础，包括数组、链表、树、图、哈希表等。"),
            Map.entry("algorithms", "解决问题的方法集合，如排序、搜索、动态规划等。"),
            Map.entry("design-patterns", "软件设计中可复用的经典模式。"),
            Map.entry("oop", "以对象为核心的编程范式，强调封装、继承与多态。"),
            Map.entry("networking", "计算机网络基础，涵盖 TCP/IP、HTTP、DNS 等协议。"),
            Map.entry("rest-api", "基于 REST 思想设计网络 API 的架构风格。"),
            Map.entry("microservices", "将系统拆分为松耦合、可独立部署服务的架构方式。"),
            Map.entry("jwt", "JSON Web Token，用于无状态认证与身份传递。"),
            Map.entry("ci-cd", "持续集成与持续交付实践，加快质量反馈与发布效率。"),
            Map.entry("rag", "检索增强生成，通过先检索相关知识再生成回答来提升准确性。")
    );

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
}
