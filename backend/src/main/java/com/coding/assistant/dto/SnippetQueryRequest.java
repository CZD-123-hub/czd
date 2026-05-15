package com.coding.assistant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SnippetQueryRequest {

    private String keyword;

    private String tag;

    private String language;

    @Min(value = 1, message = "page must be greater than 0")
    @Builder.Default
    private int page = 1;

    @Min(value = 1, message = "size must be greater than 0")
    @Max(value = 100, message = "size must be less than or equal to 100")
    @Builder.Default
    private int size = 20;
}
