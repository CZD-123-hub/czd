package com.coding.assistant.controller;

import com.coding.assistant.dto.ApiResponse;
import com.coding.assistant.dto.EdgeVO;
import com.coding.assistant.dto.GraphHealthVO;
import com.coding.assistant.dto.GraphNodeCreateRequest;
import com.coding.assistant.dto.GraphRelationCreateRequest;
import com.coding.assistant.dto.GraphVO;
import com.coding.assistant.dto.KnowledgeNodeVO;
import com.coding.assistant.dto.RelatedDocumentVO;
import com.coding.assistant.security.SecurityUtil;
import com.coding.assistant.service.GraphService;
import com.coding.assistant.service.LearningActionType;
import com.coding.assistant.service.ProgressService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/graph")
@RequiredArgsConstructor
public class GraphController {

    private final GraphService graphService;
    private final ProgressService progressService;

    // 返回图谱总览数据（nodes + edges），用于图谱页首次渲染。
    @GetMapping("/overview")
    public ApiResponse<GraphVO> getOverview() {
        Long userId = SecurityUtil.getCurrentUserId();
        progressService.recordAction(userId, LearningActionType.GRAPH_EXPLORE, "overview");
        GraphVO graph = graphService.getOverview();
        return ApiResponse.success(graph);
    }

    // 返回单个节点详情，用于右侧详情面板展示。
    @GetMapping({"/node/{id}", "/nodes/{id}"})
    public ApiResponse<KnowledgeNodeVO> getNodeDetail(@PathVariable String id) {
        Long userId = SecurityUtil.getCurrentUserId();
        progressService.recordAction(userId, LearningActionType.GRAPH_EXPLORE, id);
        KnowledgeNodeVO node = graphService.getNodeDetail(id);
        return ApiResponse.success(node);
    }

    // 按 depth 返回某个节点周边的子图数据。
    @GetMapping({"/node/{id}/neighbors", "/nodes/{id}/neighbors"})
    public ApiResponse<GraphVO> getNeighbors(
            @PathVariable String id,
            @RequestParam(defaultValue = "1") Integer depth
    ) {
        Long userId = SecurityUtil.getCurrentUserId();
        progressService.recordAction(userId, LearningActionType.GRAPH_EXPLORE, id);
        GraphVO graph = graphService.getNeighbors(id, depth);
        return ApiResponse.success(graph);
    }

    // 在图谱中按关键词搜索，并返回命中子图。
    @GetMapping("/search")
    public ApiResponse<GraphVO> search(@RequestParam String keyword) {
        GraphVO graph = graphService.searchGraph(keyword);
        return ApiResponse.success(graph);
    }

    // 在 Neo4j 中创建新的 Knowledge 节点。
    @PostMapping("/node")
    public ApiResponse<KnowledgeNodeVO> createNode(@Valid @RequestBody GraphNodeCreateRequest request) {
        KnowledgeNodeVO node = graphService.createNode(request);
        return ApiResponse.success(node);
    }

    // 在两个 Knowledge 节点之间创建有向关系。
    @PostMapping("/relation")
    public ApiResponse<EdgeVO> createRelation(@Valid @RequestBody GraphRelationCreateRequest request) {
        EdgeVO relation = graphService.createRelation(request);
        return ApiResponse.success(relation);
    }

    // 按节点 id 删除节点。
    @DeleteMapping("/node/{id}")
    public ApiResponse<Void> deleteNode(@PathVariable String id) {
        graphService.deleteNode(id);
        return ApiResponse.success();
    }

    // 按 source/target/type 删除一条有向关系。
    @DeleteMapping("/relation")
    public ApiResponse<Void> deleteRelation(@Valid @RequestBody GraphRelationCreateRequest request) {
        graphService.deleteRelation(request);
        return ApiResponse.success();
    }

    // 返回图谱健康指标，供治理面板展示。
    @GetMapping("/health")
    public ApiResponse<GraphHealthVO> getHealth() {
        GraphHealthVO health = graphService.getHealthOverview();
        return ApiResponse.success(health);
    }

    // 返回节点关联的文档列表，供详情面板展示。
    @GetMapping({"/node/{id}/related-documents", "/nodes/{id}/related-documents"})
    public ApiResponse<List<RelatedDocumentVO>> getRelatedDocuments(
            @PathVariable String id,
            @RequestParam(defaultValue = "6") Integer limit
    ) {
        List<RelatedDocumentVO> documents = graphService.getRelatedDocuments(id, limit);
        return ApiResponse.success(documents);
    }
}
