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
public class SmartInsightsVO {
    private int healthScore;
    private List<WeakAreaVO> weakAreas;
    private List<WeeklyPlanItemVO> weeklyPlan;
}
