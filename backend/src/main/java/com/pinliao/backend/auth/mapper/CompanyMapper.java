package com.pinliao.backend.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pinliao.backend.auth.entity.CompanyEntity;
import org.apache.ibatis.annotations.Mapper;

/** companies 表 Mapper。 */
@Mapper
public interface CompanyMapper extends BaseMapper<CompanyEntity> {
}
