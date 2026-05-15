package com.coding.assistant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeVideoVO {

    private Long id;
    private String title;
    private String platform;
    private String url;
    private Integer durationSeconds;
}
