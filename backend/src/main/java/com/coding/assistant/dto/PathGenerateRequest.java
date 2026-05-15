package com.coding.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PathGenerateRequest {

    @NotBlank(message = "target is required")
    private String target;

    private List<String> knownKnowledgeIds;
}
