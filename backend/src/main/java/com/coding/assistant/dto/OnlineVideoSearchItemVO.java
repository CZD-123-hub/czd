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
public class OnlineVideoSearchItemVO {

    private String externalId;
    private String title;
    private String description;
    private String platform;
    private String url;
    private String embedUrl;
    private String coverUrl;
    private Integer durationSeconds;
    private List<String> tags;
}

