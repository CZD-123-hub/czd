package com.coding.assistant.controller;

import com.coding.assistant.dto.*;
import com.coding.assistant.security.SecurityUtil;
import com.coding.assistant.service.ProgressService;
import com.coding.assistant.service.SnippetService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/snippets")
@RequiredArgsConstructor
public class SnippetController {

    private final SnippetService snippetService;
    private final ProgressService progressService;

    @PostMapping
    public ApiResponse<SnippetVO> create(@Valid @RequestBody SnippetRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        SnippetVO snippet = snippetService.create(userId, request);
        progressService.recordAction(userId, "code_save", String.valueOf(snippet.getId()));
        return ApiResponse.success(snippet);
    }

    @GetMapping
    public ApiResponse<PageResult<SnippetVO>> list(SnippetQueryRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        PageResult<SnippetVO> result = snippetService.list(userId, request);
        return ApiResponse.success(result);
    }

    @GetMapping("/recommend")
    public ApiResponse<List<SnippetVO>> recommend(@RequestParam Long conversationId) {
        Long userId = SecurityUtil.getCurrentUserId();
        List<SnippetVO> snippets = snippetService.recommend(userId, conversationId);
        return ApiResponse.success(snippets);
    }

    @PutMapping("/{id}")
    public ApiResponse<SnippetVO> update(@PathVariable Long id,
                                          @Valid @RequestBody SnippetRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        SnippetVO snippet = snippetService.update(userId, id, request);
        return ApiResponse.success(snippet);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        snippetService.delete(userId, id);
        return ApiResponse.success();
    }

    @GetMapping(value = "/export", produces = MediaType.APPLICATION_JSON_VALUE)
    public void exportAll(HttpServletResponse response) throws IOException {
        Long userId = SecurityUtil.getCurrentUserId();
        String json = snippetService.exportAll(userId);
        response.setContentType("application/json");
        response.setHeader("Content-Disposition", "attachment; filename=snippets.json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(json);
    }

    @PostMapping("/import")
    public ApiResponse<ImportResultVO> importSnippets(@RequestParam("file") MultipartFile file) throws IOException {
        Long userId = SecurityUtil.getCurrentUserId();
        String json = new String(file.getBytes(), StandardCharsets.UTF_8);
        ImportResultVO result = snippetService.importFromJson(userId, json);
        return ApiResponse.success(result);
    }
}