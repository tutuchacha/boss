package com.pinliao.backend.job.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.pinliao.backend.job.entity.JobEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * jobs 表 Mapper（契约 §6.1~§6.4、§6.9 公开浏览查询）。
 *
 * <p><b>为什么显式 {@code @InterceptorIgnore(tenantLine = "true")}</b>：契约把 §6.1~§6.4 / §6.9 定义为
 * 「公开浏览」，无论调用者是否登录（例如 HR 账号也可打开求职者端职位页）都应看到全平台可见职位；
 * 租户注入只作用于 HR 管理端口径（§4.3 / §6.5~§6.8）。</p>
 *
 * <p><b>为什么用普通 SQL（非全文索引）</b>：演示规模（51 条）下 LIKE 足够；§6.2 已预留独立路径
 * {@code /search/jobs}，后续切 ES 时只换实现不换前端。排序固定 {@code published_at DESC, id ASC}，
 * 推荐加权是派生排序，由 service 层做稳定排序（见 JobQueryService），保证同分时行序确定。</p>
 */
@Mapper
public interface JobMapper extends BaseMapper<JobEntity> {

    /** 求职者可见职位（OPEN 且未软删）；筛选参数为 null 即不过滤；kwLike 为已转义的 LIKE 模式。 */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            <script>
            SELECT j.* FROM jobs j
            JOIN companies c ON c.id = j.company_id
            WHERE j.status = 'OPEN' AND j.deleted_at IS NULL
            <if test="city != null"> AND j.city = #{city}</if>
            <if test="cat != null"> AND j.cat = #{cat}</if>
            <if test="sub != null"> AND j.sub = #{sub}</if>
            <if test="exp != null"> AND j.exp = #{exp}</if>
            AND j.salary_min &lt; #{salMax} AND j.salary_max &gt; #{salMin}
            <if test="kwLike != null">
              AND (j.title LIKE #{kwLike} OR c.name LIKE #{kwLike} OR j.cat LIKE #{kwLike}
                   OR j.sub LIKE #{kwLike} OR j.tags LIKE #{kwLike} OR j.area LIKE #{kwLike})
            </if>
            ORDER BY j.published_at DESC, j.id ASC
            </script>
            """)
    @Results(id = "jobResult", value = {
            @Result(column = "id", property = "id", id = true),
            @Result(column = "company_id", property = "companyId"),
            @Result(column = "hr_id", property = "hrId"),
            @Result(column = "hr_name", property = "hrName"),
            @Result(column = "hr_title", property = "hrTitle"),
            @Result(column = "title", property = "title"),
            @Result(column = "salary_min", property = "salaryMin"),
            @Result(column = "salary_max", property = "salaryMax"),
            @Result(column = "city", property = "city"),
            @Result(column = "area", property = "area"),
            @Result(column = "exp", property = "exp"),
            @Result(column = "edu", property = "edu"),
            @Result(column = "cat", property = "cat"),
            @Result(column = "sub", property = "sub"),
            @Result(column = "tags", property = "tags", typeHandler = JacksonTypeHandler.class),
            @Result(column = "responsibilities", property = "responsibilities"),
            @Result(column = "requirements", property = "requirements"),
            @Result(column = "welfare", property = "welfare", typeHandler = JacksonTypeHandler.class),
            @Result(column = "status", property = "status"),
            @Result(column = "published_at", property = "publishedAt"),
            @Result(column = "view_count", property = "viewCount"),
            @Result(column = "apply_count", property = "applyCount"),
            @Result(column = "created_at", property = "createdAt"),
            @Result(column = "updated_at", property = "updatedAt"),
            @Result(column = "deleted_at", property = "deletedAt")
    })
    List<JobEntity> selectVisible(@Param("city") String city,
                                  @Param("cat") String cat,
                                  @Param("sub") String sub,
                                  @Param("exp") String exp,
                                  @Param("salMin") int salMin,
                                  @Param("salMax") int salMax,
                                  @Param("kwLike") String kwLike);

    /** 求职者可见职位详情（不存在 / 已关闭 / 已删除 → null，由 service 抛 40410）。 */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT j.* FROM jobs j
            WHERE j.id = #{jobId} AND j.status = 'OPEN' AND j.deleted_at IS NULL
            """)
    @ResultMap("jobResult")
    JobEntity selectVisibleById(@Param("jobId") String jobId);

    /** 相似职位：同二级岗位或同二级分类，排除自身（对齐 mock getJobDetail 的过滤规则）。 */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT j.* FROM jobs j
            WHERE j.id != #{jobId} AND j.status = 'OPEN' AND j.deleted_at IS NULL
              AND (j.sub = #{sub} OR j.cat = #{cat})
            ORDER BY j.published_at DESC, j.id ASC
            LIMIT #{limit}
            """)
    @ResultMap("jobResult")
    List<JobEntity> selectSimilar(@Param("jobId") String jobId,
                                  @Param("sub") String sub,
                                  @Param("cat") String cat,
                                  @Param("limit") int limit);

    /** §6.9 浏览数 +1（防重复窗口在 service 层用 Redis 控制）。 */
    @InterceptorIgnore(tenantLine = "true")
    @Update("UPDATE jobs SET view_count = view_count + 1 WHERE id = #{jobId} AND deleted_at IS NULL")
    int incrementViewCount(@Param("jobId") String jobId);

    /** §6.5 发布配额：tenant_configs.max_jobs（无配置行返回 null → service 用缺省 50）。 */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT max_jobs FROM tenant_configs WHERE company_id = #{companyId}")
    Integer selectTenantMaxJobs(@Param("companyId") String companyId);

    /**
     * 新建职位 id 序号：{@code job-new-{seq}} 中现有最大序号 + 1（递增派生，对齐 mock；不用时间戳/随机）。
     *
     * <p>软删只置 deleted_at、行保留，因此序号不会复用；显式忽略租户注入——id 是全局主键，
     * 必须跨公司取最大值，不能按当前公司过滤。</p>
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT COALESCE(MAX(CAST(SUBSTRING(id, 9) AS UNSIGNED)), 0) + 1
            FROM jobs WHERE id LIKE 'job-new-%'
            """)
    long nextNewJobSeq();
}
