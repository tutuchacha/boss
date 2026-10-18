package com.pinliao.backend.dict.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pinliao.backend.dict.entity.CategoryEntity;
import org.apache.ibatis.annotations.Mapper;

/** categories 表 Mapper（跨租户共享，租户拦截器不注入）。 */
@Mapper
public interface CategoryMapper extends BaseMapper<CategoryEntity> {
}
