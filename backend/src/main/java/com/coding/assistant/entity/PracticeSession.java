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
@TableName("practice_session")
public class PracticeSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("path_id")
    private Long pathId;

    @TableField("mode")
    private String mode;

    @TableField("status")
    private String status;

    @TableField("total_questions")
    private Integer totalQuestions;

    @TableField("answered_count")
    private Integer answeredCount;

    @TableField("correct_count")
    private Integer correctCount;

    @TableField("duration_minutes")
    private Integer durationMinutes;

    @TableField("score")
    private Double score;

    @TableField("grade")
    private String grade;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField("submitted_at")
    private LocalDateTime submittedAt;
}
