package com.coding.assistant.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ExamSubmitRequest {

    @Valid
    private List<ExamAnswerItem> answers = new ArrayList<>();

    @Data
    public static class ExamAnswerItem {
        @NotNull
        private Long questionId;
        @NotBlank
        private String answer;
    }
}
