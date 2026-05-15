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
public class PracticeQuestionVO {

    private Long id;
    private String knowledgeId;
    private String stem;
    private Integer order;
    private List<String> options;
    private String userAnswer;
    private String correctAnswer;
    private Boolean answered;
    private Boolean correct;
    private String explanation;
}
