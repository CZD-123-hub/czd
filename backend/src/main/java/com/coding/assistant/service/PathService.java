package com.coding.assistant.service;

import com.coding.assistant.dto.LearningNodeVO;
import com.coding.assistant.dto.LearningPathVO;
import com.coding.assistant.dto.PathGenerateRequest;
import com.coding.assistant.entity.LearningNode;
import com.coding.assistant.entity.LearningPath;
import com.coding.assistant.exception.BusinessException;
import com.coding.assistant.mapper.LearningNodeMapper;
import com.coding.assistant.mapper.LearningPathMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Values;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PathService {

    private final LearningPathMapper learningPathMapper;
    private final LearningNodeMapper learningNodeMapper;
    private final Driver neo4jDriver;
    private final ChatService chatService;  // 使用 ChatService 代替 LLMService

    @Transactional
    public LearningPathVO generate(Long userId, PathGenerateRequest request) {
        Set<String> knownIds = request.getKnownKnowledgeIds() != null ?
                new HashSet<>(request.getKnownKnowledgeIds()) : new HashSet<>();

        // Query Neo4j for dependency graph related to target
        List<Map<String, String>> dependencies = new ArrayList<>();
        Map<String, String> nodeNames = new HashMap<>();

        try (Session session = neo4jDriver.session()) {
            // Find nodes related to the target topic
            String cypher = "MATCH (target:Knowledge) " +
                    "WHERE toLower(target.name) CONTAINS toLower($target) " +
                    "WITH target " +
                    "MATCH path = (target)-[:DEPENDS_ON*0..5]->(dep:Knowledge) " +
                    "UNWIND nodes(path) as n " +
                    "WITH DISTINCT n " +
                    "OPTIONAL MATCH (n)-[:DEPENDS_ON]->(prereq:Knowledge) " +
                    "RETURN n.id as nodeId, n.name as nodeName, prereq.id as prereqId";

            Result result = session.run(cypher, Values.parameters("target", request.getTarget()));

            while (result.hasNext()) {
                Record record = result.next();
                String nodeId = record.get("nodeId").isNull() ? null : record.get("nodeId").asString();
                String nodeName = record.get("nodeName").isNull() ? "" : record.get("nodeName").asString();
                String prereqId = record.get("prereqId").isNull() ? null : record.get("prereqId").asString();

                if (nodeId != null) {
                    nodeNames.put(nodeId, nodeName);
                    if (prereqId != null) {
                        Map<String, String> dep = new HashMap<>();
                        dep.put("from", prereqId);
                        dep.put("to", nodeId);
                        dependencies.add(dep);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error querying Neo4j for learning path: {}", e.getMessage());
        }

        // Topological sort excluding known nodes
        List<String> sortedNodes = topologicalSort(nodeNames.keySet(), dependencies);
        sortedNodes.removeAll(knownIds);

        // 如果知识图谱中没有找到节点，使用 AI 生成
        if (sortedNodes.isEmpty()) {
            log.info("No nodes found in knowledge graph for target '{}', using AI generation", request.getTarget());
            return generateByAI(userId, request);
        }

        // Save learning path
        LearningPath path = LearningPath.builder()
                .userId(userId)
                .target(request.getTarget())
                .status("active")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        learningPathMapper.insert(path);

        // Save learning nodes
        List<LearningNodeVO> nodeVOs = new ArrayList<>();
        for (int i = 0; i < sortedNodes.size(); i++) {
            String knowledgeId = sortedNodes.get(i);
            LearningNode node = LearningNode.builder()
                    .pathId(path.getId())
                    .knowledgeId(knowledgeId)
                    .nodeOrder(i + 1)
                    .status("todo")
                    .build();
            learningNodeMapper.insert(node);

            nodeVOs.add(LearningNodeVO.builder()
                    .id(node.getId())
                    .knowledgeId(knowledgeId)
                    .knowledgeName(nodeNames.getOrDefault(knowledgeId, knowledgeId))
                    .nodeOrder(i + 1)
                    .status("todo")
                    .resourceUrls(List.of())
                    .build());
        }

        log.info("Learning path generated for user {} with target '{}', {} nodes", userId, request.getTarget(), sortedNodes.size());

        return LearningPathVO.builder()
                .id(path.getId())
                .target(path.getTarget())
                .status(path.getStatus())
                .nodes(nodeVOs)
                .createdAt(path.getCreatedAt())
                .build();
    }

    /**
     * 使用 AI 生成学习路径
     */
    private LearningPathVO generateByAI(Long userId, PathGenerateRequest request) {
        try {
            // 构建 AI prompt
            String prompt = buildPrompt(request.getTarget(), request.getKnownKnowledgeIds());

            log.info("Calling AI to generate learning path for target: {}", request.getTarget());

            // 使用 ChatService 调用 AI
            String aiResponse = chatService.generateLearningPath(prompt);

            if (aiResponse == null || aiResponse.isEmpty()) {
                throw new BusinessException(400, "无法生成学习路径，请尝试其他学习目标");
            }

            log.info("AI response received, length: {}", aiResponse.length());

            // 解析 AI 返回的节点列表
            List<String> aiNodes = parseAIGeneratedNodes(aiResponse);

            if (aiNodes.isEmpty()) {
                log.warn("No nodes parsed from AI response: {}", aiResponse);
                throw new BusinessException(400, "无法生成学习路径，请尝试其他学习目标");
            }

            // 保存学习路径
            LearningPath path = LearningPath.builder()
                    .userId(userId)
                    .target(request.getTarget())
                    .status("active")
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            learningPathMapper.insert(path);

            // 保存学习节点
List<LearningNodeVO> nodeVOs = new ArrayList<>();
for (int i = 0; i < aiNodes.size(); i++) {
    String nodeName = aiNodes.get(i);
    // 生成一个虚拟的 knowledgeId
    String knowledgeId = "ai_" + UUID.randomUUID().toString().substring(0, 8);

    LearningNode node = LearningNode.builder()
            .pathId(path.getId())
            .knowledgeId(knowledgeId)
            .customName(nodeName)  // 保存原始名称
            .nodeOrder(i + 1)
            .status("todo")
            .build();
    learningNodeMapper.insert(node);

    nodeVOs.add(LearningNodeVO.builder()
            .id(node.getId())
            .knowledgeId(knowledgeId)
            .knowledgeName(nodeName)  // 直接使用原始名称
            .nodeOrder(i + 1)
            .status("todo")
            .resourceUrls(List.of())
            .build());
}

            log.info("AI generated learning path for user {} with target '{}', {} nodes", userId, request.getTarget(), aiNodes.size());

            return LearningPathVO.builder()
                    .id(path.getId())
                    .target(path.getTarget())
                    .status(path.getStatus())
                    .nodes(nodeVOs)
                    .createdAt(path.getCreatedAt())
                    .build();

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI generation failed: {}", e.getMessage(), e);
            throw new BusinessException(400, "No learning nodes found for the given target");
        }
    }

    /**
     * 构建 AI prompt
     */
    private String buildPrompt(String target, List<String> knownKnowledgeIds) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一个编程学习专家。请为以下学习目标生成一个详细的学习路径：\n\n");
        prompt.append("学习目标：").append(target).append("\n\n");

        if (knownKnowledgeIds != null && !knownKnowledgeIds.isEmpty()) {
            prompt.append("用户已掌握的知识：").append(String.join("、", knownKnowledgeIds)).append("\n\n");
        }

        prompt.append("请生成一个包含5-8个学习节点的学习路径，每个节点应该是一个具体的学习主题。\n");
        prompt.append("节点应该从基础到高级，循序渐进。\n\n");
        prompt.append("请严格按照以下JSON数组格式返回，不要有任何其他内容：\n");
        prompt.append("[\"节点1\", \"节点2\", \"节点3\", \"节点4\", \"节点5\"]\n\n");
        prompt.append("注意：只返回JSON数组，不要包含解释、说明或其他文字。");

        return prompt.toString();
    }

    /**
     * 解析 AI 返回的 JSON 数组
     */
    private List<String> parseAIGeneratedNodes(String aiResponse) {
        List<String> nodes = new ArrayList<>();
        try {
            // 清理响应内容，移除可能的 markdown 代码块标记
            String cleaned = aiResponse.trim();
            if (cleaned.startsWith("```json")) {
                cleaned = cleaned.substring(7);
            }
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.substring(3);
            }
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substring(0, cleaned.length() - 3);
            }
            cleaned = cleaned.trim();

            // 尝试提取 JSON 数组
            int start = cleaned.indexOf('[');
            int end = cleaned.lastIndexOf(']');
            if (start != -1 && end != -1 && start < end) {
                String jsonArray = cleaned.substring(start, end + 1);
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                nodes = mapper.readValue(jsonArray, mapper.getTypeFactory().constructCollectionType(List.class, String.class));
                log.info("Parsed {} nodes from AI response", nodes.size());
            } else {
                log.warn("No JSON array found in AI response: {}", cleaned.substring(0, Math.min(200, cleaned.length())));
            }
        } catch (Exception e) {
            log.warn("Failed to parse AI response: {}", e.getMessage());
        }
        return nodes;
    }

    public List<LearningPathVO> list(Long userId) {
        List<LearningPath> paths = learningPathMapper.selectList(
                new LambdaQueryWrapper<LearningPath>()
                        .eq(LearningPath::getUserId, userId)
                        .orderByDesc(LearningPath::getCreatedAt)
        );

        return paths.stream().map(path -> {
            List<LearningNode> nodes = learningNodeMapper.selectByPathId(path.getId());
            List<LearningNodeVO> nodeVOs = nodes.stream()
                    .map(this::toLearningNodeVO)
                    .collect(Collectors.toList());

            return LearningPathVO.builder()
                    .id(path.getId())
                    .target(path.getTarget())
                    .status(path.getStatus())
                    .nodes(nodeVOs)
                    .createdAt(path.getCreatedAt())
                    .build();
        }).collect(Collectors.toList());
    }

    @Transactional
public void deletePath(Long userId, Long pathId) {
    LearningPath path = learningPathMapper.selectById(pathId);
    if (path == null) {
        throw new BusinessException(404, "学习路径不存在");
    }
    if (!path.getUserId().equals(userId)) {
        throw new BusinessException(403, "无权删除此学习路径");
    }
    learningNodeMapper.deleteByPathId(pathId);
    learningPathMapper.deleteById(pathId);
    log.info("Learning path {} deleted by user {}", pathId, userId);
}
@Transactional
public void updateNodeStatus(Long userId, Long nodeId, String status) {
    LearningNode node = learningNodeMapper.selectById(nodeId);
    if (node == null) {
        throw new BusinessException(404, "学习节点不存在");
    }
    
    LearningPath path = learningPathMapper.selectById(node.getPathId());
    if (path == null || !path.getUserId().equals(userId)) {
        throw new BusinessException(403, "无权修改此节点");
    }
    
    node.setStatus(status);
    learningNodeMapper.updateById(node);
    
    path.setUpdatedAt(LocalDateTime.now());
    learningPathMapper.updateById(path);
    
    log.info("Learning node {} status updated to '{}' by user {}", nodeId, status, userId);
}
       


    private List<String> topologicalSort(Set<String> allNodes, List<Map<String, String>> dependencies) {
        Map<String, Set<String>> adjList = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();

        for (String node : allNodes) {
            adjList.put(node, new HashSet<>());
            inDegree.put(node, 0);
        }

        for (Map<String, String> dep : dependencies) {
            String from = dep.get("from");
            String to = dep.get("to");
            if (allNodes.contains(from) && allNodes.contains(to)) {
                adjList.get(from).add(to);
                inDegree.merge(to, 1, Integer::sum);
            }
        }

        Queue<String> queue = new LinkedList<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        List<String> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            String node = queue.poll();
            sorted.add(node);
            for (String neighbor : adjList.getOrDefault(node, Set.of())) {
                int newDegree = inDegree.get(neighbor) - 1;
                inDegree.put(neighbor, newDegree);
                if (newDegree == 0) {
                    queue.add(neighbor);
                }
            }
        }

        // Add any remaining nodes (circular deps)
        for (String node : allNodes) {
            if (!sorted.contains(node)) {
                sorted.add(node);
            }
        }

        return sorted;
    }

 private LearningNodeVO toLearningNodeVO(LearningNode node) {
    List<String> urls = new ArrayList<>();
    if (node.getResourceUrls() != null && !node.getResourceUrls().isEmpty()) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            urls = mapper.readValue(node.getResourceUrls(),
                    mapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception e) {
            log.warn("Failed to parse resource URLs for node {}: {}", node.getId(), e.getMessage());
        }
    }

    // 获取知识名称
    String knowledgeName;
    
    // 如果是 AI 生成的节点，使用 customName
    if (node.getKnowledgeId() != null && node.getKnowledgeId().startsWith("ai_") && node.getCustomName() != null) {
        knowledgeName = node.getCustomName();
    } else {
        knowledgeName = getKnowledgeName(node.getKnowledgeId());
    }

    return LearningNodeVO.builder()
            .id(node.getId())
            .knowledgeId(node.getKnowledgeId())
            .knowledgeName(knowledgeName)
            .nodeOrder(node.getNodeOrder())
            .status(node.getStatus())
            .resourceUrls(urls)
            .build();
}

    private String getKnowledgeName(String knowledgeId) {
        // 如果是 AI 生成的虚拟 ID，直接返回 ID 作为名称
        if (knowledgeId != null && knowledgeId.startsWith("ai_")) {
            return knowledgeId;
        }

        try (Session session = neo4jDriver.session()) {
            String cypher = "MATCH (n:Knowledge {id: $id}) RETURN n.name as name";
            Result result = session.run(cypher, Values.parameters("id", knowledgeId));
            if (result.hasNext()) {
                return result.next().get("name").asString();
            }
        } catch (Exception e) {
            log.warn("Failed to get knowledge name for id {}: {}", knowledgeId, e.getMessage());
        }
        return knowledgeId;
    }
}