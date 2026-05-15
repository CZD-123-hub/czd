package com.coding.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PracticeAnswerRequest {

    @NotNull
    private Long questionId;

    @NotBlank
    private String answer;
}
