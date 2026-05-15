package com.coding.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class GraphRelationCreateRequest {

    @NotBlank(message = "源节点ID不能为空")
    @Size(max = 64, message = "源节点ID长度不能超过64")
    private String sourceId;

    @NotBlank(message = "目标节点ID不能为空")
    @Size(max = 64, message = "目标节点ID长度不能超过64")
    private String targetId;

    @NotBlank(message = "关系类型不能为空")
    @Size(max = 32, message = "关系类型长度不能超过32")
    private String relationType;
}

