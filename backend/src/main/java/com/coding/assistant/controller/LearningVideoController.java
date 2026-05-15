package com.coding.assistant.controller;

import com.coding.assistant.dto.ApiResponse;
import com.coding.assistant.dto.LearningVideoVO;
import com.coding.assistant.dto.OnlineVideoImportRequest;
import com.coding.assistant.dto.OnlineVideoSearchItemVO;
import com.coding.assistant.dto.PageResult;
import com.coding.assistant.dto.VideoFavoriteRequest;
import com.coding.assistant.dto.VideoWatchRequest;
import com.coding.assistant.security.SecurityUtil;
import com.coding.assistant.service.LearningVideoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/learning-videos")
@RequiredArgsConstructor
public class LearningVideoController {

    private final LearningVideoService learningVideoService;

    @GetMapping("/search")
    public ApiResponse<PageResult<LearningVideoVO>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "12") Integer size,
            @RequestParam(defaultValue = "false") Boolean localFileOnly
    ) {
        Long userId = SecurityUtil.getCurrentUserId();
        return ApiResponse.success(learningVideoService.search(userId, keyword, page, size, localFileOnly));
    }

    @GetMapping("/{id}")
    public ApiResponse<LearningVideoVO> detail(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        return ApiResponse.success(learningVideoService.detail(userId, id));
    }

    @GetMapping("/favorites")
    public ApiResponse<List<LearningVideoVO>> favorites(@RequestParam(defaultValue = "24") Integer size) {
        Long userId = SecurityUtil.getCurrentUserId();
        return ApiResponse.success(learningVideoService.listFavorites(userId, size));
    }

    @GetMapping("/history")
    public ApiResponse<List<LearningVideoVO>> history(@RequestParam(defaultValue = "24") Integer size) {
        Long userId = SecurityUtil.getCurrentUserId();
        return ApiResponse.success(learningVideoService.listHistory(userId, size));
    }

    @GetMapping("/recommend")
    public ApiResponse<List<LearningVideoVO>> recommend(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Long conversationId,
            @RequestParam(defaultValue = "6") Integer size
    ) {
        Long userId = SecurityUtil.getCurrentUserId();
        return ApiResponse.success(learningVideoService.recommend(userId, query, conversationId, size));
    }

    @GetMapping("/search-online")
    public ApiResponse<List<OnlineVideoSearchItemVO>> searchOnline(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(defaultValue = "bilibili") String platform
    ) {
        SecurityUtil.getCurrentUserId();
        return ApiResponse.success(learningVideoService.searchOnline(keyword, size, platform));
    }

    @PostMapping("/import-online")
    public ApiResponse<LearningVideoVO> importOnline(@Valid @RequestBody OnlineVideoImportRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        return ApiResponse.success(learningVideoService.importOnline(userId, request));
    }

    @PostMapping(value = "/upload-local", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<LearningVideoVO> uploadLocal(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String title,
            @RequestParam(defaultValue = "false") Boolean favorite
    ) {
        Long userId = SecurityUtil.getCurrentUserId();
        return ApiResponse.success(learningVideoService.uploadLocal(userId, file, title, favorite));
    }

    @PostMapping("/{id}/favorite")
    public ApiResponse<Void> toggleFavorite(@PathVariable Long id, @Valid @RequestBody VideoFavoriteRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        learningVideoService.toggleFavorite(userId, id, request.getFavorite());
        return ApiResponse.success();
    }

    @PostMapping("/{id}/watch")
    public ApiResponse<Void> recordWatch(@PathVariable Long id, @Valid @RequestBody VideoWatchRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        learningVideoService.recordWatch(userId, id, request.getWatchedSeconds());
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}/history")
    public ApiResponse<Void> clearWatchHistory(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        learningVideoService.clearWatchHistory(userId, id);
        return ApiResponse.success();
    }
}
