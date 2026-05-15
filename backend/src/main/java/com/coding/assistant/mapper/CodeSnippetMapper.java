package com.coding.assistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.coding.assistant.entity.CodeSnippet;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CodeSnippetMapper extends BaseMapper<CodeSnippet> {

    IPage<CodeSnippet> selectByUserIdWithFilter(
            Page<CodeSnippet> page,
            @Param("userId") Long userId,
            @Param("keyword") String keyword,
            @Param("tag") String tag,
            @Param("language") String language
    );

    @Update("""
            UPDATE code_snippet
            SET use_count = COALESCE(use_count, 0) + 1,
                updated_at = NOW()
            WHERE id = #{id}
              AND user_id = #{userId}
            """)
    int incrementUseCount(@Param("id") Long id, @Param("userId") Long userId);
}
