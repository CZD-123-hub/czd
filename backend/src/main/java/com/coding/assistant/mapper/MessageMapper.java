package com.coding.assistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.coding.assistant.entity.Message;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MessageMapper extends BaseMapper<Message> {

    @Select("SELECT * FROM message WHERE conversation_id = #{conversationId} ORDER BY created_at ASC")
    List<Message> selectByConversationIdOrderByCreatedAt(@Param("conversationId") Long conversationId);

    @Delete("DELETE FROM message WHERE conversation_id = #{conversationId}")
    int deleteByConversationId(@Param("conversationId") Long conversationId);

    @Select("""
            SELECT content
            FROM message
            WHERE conversation_id = #{conversationId}
              AND role = 'user'
            ORDER BY created_at DESC
            LIMIT 1
            """)
    String selectLatestUserContentByConversationId(@Param("conversationId") Long conversationId);
}
