package com.coding.assistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.coding.assistant.entity.PracticeQuestion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PracticeQuestionMapper extends BaseMapper<PracticeQuestion> {

    @Select("""
            SELECT *
            FROM practice_question
            WHERE session_id = #{sessionId}
            ORDER BY question_order ASC, id ASC
            """)
    List<PracticeQuestion> selectBySessionId(@Param("sessionId") Long sessionId);

    @Select("""
            SELECT *
            FROM practice_question
            WHERE id = #{questionId}
              AND session_id = #{sessionId}
            LIMIT 1
            """)
    PracticeQuestion selectByIdAndSessionId(@Param("questionId") Long questionId, @Param("sessionId") Long sessionId);
}
