package com.coding.assistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.coding.assistant.entity.CodeSnippet;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CodeSnippetMapper extends BaseMapper<CodeSnippet> {

    IPage<CodeSnippet> selectByUserIdWithFilter(
            Page<CodeSnippet> page,
            @Param("userId") Long userId,
            @Param("keyword") String keyword,
            @Param("tag") String tag
    );
}