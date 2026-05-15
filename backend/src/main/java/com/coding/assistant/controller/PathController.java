package com.coding.assistant.controller;

import com.coding.assistant.dto.*;
import com.coding.assistant.security.SecurityUtil;
import com.coding.assistant.service.LearningActionType;
import com.coding.assistant.service.PathService;
import com.coding.assistant.service.ProgressService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/path")
@RequiredArgsConstructor
public class PathController {

    private final PathService pathService;
    private final ProgressService progressService;

    @PostMapping("/generate")
    public ApiResponse<LearningPathVO> generate(@Valid @RequestBody PathGenerateRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        LearningPathVO path = pathService.generate(userId, request);
        progressService.recordAction(userId, LearningActionType.PATH_LEARN, String.valueOf(path.getId()));
        return ApiResponse.success(path);
    }

    @GetMapping("/list")
    public ApiResponse<List<LearningPathVO>> list() {
        Long userId = SecurityUtil.getCurrentUserId();
        List<LearningPathVO> paths = pathService.list(userId);
        return ApiResponse.success(paths);
    }

    @PutMapping("/node/{id}/status")
    public ApiResponse<Void> updateNodeStatus(@PathVariable Long id,
                                               @Valid @RequestBody NodeStatusRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        pathService.updateNodeStatus(userId, id, request.getStatus());
        progressService.recordAction(userId, LearningActionType.PATH_LEARN, String.valueOf(id));
        return ApiResponse.success();
    }
    /**
     * 删除学习路径
     */
    @DeleteMapping("/{pathId}")
    public ApiResponse<Void> deletePath(@PathVariable Long pathId) {
        Long userId = SecurityUtil.getCurrentUserId();
        pathService.deletePath(userId, pathId);
        return ApiResponse.success();
    }
}
