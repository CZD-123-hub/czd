package com.coding.assistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("query_log")
public class QueryLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("conversation_id")
    private Long conversationId;

    @TableField("user_message_id")
    private Long userMessageId;

    @TableField("assistant_message_id")
    private Long assistantMessageId;

    @TableField("query_text")
    private String queryText;

    @TableField("query_hash")
    private String queryHash;

    @TableField("expanded_terms")
    private String expandedTerms;

    @TableField("retrieval_status")
    private String retrievalStatus;

    @TableField("source_count")
    private Integer sourceCount;

    @TableField("created_at")
    private LocalDateTime createdAt;
}

