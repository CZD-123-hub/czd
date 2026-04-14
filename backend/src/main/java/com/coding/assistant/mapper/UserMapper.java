package com.coding.assistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.coding.assistant.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}