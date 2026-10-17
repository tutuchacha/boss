package com.pinliao.backend.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pinliao.backend.auth.entity.CompanyMemberEntity;
import org.apache.ibatis.annotations.Mapper;

/** company_members 表 Mapper。 */
@Mapper
public interface CompanyMemberMapper extends BaseMapper<CompanyMemberEntity> {
}
