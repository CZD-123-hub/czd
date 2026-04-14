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
public class DashboardVO {

    private int totalDays;
    private int totalChats;
    private int totalSnippets;
    private double knowledgeCoverage;
    private List<ActivityVO> recentActivity;
}