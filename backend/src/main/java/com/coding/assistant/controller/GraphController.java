package com.coding.assistant.controller;

import com.coding.assistant.dto.ApiResponse;
import com.coding.assistant.dto.GraphVO;
import com.coding.assistant.dto.KnowledgeNodeVO;
import com.coding.assistant.security.SecurityUtil;
import com.coding.assistant.service.GraphService;
import com.coding.assistant.service.ProgressService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/graph")
@RequiredArgsConstructor
public class GraphController {

    private final GraphService graphService;
    private final ProgressService progressService;

    @GetMapping("/overview")
    public ApiResponse<GraphVO> getOverview() {
        Long userId = SecurityUtil.getCurrentUserId();
        progressService.recordAction(userId, "graph_explore", "overview");
        GraphVO graph = graphService.getOverview();
        return ApiResponse.success(graph);
    }

    @GetMapping("/node/{id}")
    public ApiResponse<KnowledgeNodeVO> getNodeDetail(@PathVariable String id) {
        Long userId = SecurityUtil.getCurrentUserId();
        progressService.recordAction(userId, "graph_explore", id);
        KnowledgeNodeVO node = graphService.getNodeDetail(id);
        return ApiResponse.success(node);
    }

    @GetMapping("/node/{id}/neighbors")
    public ApiResponse<GraphVO> getNeighbors(@PathVariable String id) {
        Long userId = SecurityUtil.getCurrentUserId();
        progressService.recordAction(userId, "graph_explore", id);
        GraphVO graph = graphService.getNeighbors(id);
        return ApiResponse.success(graph);
    }

    @GetMapping("/search")
    public ApiResponse<GraphVO> search(@RequestParam String keyword) {
        GraphVO graph = graphService.searchGraph(keyword);
        return ApiResponse.success(graph);
    }
}