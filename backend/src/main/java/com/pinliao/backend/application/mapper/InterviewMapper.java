package com.pinliao.backend.application.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pinliao.backend.application.entity.InterviewEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** interviews 表 Mapper（§10）。 */
@Mapper
public interface InterviewMapper extends BaseMapper<InterviewEntity> {

    /**
     * 求职者视角：面试 → 投递 → user_id 归属过滤（跨租户，不依赖 company 注入）。
     *
     * <p>status 为 null 时不过滤；排序与 service 层游标语义一致（created_at DESC, id ASC）。</p>
     */
    @Select("""
            <script>
            SELECT i.* FROM interviews i
            JOIN applications a ON a.id = i.application_id
            WHERE a.user_id = #{userId} AND a.deleted_at IS NULL
            <if test="status != null"> AND i.status = #{status}</if>
            ORDER BY i.created_at DESC, i.id ASC
            </script>
            """)
    List<InterviewEntity> selectByUser(@Param("userId") String userId,
                                       @Param("status") String status);

    /** 新建面试序号：{@code int-new-{seq}}（全局，忽略租户注入）。 */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT COALESCE(MAX(CAST(SUBSTRING(id, 9) AS UNSIGNED)), 0) + 1
            FROM interviews WHERE id LIKE 'int-new-%'
            """)
    long nextNewSeq();
}
