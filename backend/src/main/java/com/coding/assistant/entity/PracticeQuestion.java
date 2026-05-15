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
@TableName("practice_question")
public class PracticeQuestion {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("session_id")
    private Long sessionId;

    @TableField("knowledge_id")
    private String knowledgeId;

    @TableField("question_stem")
    private String questionStem;

    @TableField("options_json")
    private String optionsJson;

    @TableField("correct_answer")
    private String correctAnswer;

    @TableField("explanation")
    private String explanation;

    @TableField("user_answer")
    private String userAnswer;

    @TableField("is_correct")
    private Integer isCorrect;

    @TableField("question_order")
    private Integer questionOrder;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
