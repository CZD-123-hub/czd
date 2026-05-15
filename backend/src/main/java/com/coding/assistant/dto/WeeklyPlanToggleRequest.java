package com.coding.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WeeklyPlanToggleRequest {
    @NotBlank(message = "planId is required")
    private String planId;

    @NotNull(message = "completed is required")
    private Boolean completed;
}
