package com.pinliao.backend.application.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pinliao.backend.application.entity.MessageEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/** messages 表 Mapper（T4 仅写入面试邀请消息；完整 §8 在 T6）。 */
@Mapper
public interface MessageMapper extends BaseMapper<MessageEntity> {

    /** 新建消息序号：{@code msg-new-{seq}}（全局，忽略租户注入）。 */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT COALESCE(MAX(CAST(SUBSTRING(id, 9) AS UNSIGNED)), 0) + 1
            FROM messages WHERE id LIKE 'msg-new-%'
            """)
    long nextNewSeq();
}
