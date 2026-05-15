package com.coding.assistant.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class ExamGenerateRequest {

    private Long pathId;

    @Min(5)
    @Max(30)
    private Integer questionCount = 10;

    @Min(5)
    @Max(180)
    private Integer durationMinutes = 20;
}
