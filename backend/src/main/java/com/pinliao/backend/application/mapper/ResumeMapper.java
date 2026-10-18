package com.pinliao.backend.application.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pinliao.backend.application.entity.ResumeEntity;
import org.apache.ibatis.annotations.Mapper;

/** resumes 表 Mapper（T4 只读；完整 §11 在 T7）。 */
@Mapper
public interface ResumeMapper extends BaseMapper<ResumeEntity> {
}
