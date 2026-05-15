package com.coding.assistant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeeklyPlanItemVO {
    private String id;
    private String title;
    private String description;
    private String expectedImpact;
    private boolean completed;
}
