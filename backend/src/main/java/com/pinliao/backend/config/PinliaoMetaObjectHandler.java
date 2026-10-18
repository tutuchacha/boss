package com.pinliao.backend.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * created_at / updated_at 自动填充（实体字段需标注 {@code @TableField(fill = ...)} 才生效）。
 *
 * <p>时间统一取 UTC，与连接层 {@code SET time_zone='+00:00'}、Jackson UTC 序列化保持一致。</p>
 */
public class PinliaoMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now(ZoneOffset.UTC));
    }
}
