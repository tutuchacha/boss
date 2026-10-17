package com.pinliao.backend.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pinliao.backend.auth.entity.UserEntity;
import org.apache.ibatis.annotations.Mapper;

/** users 表 Mapper。 */
@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {
}
