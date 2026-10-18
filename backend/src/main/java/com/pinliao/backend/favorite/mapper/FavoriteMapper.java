package com.pinliao.backend.favorite.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pinliao.backend.favorite.entity.FavoriteEntity;
import org.apache.ibatis.annotations.Mapper;

/** favorites 表 Mapper（T7）。 */
@Mapper
public interface FavoriteMapper extends BaseMapper<FavoriteEntity> {
}
