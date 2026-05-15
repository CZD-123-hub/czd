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
public class OnlineVideoImportRequest {

    @NotBlank(message = "title is required")
    private String title;

    private String description;

    private String platform;

    @NotBlank(message = "url is required")
    private String url;

    private String coverUrl;

    private Integer durationSeconds;

    private String embedUrl;

    private String knowledgeId;

    private List<String> tags;

    private Boolean favorite;
}
