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
public class LearningPathVO {

    private Long id;
    private String target;
    private String status;
    private List<LearningNodeVO> nodes;
    private LocalDateTime createdAt;
}