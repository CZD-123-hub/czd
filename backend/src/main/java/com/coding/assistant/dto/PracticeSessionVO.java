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
public class PracticeSessionVO {

    private Long id;
    private Long pathId;
    private String mode;
    private String status;
    private Integer totalQuestions;
    private Integer answeredCount;
    private Integer correctCount;
    private Integer durationMinutes;
    private Double score;
    private String grade;
    private LocalDateTime createdAt;
    private LocalDateTime submittedAt;
    private List<PracticeQuestionVO> questions;
}
