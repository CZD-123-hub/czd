package com.coding.assistant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeDocumentVO {

    private Long id;
    private String title;
    private String content;
    private String category;
    private Boolean saved;
    private LocalDateTime createdAt;
}
