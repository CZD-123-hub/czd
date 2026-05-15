package com.coding.assistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.coding.assistant.entity.LearningVideoFavorite;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface LearningVideoFavoriteMapper extends BaseMapper<LearningVideoFavorite> {

    @Select("""
            SELECT *
            FROM learning_video_favorite
            WHERE user_id = #{userId}
              AND video_id = #{videoId}
            LIMIT 1
            """)
    LearningVideoFavorite selectByUserIdAndVideoId(@Param("userId") Long userId, @Param("videoId") Long videoId);

    @Select("""
            SELECT video_id
            FROM learning_video_favorite
            WHERE user_id = #{userId}
            """)
    List<Long> selectVideoIdsByUserId(@Param("userId") Long userId);

    @Delete("""
            DELETE FROM learning_video_favorite
            WHERE user_id = #{userId}
              AND video_id = #{videoId}
            """)
    int deleteByUserIdAndVideoId(@Param("userId") Long userId, @Param("videoId") Long videoId);
}
