package com.coding.assistant.controller;

import com.coding.assistant.dto.ApiResponse;
import com.coding.assistant.dto.DocumentCreateRequest;
import com.coding.assistant.dto.KnowledgeDocumentVO;
import com.coding.assistant.dto.RelatedGraphNodeVO;
import com.coding.assistant.entity.KnowledgeDocument;
import com.coding.assistant.security.SecurityUtil;
import com.coding.assistant.service.DocumentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @GetMapping
    public ApiResponse<List<KnowledgeDocumentVO>> list(
            @RequestParam(defaultValue = "false") Boolean savedOnly
    ) {
        Long userId = SecurityUtil.getCurrentUserId();
        List<KnowledgeDocumentVO> documents = documentService.listAll(userId, Boolean.TRUE.equals(savedOnly));
        return ApiResponse.success(documents);
    }

    @PostMapping
    public ApiResponse<KnowledgeDocumentVO> add(@Valid @RequestBody DocumentCreateRequest body) {
        KnowledgeDocument doc = documentService.addDocument(
                body.getTitle(),
                body.getContent(),
                body.getCategory()
        );
        return ApiResponse.success(toVO(doc));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        documentService.deleteDocument(id);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/favorite")
    public ApiResponse<Void> favorite(
            @PathVariable Long id,
            @RequestParam(defaultValue = "true") Boolean favorite
    ) {
        Long userId = SecurityUtil.getCurrentUserId();
        documentService.toggleFavorite(userId, id, Boolean.TRUE.equals(favorite));
        return ApiResponse.success();
    }

    @GetMapping("/{id}/related-nodes")
    public ApiResponse<List<RelatedGraphNodeVO>> relatedNodes(
            @PathVariable Long id,
            @RequestParam(defaultValue = "8") Integer limit
    ) {
        return ApiResponse.success(documentService.getRelatedNodes(id, limit));
    }

    private KnowledgeDocumentVO toVO(KnowledgeDocument doc) {
        return KnowledgeDocumentVO.builder()
                .id(doc.getId())
                .title(doc.getTitle())
                .content(doc.getContent())
                .category(doc.getCategory())
                .saved(Boolean.FALSE)
                .createdAt(doc.getCreatedAt())
                .build();
    }

}
