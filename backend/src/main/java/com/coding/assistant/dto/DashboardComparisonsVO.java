package com.coding.assistant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardComparisonsVO {
    private MetricComparisonVO totalDays;
    private MetricComparisonVO totalChats;
    private MetricComparisonVO totalSnippets;
    private MetricComparisonVO knowledgeCoverage;
}

