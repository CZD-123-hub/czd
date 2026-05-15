package com.coding.assistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.coding.assistant.entity.KnowledgeChunk;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface KnowledgeChunkMapper extends BaseMapper<KnowledgeChunk> {

    @Select("SELECT id, document_id, chunk_index, content, embedding, created_at FROM knowledge_chunk WHERE embedding IS NOT NULL")
    List<KnowledgeChunk> selectAllWithEmbedding();
}
