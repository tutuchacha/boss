package com.pinliao.backend.job.service;

import com.pinliao.backend.auth.context.Roles;
import com.pinliao.backend.auth.context.UserContext;
import com.pinliao.backend.auth.entity.CompanyEntity;
import com.pinliao.backend.auth.mapper.CompanyMapper;
import com.pinliao.backend.common.api.ApiCodes;
import com.pinliao.backend.common.exception.BusinessException;
import com.pinliao.backend.job.JobCodes;
import com.pinliao.backend.job.entity.JobEntity;
import com.pinliao.backend.job.mapper.JobMapper;
import com.pinliao.backend.job.view.JobViews;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * §4.3 招聘者职位查询（HR 管理视角）。
 *
 * <p><b>与公开浏览的区别</b>：§6.1~§6.4 只认 {@code status='OPEN'}，HR 管理端必须看到
 * {@code CLOSED}（以及预留的 {@code DRAFT}），因此这里走 {@code selectByCompanyAnyStatus} /
 * {@code selectForCompany}，仅排除软删行。归属公司一律取自登录上下文（不信任请求参数）。</p>
 *
 * <p><b>出参形状</b>：契约 §4.3 写「分页 Job[]」，但前端 service（{@code src/api/hrJobs.ts}）按
 * {@code HrJobRecord[]} 数组解包。演示规模（每公司 ≤ 数十条）下返回全量数组（与 mock 一致），
 * {@code limit} 作为上限保护；{@code cursor} 参数保留但未启用游标分页（T9 前端也不传）。
 * 出参字段为 {@link JobViews.HrRecord}（JobSummary + 状态 + 详情字段），是契约形状的超集。</p>
 */
@Service
public class HrJobQueryService {

    /** §4.3 status 允许值（ALL / 缺省为全部） */
    static final Set<String> HR_JOB_STATUSES = Set.of("OPEN", "CLOSED", "DRAFT");

    private static final int MAX_LIMIT = 100;

    private final JobMapper jobMapper;
    private final CompanyMapper companyMapper;

    public HrJobQueryService(JobMapper jobMapper, CompanyMapper companyMapper) {
        this.jobMapper = jobMapper;
        this.companyMapper = companyMapper;
    }

    /**
     * §4.3 我（所在公司）发布的职位。
     *
     * @param status 可选：OPEN / CLOSED / DRAFT，缺省或 ALL 为全部（非法值 42201）
     * @param q      可选扩展参数：按职位名称模糊匹配（大小写不敏感，对齐前端 mock 的标题过滤）
     * @param hrId   可选扩展参数：仅看该 HR 发布的职位（对齐前端「仅看我发布的」筛选）
     * @param limit  可选上限保护（1~100，缺省不限）
     */
    public List<JobViews.HrRecord> list(String status, String q, String hrId, Integer limit) {
        UserContext.CurrentUser user = Roles.requireManager();
        String normalizedStatus = normalizeStatus(status);
        String keyword = lowerOrNull(q);
        String ownerHrId = trimToNull(hrId);
        Integer cap = limit == null ? null : Math.max(1, Math.min(limit, MAX_LIMIT));

        List<JobViews.HrRecord> views = new ArrayList<>();
        CompanyEntity company = companyMapper.selectById(user.companyId());
        for (JobEntity job : jobMapper.selectByCompanyAnyStatus(user.companyId())) {
            if (normalizedStatus != null && !normalizedStatus.equals(job.getStatus())) {
                continue;
            }
            if (ownerHrId != null && !ownerHrId.equals(job.getHrId())) {
                continue;
            }
            if (keyword != null && (job.getTitle() == null
                    || !job.getTitle().toLowerCase(Locale.ROOT).contains(keyword))) {
                continue;
            }
            views.add(JobViews.HrRecord.of(job, company));
            if (cap != null && views.size() >= cap) {
                break;
            }
        }
        return views;
    }

    /**
     * §4.3+ 单个职位（HR 视角，含 CLOSED / DRAFT）。
     *
     * <p>契约未列该读取，但前端职位编辑页需要「已关闭职位」的内容（公开详情会 40410）；
     * 参照 T6 {@code GET /conversations/{id}} 的先例作为契约外扩展。不存在 / 非本公司 / 已软删
     * 统一 40410，不泄露职位是否存在。</p>
     */
    public JobViews.HrRecord detail(String jobId) {
        UserContext.CurrentUser user = Roles.requireManager();
        JobEntity job = jobMapper.selectForCompany(jobId, user.companyId());
        if (job == null) {
            throw new BusinessException(JobCodes.JOB_NOT_FOUND, "职位不存在");
        }
        return JobViews.HrRecord.of(job, companyMapper.selectById(job.getCompanyId()));
    }

    private static String normalizeStatus(String status) {
        String normalized = trimToNull(status);
        if (normalized == null || "ALL".equalsIgnoreCase(normalized)) {
            return null;
        }
        String upper = normalized.toUpperCase(Locale.ROOT);
        if (!HR_JOB_STATUSES.contains(upper)) {
            throw new BusinessException(ApiCodes.FIELD_VALIDATION, "状态筛选值无效");
        }
        return upper;
    }

    private static String lowerOrNull(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
