package com.coding.assistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.coding.assistant.entity.KnowledgeDocument;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface KnowledgeDocumentMapper extends BaseMapper<KnowledgeDocument> {

    /** 只查询有 embedding 的文档（用于向量检索） */
    @Select("SELECT id, title, content, category, embedding FROM knowledge_document WHERE embedding IS NOT NULL")
    List<KnowledgeDocument> selectAllWithEmbedding();
}
