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
public class LearningVideoVO {

    private Long id;
    private String title;
    private String description;
    private String platform;
    private String url;
    private String coverUrl;
    private Integer durationSeconds;
    private String knowledgeId;
    private List<String> tags;
    private boolean favorite;
    private Integer watchedSeconds;
    private LocalDateTime lastWatchedAt;
    private Double completionRate;
}
