package com.coding.assistant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PracticeAnswerResultVO {

    private Long sessionId;
    private Long questionId;
    private String answer;
    private String correctAnswer;
    private Boolean correct;
    private String explanation;
    private Integer answeredCount;
    private Integer correctCount;
    private Boolean completed;
}
