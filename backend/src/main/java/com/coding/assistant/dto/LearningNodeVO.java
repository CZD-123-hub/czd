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
public class LearningNodeVO {

    private Long id;
    private String knowledgeId;
    private String knowledgeName;
    private Integer nodeOrder;
    private String status;
    private List<String> resourceUrls;
    private List<NodeDocumentVO> recommendedDocuments;
    private List<NodeVideoVO> recommendedVideos;
}
