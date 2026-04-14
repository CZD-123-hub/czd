package com.coding.assistant.dto;

import lombok.Data;

@Data
public class WeeklyPlanToggleRequest {
    private String planId;
    private Boolean completed;
}
