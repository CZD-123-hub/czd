package com.coding.assistant.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class PracticeGenerateRequest {

    private Long pathId;

    @Min(3)
    @Max(20)
    private Integer questionCount = 6;
}
