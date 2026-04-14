package com.coding.assistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("learning_node")
public class LearningNode {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("path_id")
    private Long pathId;

    @TableField("knowledge_id")
    private String knowledgeId;

    @TableField("node_order")
    private Integer nodeOrder;

    @TableField("status")
    private String status;

    @TableField("resource_urls")
    private String resourceUrls;
    
    @TableField("custom_name")
    private String customName;
}