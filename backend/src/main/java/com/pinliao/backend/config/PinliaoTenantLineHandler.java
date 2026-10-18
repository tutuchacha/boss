package com.pinliao.backend.config;

import com.pinliao.backend.auth.context.UserContext;
import com.pinliao.backend.common.api.ApiCodes;
import com.pinliao.backend.common.exception.BusinessException;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;

import java.util.Locale;
import java.util.Set;

/**
 * 多租户（companyId）注入策略：MyBatis-Plus 租户拦截器据此为 SQL 追加/填充 {@code company_id}。
 *
 * <p><b>注入规则</b>（与 step3-plan §3「多租户 companyId」一致）：</p>
 * <ul>
 *   <li>仅当「当前登录者携带 companyId（HR / COMPANY_ADMIN）」且「表属于租户私有集合」时注入；</li>
 *   <li>未登录（鉴权白名单内的接口）与求职者（跨租户视角，如职位浏览 / 我的投递 / 我的会话）
 *       一律不注入——求职者侧查询必须在 service 层显式过滤 {@code user_id} / 可见职位；</li>
 *   <li>全局表（users / companies / resumes / favorites / categories 等）永不注入。</li>
 * </ul>
 *
 * <p>求职者跨租户是产品语义（同一份简历可投多家），故不能用「一刀切按表注入」；这是与
 * 常见 SaaS 租户中间件最大的差异，改动本策略前先读 docs/step3-plan.md §3。</p>
 */
public class PinliaoTenantLineHandler implements TenantLineHandler {

    /** 租户私有表（带 company_id）：仅这些表可能被注入，其余表直接忽略。 */
    private static final Set<String> TENANT_TABLES = Set.of(
            "jobs",
            "conversations",
            "messages",
            "applications",
            "interviews",
            "resume_view_logs",
            "tenant_configs",
            "usage_logs"
    );

    @Override
    public Expression getTenantId() {
        UserContext.CurrentUser user = UserContext.get();
        if (user == null || user.companyId() == null || user.companyId().isBlank()) {
            // ignoreTable 已拦住该场景；此处兜底 fail-fast，避免越权查询被静默放行
            throw new BusinessException(ApiCodes.FORBIDDEN, "缺少企业上下文，无法访问租户数据");
        }
        return new StringValue(user.companyId());
    }

    @Override
    public String getTenantIdColumn() {
        return "company_id";
    }

    @Override
    public boolean ignoreTable(String tableName) {
        UserContext.CurrentUser user = UserContext.get();
        if (user == null || user.companyId() == null || user.companyId().isBlank()) {
            // 未登录 / 求职者：不注入（service 层显式过滤，保证跨租户浏览与个人数据隔离）
            return true;
        }
        return !TENANT_TABLES.contains(tableName.toLowerCase(Locale.ROOT));
    }
}
