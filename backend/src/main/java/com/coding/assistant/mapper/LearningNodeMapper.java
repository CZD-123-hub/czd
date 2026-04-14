package com.coding.assistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.coding.assistant.entity.LearningNode;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface LearningNodeMapper extends BaseMapper<LearningNode> {
    
    List<LearningNode> selectByPathId(@Param("pathId") Long pathId);
    
    // 添加这个方法
    void deleteByPathId(@Param("pathId") Long pathId);
}