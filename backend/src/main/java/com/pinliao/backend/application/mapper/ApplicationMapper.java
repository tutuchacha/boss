package com.pinliao.backend.application.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pinliao.backend.application.entity.ApplicationEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/** applications 表 Mapper（§9）。 */
@Mapper
public interface ApplicationMapper extends BaseMapper<ApplicationEntity> {

    /**
     * 新建投递序号：{@code app-new-{seq}} 中现有最大序号 + 1。
     *
     * <p>显式忽略租户注入：id 是全局主键，序号必须跨公司取最大值；
     * 冒烟产物统一 {@code app-new-%} 前缀，便于种子巡检排除。</p>
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT COALESCE(MAX(CAST(SUBSTRING(id, 9) AS UNSIGNED)), 0) + 1
            FROM applications WHERE id LIKE 'app-new-%'
            """)
    long nextNewSeq();
}
