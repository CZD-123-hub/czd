package com.coding.assistant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeNodeVO {

    private String id;
    private String name;
    private String category;
    private String description;
    private String difficulty;
    private List<String> keywords;
}