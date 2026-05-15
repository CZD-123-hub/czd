package com.coding.assistant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HealthBreakdownVO {
    private int activeDaysScore;
    private int streakScore;
    private int avgActionsScore;
    private int activeDays;
    private int streak;
    private double avgDailyActions;
}

