package com.coding.assistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.coding.assistant.entity.LearningRecord;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface LearningRecordMapper extends BaseMapper<LearningRecord> {

    @Select("SELECT COUNT(*) FROM learning_record WHERE user_id = #{userId} AND action_type = #{actionType}")
    int countByUserIdAndActionType(@Param("userId") Long userId, @Param("actionType") String actionType);

    @Select("SELECT COUNT(*) FROM learning_record WHERE user_id = #{userId} AND action_type = #{actionType} AND target_id = #{targetId}")
    int countByUserIdAndActionTypeAndTargetId(
            @Param("userId") Long userId,
            @Param("actionType") String actionType,
            @Param("targetId") String targetId
    );

    @Delete("DELETE FROM learning_record WHERE user_id = #{userId} AND action_type = #{actionType} AND target_id = #{targetId}")
    int deleteByUserIdAndActionTypeAndTargetId(
            @Param("userId") Long userId,
            @Param("actionType") String actionType,
            @Param("targetId") String targetId
    );

    @Select("SELECT * FROM learning_record WHERE user_id = #{userId} AND created_at >= #{startDate} AND created_at < #{endDate} ORDER BY created_at ASC")
    List<LearningRecord> selectByUserIdAndDateRange(
            @Param("userId") Long userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    @Select("SELECT DATE(created_at) as date, COUNT(*) as count FROM learning_record WHERE user_id = #{userId} AND created_at >= #{startDate} AND created_at < #{endDate} GROUP BY DATE(created_at) ORDER BY date ASC")
    List<Map<String, Object>> countByUserIdGroupByDate(
            @Param("userId") Long userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    @Select("SELECT action_type, COUNT(*) as count FROM learning_record WHERE user_id = #{userId} GROUP BY action_type")
    List<Map<String, Object>> countByUserIdGroupByActionType(@Param("userId") Long userId);

    @Select("SELECT COUNT(DISTINCT DATE(created_at)) FROM learning_record WHERE user_id = #{userId}")
    int countDistinctDaysByUserId(@Param("userId") Long userId);

    @Select("SELECT DISTINCT target_id FROM learning_record WHERE user_id = #{userId} AND action_type = #{actionType} AND target_id IS NOT NULL")
    List<String> selectDistinctTargetIdsByUserIdAndActionType(@Param("userId") Long userId, @Param("actionType") String actionType);
}
