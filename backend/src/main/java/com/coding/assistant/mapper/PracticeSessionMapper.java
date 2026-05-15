package com.coding.assistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.coding.assistant.entity.PracticeSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PracticeSessionMapper extends BaseMapper<PracticeSession> {

    @Select("""
            SELECT *
            FROM practice_session
            WHERE user_id = #{userId}
              AND mode = #{mode}
            ORDER BY id DESC
            LIMIT 1
            """)
    PracticeSession selectLatestByUserIdAndMode(@Param("userId") Long userId, @Param("mode") String mode);
}
