package com.coding.assistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.coding.assistant.entity.LearningVideoHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface LearningVideoHistoryMapper extends BaseMapper<LearningVideoHistory> {

    @Select("""
            SELECT *
            FROM learning_video_history
            WHERE user_id = #{userId}
              AND video_id = #{videoId}
            LIMIT 1
            """)
    LearningVideoHistory selectByUserIdAndVideoId(@Param("userId") Long userId, @Param("videoId") Long videoId);

    @Select({
            "<script>",
            "SELECT *",
            "FROM learning_video_history",
            "WHERE user_id = #{userId}",
            "<if test='videoIds != null and videoIds.size() > 0'>",
            "AND video_id IN",
            "<foreach collection='videoIds' item='id' open='(' separator=',' close=')'>",
            "#{id}",
            "</foreach>",
            "</if>",
            "</script>"
    })
    List<LearningVideoHistory> selectByUserIdAndVideoIds(
            @Param("userId") Long userId,
            @Param("videoIds") List<Long> videoIds
    );

    @Select("""
            SELECT *
            FROM learning_video_history
            WHERE user_id = #{userId}
            ORDER BY last_watched_at DESC
            LIMIT #{size}
            """)
    List<LearningVideoHistory> selectRecentByUserId(@Param("userId") Long userId, @Param("size") int size);
}
