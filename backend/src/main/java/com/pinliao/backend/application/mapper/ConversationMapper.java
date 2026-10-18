package com.pinliao.backend.application.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pinliao.backend.application.entity.ConversationEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/** conversations 表 Mapper（T4 仅占位创建 + 约面试更新；完整 §8 在 T6）。 */
@Mapper
public interface ConversationMapper extends BaseMapper<ConversationEntity> {

    /** 新建会话序号：{@code conv-new-{seq}}（全局，忽略租户注入）。 */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT COALESCE(MAX(CAST(SUBSTRING(id, 10) AS UNSIGNED)), 0) + 1
            FROM conversations WHERE id LIKE 'conv-new-%'
            """)
    long nextNewSeq();
}
