package com.coding.assistant.controller;

import com.coding.assistant.dto.ApiResponse;
import com.coding.assistant.entity.KnowledgeDocument;
import com.coding.assistant.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @GetMapping
    public ApiResponse<List<KnowledgeDocument>> list() {
        return ApiResponse.success(documentService.listAll());
    }

    @PostMapping
    public ApiResponse<KnowledgeDocument> add(@RequestBody Map<String, String> body) {
        KnowledgeDocument doc = documentService.addDocument(
                body.get("title"),
                body.get("content"),
                body.get("category")
        );
        return ApiResponse.success(doc);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        documentService.deleteDocument(id);
        return ApiResponse.success();
    }
}
