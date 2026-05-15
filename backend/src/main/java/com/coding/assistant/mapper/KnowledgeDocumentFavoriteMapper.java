package com.coding.assistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.coding.assistant.entity.KnowledgeDocumentFavorite;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface KnowledgeDocumentFavoriteMapper extends BaseMapper<KnowledgeDocumentFavorite> {

    @Select("""
            SELECT *
            FROM knowledge_document_favorite
            WHERE user_id = #{userId}
              AND document_id = #{documentId}
            LIMIT 1
            """)
    KnowledgeDocumentFavorite selectByUserIdAndDocumentId(
            @Param("userId") Long userId,
            @Param("documentId") Long documentId
    );

    @Select("""
            SELECT document_id
            FROM knowledge_document_favorite
            WHERE user_id = #{userId}
            ORDER BY created_at DESC, id DESC
            """)
    List<Long> selectDocumentIdsByUserId(@Param("userId") Long userId);

    @Delete("""
            DELETE FROM knowledge_document_favorite
            WHERE user_id = #{userId}
              AND document_id = #{documentId}
            """)
    int deleteByUserIdAndDocumentId(@Param("userId") Long userId, @Param("documentId") Long documentId);
}
