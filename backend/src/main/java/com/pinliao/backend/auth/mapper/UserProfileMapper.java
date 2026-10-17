package com.pinliao.backend.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pinliao.backend.auth.entity.UserProfileEntity;
import org.apache.ibatis.annotations.Mapper;

/** user_profiles 表 Mapper。 */
@Mapper
public interface UserProfileMapper extends BaseMapper<UserProfileEntity> {
}
