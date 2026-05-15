package com.coding.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class GraphNodeCreateRequest {

    @NotBlank(message = "节点ID不能为空")
    @Size(max = 64, message = "节点ID长度不能超过64")
    private String id;

    @NotBlank(message = "节点名称不能为空")
    @Size(max = 128, message = "节点名称长度不能超过128")
    private String name;

    @NotBlank(message = "节点分类不能为空")
    @Size(max = 32, message = "节点分类长度不能超过32")
    private String category;

    @NotBlank(message = "难度不能为空")
    @Size(max = 32, message = "难度长度不能超过32")
    private String difficulty;

    @Size(max = 2000, message = "描述长度不能超过2000")
    private String description;

    private List<String> keywords;
}

