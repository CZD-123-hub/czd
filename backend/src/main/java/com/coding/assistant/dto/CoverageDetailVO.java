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
public class CoverageDetailVO {
    private int coveredCoreActions;
    private int totalCoreActions;
    private List<String> coveredActionKeys;
}

