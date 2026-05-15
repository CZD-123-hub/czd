package com.coding.assistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.coding.assistant.entity.KnowledgeDocument;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface KnowledgeDocumentMapper extends BaseMapper<KnowledgeDocument> {

    @Select("SELECT id, title, content, category, embedding FROM knowledge_document WHERE embedding IS NOT NULL")
    List<KnowledgeDocument> selectAllWithEmbedding();

    @Select("""
            SELECT id, title, content, category, created_at
            FROM knowledge_document
            WHERE title LIKE CONCAT('%', #{keyword}, '%')
               OR content LIKE CONCAT('%', #{keyword}, '%')
               OR category LIKE CONCAT('%', #{keyword}, '%')
            ORDER BY created_at DESC, id DESC
            LIMIT #{size}
            """)
    List<KnowledgeDocument> searchByKeyword(@Param("keyword") String keyword, @Param("size") int size);

    @Select("""
            SELECT id, title, content, category, created_at
            FROM knowledge_document
            ORDER BY created_at DESC, id DESC
            LIMIT #{size}
            """)
    List<KnowledgeDocument> selectRecent(@Param("size") int size);
}
