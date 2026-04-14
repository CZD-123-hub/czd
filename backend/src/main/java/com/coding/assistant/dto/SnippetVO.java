package com.coding.assistant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SnippetVO {

    private Long id;
    private String title;
    private String code;
    private String language;
    private String description;
    private List<String> tags;
    private Integer useCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}