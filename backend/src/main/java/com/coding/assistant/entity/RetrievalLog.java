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
@TableName("retrieval_log")
public class RetrievalLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("query_log_id")
    private Long queryLogId;

    @TableField("user_id")
    private Long userId;

    @TableField("conversation_id")
    private Long conversationId;

    @TableField("assistant_message_id")
    private Long assistantMessageId;

    @TableField("source_type")
    private String sourceType;

    @TableField("source_id")
    private String sourceId;

    @TableField("source_title")
    private String sourceTitle;

    @TableField("excerpt")
    private String excerpt;

    @TableField("score")
    private Double score;

    @TableField("chunk_index")
    private Integer chunkIndex;

    @TableField("metadata")
    private String metadata;

    @TableField("created_at")
    private LocalDateTime createdAt;
}

