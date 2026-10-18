package com.pinliao.backend.job.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pinliao.backend.auth.context.UserContext;
import com.pinliao.backend.auth.entity.CompanyEntity;
import com.pinliao.backend.auth.entity.HrProfileEntity;
import com.pinliao.backend.auth.mapper.CompanyMapper;
import com.pinliao.backend.auth.mapper.HrProfileMapper;
import com.pinliao.backend.common.api.ApiCodes;
import com.pinliao.backend.common.exception.BusinessException;
import com.pinliao.backend.job.JobCodes;
import com.pinliao.backend.job.dto.JobRequests;
import com.pinliao.backend.job.entity.JobEntity;
import com.pinliao.backend.job.mapper.JobMapper;
import com.pinliao.backend.job.view.JobViews;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 职位写接口（契约 §6.5~§6.8）：发布 / 更新 / 关闭·重开 / 软删。
 *
 * <p><b>鉴权与租户</b>：仅 {@code HR} / {@code COMPANY_ADMIN} 且 token 带 companyId 的身份可调用；
 * 归属公司一律取自登录上下文（不信任请求体）。写操作先 {@code selectById}（租户拦截器自动追加
 * {@code company_id} 条件）再显式比对 companyId，缺失 / 非本公司 / 已软删统一 40310
 * 「无权操作该职位」（对齐 mock {@code src/api/mock/hrJobs.ts}，不向外部泄露职位是否存在）。</p>
 *
 * <p><b>发布语义</b>（对齐 mock {@code createHrJob}）：状态 OPEN、发布时间 = 当前时间、统计清零；
 * 新 id 为 {@code job-new-{seq}}，序号取库内现有 {@code job-new-%} 最大序号 + 1（递增派生，
 * 不用时间戳 / 随机；软删保留行，序号不复用）。</p>
 *
 * <p><b>配额与限流</b>：§6.5 要求「10 次/分钟（按 companyId）」与 42211 企业配额已满；
 * 配额取 {@code tenant_configs.max_jobs}（无配置行时默认 50，仅统计未软删职位）。</p>
 */
@Service
public class JobCommandService {

    /** 企业配额缺省值（tenant_configs 无配置行时） */
    static final int DEFAULT_MAX_JOBS = 50;
    /** §6.5 发布限流：10 次/分钟（按 companyId） */
    static final int CREATE_LIMIT_PER_MINUTE = 10;
    /** 标签上限（对齐 mock validateJobForm） */
    static final int MAX_TAGS = 8;

    static final String STATUS_OPEN = "OPEN";
    static final String STATUS_CLOSED = "CLOSED";

    private static final String ROLE_HR = "HR";
    private static final String ROLE_COMPANY_ADMIN = "COMPANY_ADMIN";
    private static final String CREATE_RATE_KEY = "pinliao:job:create:%s:%d";

    private final JobMapper jobMapper;
    private final CompanyMapper companyMapper;
    private final HrProfileMapper hrProfileMapper;
    private final StringRedisTemplate redis;

    public JobCommandService(JobMapper jobMapper, CompanyMapper companyMapper,
                             HrProfileMapper hrProfileMapper, StringRedisTemplate redis) {
        this.jobMapper = jobMapper;
        this.companyMapper = companyMapper;
        this.hrProfileMapper = hrProfileMapper;
        this.redis = redis;
    }

    /** 合并后的可写字段（PATCH 时 null 表示保留原值，合并结果必填项非空）。 */
    private record Fields(String title, int salaryMin, int salaryMax, String city, String area,
                          String exp, String edu, String cat, String sub, List<String> tags,
                          String responsibilities, String requirements, List<String> welfare) {
    }

    /** §6.5 发布职位（HR / COMPANY_ADMIN；42211 配额、42901 限流、42201 字段校验）。 */
    @Transactional
    public JobViews.HrRecord create(JobRequests.Upsert input) {
        UserContext.CurrentUser user = requireManager();
        Fields fields = validate(input, null);
        limitCreateRate(user.companyId());
        ensureQuota(user.companyId());

        HrProfileEntity profile = hrProfileOf(user.userId());
        // 截到毫秒：MySQL DATETIME(3) 只存毫秒，保证创建响应与后续读库的 publishedAt 完全一致
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS);

