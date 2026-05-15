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
public class GraphHealthVO {

    private Integer healthScore;

    private Long totalNodes;
    private Long totalEdges;
    private Long relationTypeCount;

    private Long isolatedNodeCount;
    private Long selfLoopEdgeCount;
    private Long duplicateEdgeGroupCount;
    private Long duplicateEdgeExtraCount;

    private Long missingIdCount;
    private Long missingNameCount;
    private Long missingCategoryCount;
    private Long missingDescriptionCount;
    private Long missingDifficultyCount;

    private Long invalidRelationTypeCount;
    private Long invalidCategoryCount;

    private Boolean hasDependencyCycle;

    private List<String> isolatedNodeSamples;
    private List<String> duplicateEdgeSamples;
    private List<String> cycleNodeSamples;
    private List<String> invalidRelationTypeSamples;
    private List<String> invalidCategorySamples;
}
