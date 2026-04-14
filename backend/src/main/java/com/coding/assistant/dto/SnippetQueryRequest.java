package com.coding.assistant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SnippetQueryRequest {

    private String keyword;

    private String tag;

    @Builder.Default
    private int page = 1;

    @Builder.Default
    private int size = 20;
}