        JobEntity job = new JobEntity();
        job.setId("job-new-" + jobMapper.nextNewJobSeq());
        job.setCompanyId(user.companyId());
        // HR 展示信息取本账号档案；极端情况下无档案时退回 userId，保证 hr.name 字段不为空
        job.setHrId(profile == null ? user.userId() : profile.getId());
        job.setHrName(profile == null ? "招聘者" : profile.getName());
        job.setHrTitle(profile == null ? null : profile.getTitle());
        apply(job, fields);
        job.setStatus(STATUS_OPEN);
        job.setPublishedAt(now);
        job.setViewCount(0);
        job.setApplyCount(0);
        jobMapper.insert(job);
        return toRecord(job);
    }

    /** §6.6 更新职位（本公司；部分字段覆盖，保留发布者 / 发布时间 / 统计）。 */
    @Transactional
    public JobViews.HrRecord update(String jobId, JobRequests.Upsert input) {
        UserContext.CurrentUser user = requireManager();
        JobEntity job = requireOwnEditable(jobId, user.companyId());
        apply(job, validate(input, job));
        jobMapper.updateById(job);
        return toRecord(job);
    }

    /** §6.7 关闭职位。 */
    @Transactional
    public JobViews.HrRecord close(String jobId) {
        return changeStatus(jobId, STATUS_CLOSED);
    }

    /** §6.7 重新开放职位。 */
    @Transactional
    public JobViews.HrRecord reopen(String jobId) {
        return changeStatus(jobId, STATUS_OPEN);
    }

    /** §6.8 删除职位（软删；重复删除幂等成功，对齐 mock softDeleteHrJob）。 */
    @Transactional
    public void delete(String jobId) {
        UserContext.CurrentUser user = requireManager();
        JobEntity job = jobMapper.selectById(jobId);
        if (job == null || !user.companyId().equals(job.getCompanyId())) {
            throw forbidden();
        }
        if (job.getDeletedAt() == null) {
            job.setDeletedAt(LocalDateTime.now(ZoneOffset.UTC));
            jobMapper.updateById(job);
        }
    }

    // ------------------------------------------------------------------
    // 内部：鉴权 / 校验 / 配额 / 映射
    // ------------------------------------------------------------------

    private JobViews.HrRecord changeStatus(String jobId, String status) {
        UserContext.CurrentUser user = requireManager();
        JobEntity job = requireOwnEditable(jobId, user.companyId());
        job.setStatus(status);
        jobMapper.updateById(job);
        return toRecord(job);
    }

    /** 仅 HR / COMPANY_ADMIN 且带企业上下文可管理职位，否则 40301。 */
    private static UserContext.CurrentUser requireManager() {
        UserContext.CurrentUser user = UserContext.require();
        boolean manager = ROLE_HR.equals(user.role()) || ROLE_COMPANY_ADMIN.equals(user.role());
        if (!manager || user.companyId() == null || user.companyId().isBlank()) {
            throw new BusinessException(ApiCodes.FORBIDDEN, "仅招聘者（HR / 企业管理员）可管理职位");
        }
        return user;
    }

    private JobEntity requireOwnEditable(String jobId, String companyId) {
        JobEntity job = jobMapper.selectById(jobId);
        if (job == null || !companyId.equals(job.getCompanyId()) || job.getDeletedAt() != null) {
            throw forbidden();
        }
        return job;
    }

    private static BusinessException forbidden() {
        return new BusinessException(JobCodes.JOB_FORBIDDEN, "无权操作该职位");
    }

    private HrProfileEntity hrProfileOf(String userId) {
        return hrProfileMapper.selectOne(Wrappers.<HrProfileEntity>lambdaQuery()
                .eq(HrProfileEntity::getUserId, userId)
                .last("LIMIT 1"));
    }

    /** 发布配额：tenant_configs.max_jobs（缺省 50），只统计未软删职位。 */
    private void ensureQuota(String companyId) {
        Integer configured = jobMapper.selectTenantMaxJobs(companyId);
        int max = configured == null || configured <= 0 ? DEFAULT_MAX_JOBS : configured;
        Long used = jobMapper.selectCount(Wrappers.<JobEntity>lambdaQuery()
                .eq(JobEntity::getCompanyId, companyId)
                .isNull(JobEntity::getDeletedAt));
        if (used != null && used >= max) {
            throw new BusinessException(JobCodes.COMPANY_QUOTA_EXCEEDED, "企业配额已满，无法发布职位");
        }
    }

    /** 发布限流：同一公司每分钟 10 次（Redis 计数，窗口固定为自然分钟）。 */
    private void limitCreateRate(String companyId) {
        long minute = Instant.now().getEpochSecond() / 60;
        String key = CREATE_RATE_KEY.formatted(companyId, minute);
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, Duration.ofSeconds(60));
        }
        if (count != null && count > CREATE_LIMIT_PER_MINUTE) {
            throw new BusinessException(ApiCodes.RATE_LIMITED, "发布过于频繁，请稍后再试");
        }
    }

    /** 合并 + 校验（create 时 existing 为 null；PATCH 时 null 字段回退到 existing）。 */
    private static Fields validate(JobRequests.Upsert input, JobEntity existing) {
        String title = trimmed(pick(input.title(), existing == null ? null : existing.getTitle()));
        if (title == null) {
            throw invalid("请填写职位名称");
        }
        if (title.length() < 2) {
            throw invalid("职位名称至少 2 个字");
        }
        if (title.length() > 30) {
            throw invalid("职位名称不超过 30 个字");
        }

        Integer salaryMin = pick(input.salaryMin(), existing == null ? null : existing.getSalaryMin());
        Integer salaryMax = pick(input.salaryMax(), existing == null ? null : existing.getSalaryMax());
        if (salaryMin == null) {
            throw invalid("请填写最低薪资");
        }
        if (salaryMax == null) {
            throw invalid("请填写最高薪资");
        }
        if (salaryMin <= 0 || salaryMax <= 0) {
            throw invalid("薪资需为正数");
        }
        if (salaryMin > 999 || salaryMax > 999) {
            throw invalid("薪资数值过大");
        }
        if (salaryMin >= salaryMax) {
            throw invalid("最高薪资需大于最低薪资");
        }

        String city = trimmed(pick(input.city(), existing == null ? null : existing.getCity()));
        if (city == null) {
            throw invalid("请选择城市");
        }
        String exp = trimmed(pick(input.exp(), existing == null ? null : existing.getExp()));
        if (exp == null) {
            throw invalid("请选择经验要求");
        }
        String edu = trimmed(pick(input.edu(), existing == null ? null : existing.getEdu()));
        if (edu == null) {
            throw invalid("请选择学历要求");
        }
        String cat = trimmed(pick(input.cat(), existing == null ? null : existing.getCat()));
        if (cat == null) {
            throw invalid("请选择职位大类");
        }
        String sub = trimmed(pick(input.sub(), existing == null ? null : existing.getSub()));
        if (sub == null) {
            throw invalid("请选择职位子类");
        }

        List<String> tags = normalize(pick(input.tags(), existing == null ? null : existing.getTags()));
        if (tags.size() > MAX_TAGS) {
            throw invalid("标签最多 8 个");
        }

        String area = input.area() != null
                ? trimmed(input.area())
                : (existing == null ? null : existing.getArea());
        String responsibilities = input.responsibilities() != null
                ? input.responsibilities().trim()
                : (existing == null ? null : existing.getResponsibilities());
        String requirements = input.requirements() != null
                ? input.requirements().trim()
                : (existing == null ? null : existing.getRequirements());
        List<String> welfare = normalize(input.welfare() != null
                ? input.welfare()
                : (existing == null ? null : existing.getWelfare()));

        return new Fields(title, salaryMin, salaryMax, city, area, exp, edu, cat, sub, tags,
                responsibilities, requirements, welfare);
    }

    private static void apply(JobEntity job, Fields fields) {
        job.setTitle(fields.title());
        job.setSalaryMin(fields.salaryMin());
        job.setSalaryMax(fields.salaryMax());
        job.setCity(fields.city());
        job.setArea(fields.area());
        job.setExp(fields.exp());
        job.setEdu(fields.edu());
        job.setCat(fields.cat());
        job.setSub(fields.sub());
        job.setTags(fields.tags());
        job.setResponsibilities(fields.responsibilities());
        job.setRequirements(fields.requirements());
        job.setWelfare(fields.welfare());
    }

    private JobViews.HrRecord toRecord(JobEntity job) {
        CompanyEntity company = job.getCompanyId() == null ? null : companyMapper.selectById(job.getCompanyId());
        JobViews.CompanyBrief companyBrief = company == null
                ? new JobViews.CompanyBrief(job.getCompanyId(), null, null, null)
                : new JobViews.CompanyBrief(company.getId(), company.getName(), company.getStage(), company.getSize());
        return new JobViews.HrRecord(
                job.getId(), job.getTitle(), intOf(job.getSalaryMin()), intOf(job.getSalaryMax()), salaryText(job),
                job.getCity(), job.getArea(), job.getExp(), job.getEdu(), job.getCat(), job.getSub(),
                listOf(job.getTags()), companyBrief, new JobViews.HrBrief(job.getHrId(), job.getHrName(), job.getHrTitle()),
                days(job.getPublishedAt()), instantOf(job.getPublishedAt()),
                intOf(job.getViewCount()), intOf(job.getApplyCount()),
                job.getStatus(), job.getResponsibilities(), job.getRequirements(), listOf(job.getWelfare()),
                job.getDeletedAt() != null, job.getCompanyId());
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ApiCodes.FIELD_VALIDATION, message);
    }

    private static <T> T pick(T provided, T fallback) {
        return provided != null ? provided : fallback;
    }

    /** 去重 + 去空 + 去首尾空格（对齐前端 parseTags 语义）。 */
    private static List<String> normalize(List<String> values) {
        if (values == null) {
            return List.of();
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null) {
                continue;
            }
            String trimmed = value.trim();
            if (!trimmed.isEmpty()) {
                unique.add(trimmed);
            }
        }
        return new ArrayList<>(unique);
    }

    private static String trimmed(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static long days(LocalDateTime publishedAt) {
        if (publishedAt == null) {
            return 0;
        }
        return Math.max(0, ChronoUnit.DAYS.between(publishedAt, LocalDateTime.now(ZoneOffset.UTC)));
    }

    private static Instant instantOf(LocalDateTime time) {
        return time == null ? null : time.toInstant(ZoneOffset.UTC);
    }

    private static int intOf(Integer value) {
        return value == null ? 0 : value;
    }

    private static List<String> listOf(List<String> value) {
        return value == null ? List.of() : value;
    }

    private static String salaryText(JobEntity job) {
        return intOf(job.getSalaryMin()) + "-" + intOf(job.getSalaryMax()) + "K";
    }
}
