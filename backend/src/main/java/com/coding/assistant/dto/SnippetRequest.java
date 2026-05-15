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
public class SnippetRequest {

    @NotBlank(message = "title is required")
    private String title;

    @NotBlank(message = "code is required")
    private String code;

    @NotBlank(message = "language is required")
    private String language;

    private String description;

    private List<String> tags;
}
