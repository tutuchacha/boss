package com.pinliao.backend.ai.index;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * resume_chunks 读写（Step 4 T-AI1）。
 *
 * <p>仅内部索引使用；对外不暴露任何直接查询，检索侧必须经可见范围过滤。</p>
 */
@Mapper
public interface ResumeChunkMapper extends BaseMapper<ResumeChunkEntity> {
}
