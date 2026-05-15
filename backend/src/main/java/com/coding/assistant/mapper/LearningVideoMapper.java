package com.coding.assistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.coding.assistant.entity.LearningVideo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface LearningVideoMapper extends BaseMapper<LearningVideo> {

    @Select("""
            SELECT *
            FROM learning_video
            WHERE url = #{url}
            LIMIT 1
            """)
    LearningVideo selectByUrl(@Param("url") String url);

    @Select("""
            SELECT *
            FROM learning_video
            WHERE (#{keyword} IS NULL OR #{keyword} = ''
              OR title LIKE CONCAT('%', #{keyword}, '%')
              OR description LIKE CONCAT('%', #{keyword}, '%')
              OR tags LIKE CONCAT('%', #{keyword}, '%')
              OR knowledge_id LIKE CONCAT('%', #{keyword}, '%'))
            ORDER BY updated_at DESC, id DESC
            LIMIT #{size} OFFSET #{offset}
            """)
    List<LearningVideo> searchByKeyword(
            @Param("keyword") String keyword,
            @Param("size") int size,
            @Param("offset") int offset
    );

    @Select("""
            SELECT *
            FROM learning_video
            WHERE url LIKE '/uploads/videos/%'
              AND (#{keyword} IS NULL OR #{keyword} = ''
              OR title LIKE CONCAT('%', #{keyword}, '%')
              OR description LIKE CONCAT('%', #{keyword}, '%')
              OR tags LIKE CONCAT('%', #{keyword}, '%')
              OR knowledge_id LIKE CONCAT('%', #{keyword}, '%'))
            ORDER BY updated_at DESC, id DESC
            LIMIT #{size} OFFSET #{offset}
            """)
    List<LearningVideo> searchLocalFileByKeyword(
            @Param("keyword") String keyword,
            @Param("size") int size,
            @Param("offset") int offset
    );

    @Select("""
            SELECT COUNT(*)
            FROM learning_video
            WHERE (#{keyword} IS NULL OR #{keyword} = ''
              OR title LIKE CONCAT('%', #{keyword}, '%')
              OR description LIKE CONCAT('%', #{keyword}, '%')
              OR tags LIKE CONCAT('%', #{keyword}, '%')
              OR knowledge_id LIKE CONCAT('%', #{keyword}, '%'))
            """)
    long countByKeyword(@Param("keyword") String keyword);

    @Select("""
            SELECT COUNT(*)
            FROM learning_video
            WHERE url LIKE '/uploads/videos/%'
              AND (#{keyword} IS NULL OR #{keyword} = ''
              OR title LIKE CONCAT('%', #{keyword}, '%')
              OR description LIKE CONCAT('%', #{keyword}, '%')
              OR tags LIKE CONCAT('%', #{keyword}, '%')
              OR knowledge_id LIKE CONCAT('%', #{keyword}, '%'))
            """)
    long countLocalFileByKeyword(@Param("keyword") String keyword);

    @Select("""
            SELECT *
            FROM learning_video
            WHERE title LIKE CONCAT('%', #{keyword}, '%')
               OR description LIKE CONCAT('%', #{keyword}, '%')
               OR tags LIKE CONCAT('%', #{keyword}, '%')
               OR knowledge_id LIKE CONCAT('%', #{keyword}, '%')
            ORDER BY updated_at DESC, id DESC
            LIMIT #{size}
            """)
    List<LearningVideo> recommendByKeyword(@Param("keyword") String keyword, @Param("size") int size);

    @Select("""
            SELECT *
            FROM learning_video
            ORDER BY updated_at DESC, id DESC
            LIMIT #{size}
            """)
    List<LearningVideo> selectRecent(@Param("size") int size);

    @Select("""
            SELECT v.*
            FROM learning_video v
            INNER JOIN learning_video_favorite f ON f.video_id = v.id
            WHERE f.user_id = #{userId}
            ORDER BY f.created_at DESC
            LIMIT #{size}
            """)
    List<LearningVideo> selectFavoriteVideosByUserId(@Param("userId") Long userId, @Param("size") int size);
}
