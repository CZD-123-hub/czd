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
public class ExamSummaryVO {

    private Integer completedCount;
    private Double avgScore;
    private Double passRate;
    private Double latestScore;
    private String latestGrade;
    private String latestSubmittedAt;
    private List<ExamTrendPointVO> recentTrend;
}

