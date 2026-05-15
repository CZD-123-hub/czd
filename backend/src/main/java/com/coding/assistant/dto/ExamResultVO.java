package com.coding.assistant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamResultVO {

    private Long sessionId;
    private Integer totalQuestions;
    private Integer answeredCount;
    private Integer correctCount;
    private Double score;
    private String grade;
    private Boolean timeout;
}
