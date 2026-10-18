package com.pinliao.backend.job.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pinliao.backend.auth.context.UserContext;
import com.pinliao.backend.auth.entity.CompanyEntity;
import com.pinliao.backend.auth.entity.UserProfileEntity;
import com.pinliao.backend.auth.mapper.CompanyMapper;
import com.pinliao.backend.auth.mapper.UserProfileMapper;
import com.pinliao.backend.common.api.ApiCodes;
import com.pinliao.backend.common.exception.BusinessException;
import com.pinliao.backend.job.JobCodes;
import com.pinliao.backend.job.entity.JobEntity;
import com.pinliao.backend.job.mapper.JobMapper;
import com.pinliao.backend.job.view.JobViews;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * 职位公开查询（契约 §6.1~§6.4 浏览/搜索/详情/相似 + §6.9 浏览埋点）。
 *
 * <p><b>排序语义</b>（对齐 mock {@code src/api/mock/jobs.ts} 的 listJobs）：</p>
 * <ul>
 *   <li>SQL 固定按「published_at DESC, id ASC」取行，保证任何模式下同分/同日期的行序确定；</li>
 *   <li>{@code sort=recommend}（默认）且登录者为求职者时，按「期望二级岗位 = 2 分 / 期望一级分类 = 1 分」加权，
 *       用稳定排序保证同分行序 = SQL 行序；HR / 匿名不参与加权（等价于纯发布倒序）；</li>
 *   <li>{@code sort=latest} 即 SQL 行序。</li>
 * </ul>
 *
 * <p><b>分页语义</b>：游标 = base64(JSON{s,k,id})，编码「得分 + 发布时间毫秒 + id」三元组（契约 §3.2 的
 * {@code {"id":"123"}} 形态扩展）；响应含 total。演示规模（≤100 行）下过滤与排序全量取回后在做内存切片，
 * 数据量上来后把游标条件下推 SQL 即可（对外游标格式不变）。</p>
 *
 * <p><b>days 字段</b>：按当前时间与 publishedAt 实时计算（mock 的 days 在页面加载时冻结，后端按真实
 * 流逝计算，语义更准且随时间自然增长）。</p>
 */
@Service
public class JobQueryService {

    /** 契约 §3.2：limit 默认 20、最大 100 */
    static final int DEFAULT_LIMIT = 20;
    static final int MAX_LIMIT = 100;
    /** 详情页相似职位默认条数（对齐 mock getJobDetail 的 slice(0, 6)） */
    static final int SIMILAR_DEFAULT_LIMIT = 6;
    /** 相似职位 limit 上限（防止被当作全量接口使用） */
    static final int SIMILAR_MAX_LIMIT = 50;
    /** §6.9 防重复浏览：ip + jobId + 时间窗口 */
    static final Duration VIEW_DEDUPE_TTL = Duration.ofMinutes(30);

    private static final String VIEW_DEDUPE_KEY = "pinliao:job:view:%s:%s";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String SORT_LATEST = "latest";
    private static final String ROLE_SEEKER = "JOB_SEEKER";

    private final JobMapper jobMapper;
    private final CompanyMapper companyMapper;
    private final UserProfileMapper userProfileMapper;
    private final StringRedisTemplate redis;

    public JobQueryService(JobMapper jobMapper, CompanyMapper companyMapper,
                           UserProfileMapper userProfileMapper, StringRedisTemplate redis) {
        this.jobMapper = jobMapper;
        this.companyMapper = companyMapper;
        this.userProfileMapper = userProfileMapper;
        this.redis = redis;
    }

    /** 列表 / 搜索查询参数（§6.1 与 §6.2 共享）。 */
    public record Query(String q, String city, String cat, String sub, String exp, String sal,
                        String sort, String cursor, Integer limit) {
    }

    /**
     * 详情结果：视图 + ETag。
     *
     * <p>§6.3 要求「支持 ETag」，但统一包络含逐请求随机的 traceId，无法按响应体算 ETag；
     * 因此用「jobId + updatedAt」版本号做内容指纹，由控制器比对 If-None-Match 返回 304。</p>
     */
    public record DetailResult(JobViews.Detail detail, String etag) {
    }

    /** 游标三元组：得分（recommend 加权）/ 发布时间毫秒 / id。 */
    record Cursor(int score, long publishedMillis, String id) {
    }

    /** §6.1 列表（公开；登录求职者的 recommend 会按期望岗位加权）。 */
    public JobViews.Page<JobViews.Summary> list(Query query) {
        int limit = clampLimit(query.limit());
        boolean latest = SORT_LATEST.equals(trimToNull(query.sort()));

        String expectCat;
        String expectSub;
        if (latest) {
            expectCat = null;
            expectSub = null;
        } else {
            UserProfileEntity profile = currentSeekerProfile();
            expectCat = profile == null ? null : profile.getExpectCat();
            expectSub = profile == null ? null : profile.getExpectSub();
        }

        int[] salary = salaryRange(query.sal());
        List<JobEntity> rows = jobMapper.selectVisible(
                notBlankAndNot(query.city(), "全国"),
                notBlankAndNot(query.cat(), "全部"),
                notBlankAndNot(query.sub(), "全部"),
                notBlankAndNot(query.exp(), "不限"),
                salary[0], salary[1],
                likePattern(query.q()));

        // 行序 = published_at DESC, id ASC；加权用稳定排序，同分保持行序
        List<JobEntity> sorted = new ArrayList<>(rows);
        if (!latest && (expectCat != null || expectSub != null)) {
            sorted.sort(Comparator.comparingInt((JobEntity job) -> score(job, expectCat, expectSub)).reversed());
        }

        int start = 0;
        String rawCursor = trimToNull(query.cursor());
        if (rawCursor != null) {
            start = firstIndexAfter(sorted, decodeCursor(rawCursor), expectCat, expectSub);
        }
        int end = Math.min(start + limit, sorted.size());
        List<JobEntity> page = sorted.subList(start, end);

        boolean hasMore = end < sorted.size();
        String nextCursor = null;
        if (hasMore && !page.isEmpty()) {
            JobEntity last = page.get(page.size() - 1);
            nextCursor = encodeCursor(last, score(last, expectCat, expectSub));
        }
        return new JobViews.Page<>(toSummaries(page),
                new JobViews.Pagination(nextCursor, hasMore, sorted.size()));
    }

    /** §6.3 详情（含公司 / HR / 相似职位；不可见职位抛 40410）。 */
    public DetailResult detail(String jobId) {
        JobEntity job = requireVisibleJob(jobId);
        List<JobEntity> similarRows = jobMapper.selectSimilar(jobId, job.getSub(), job.getCat(), SIMILAR_DEFAULT_LIMIT);
        Map<String, CompanyEntity> companies = loadCompanies(Stream.concat(Stream.of(job), similarRows.stream())
                .map(JobEntity::getCompanyId).filter(Objects::nonNull).distinct().toList());
        JobViews.Detail detail = toDetail(job, companies.get(job.getCompanyId()), toSummaries(similarRows, companies));
        long version = job.getUpdatedAt() == null ? 0 : job.getUpdatedAt().toEpochSecond(ZoneOffset.UTC);
        return new DetailResult(detail, "\"" + job.getId() + "-" + version + "\"");
    }

    /** §6.4 相似职位（同 sub 或同 cat，排除自身；limit 默认 6）。 */
    public List<JobViews.Summary> similar(String jobId, Integer limit) {
        JobEntity job = requireVisibleJob(jobId);
        int size = limit == null ? SIMILAR_DEFAULT_LIMIT : Math.max(1, Math.min(limit, SIMILAR_MAX_LIMIT));
        return toSummaries(jobMapper.selectSimilar(jobId, job.getSub(), job.getCat(), size));
    }

    /** §6.9 浏览埋点：ip + jobId + 30 分钟窗口去重后累加（Redis SETNX）。 */
    public void recordView(String jobId, String clientIp) {
        String ip = trimToNull(clientIp) == null ? "unknown" : clientIp.trim();
        Boolean first = redis.opsForValue()
                .setIfAbsent(VIEW_DEDUPE_KEY.formatted(jobId, ip), "1", VIEW_DEDUPE_TTL);
        if (Boolean.TRUE.equals(first)) {
            jobMapper.incrementViewCount(jobId);
        }
    }

    // ---------------------------------------------------------------
    // 游标编解码（包级可见便于单测）
    // ---------------------------------------------------------------

    static String encodeCursor(JobEntity job, int score) {
        try {
            ObjectNode node = JSON.createObjectNode();
            node.put("s", score);
            node.put("k", epochMillis(job.getPublishedAt()));
            node.put("id", job.getId());
            return Base64.getUrlEncoder().withoutPadding().encodeToString(JSON.writeValueAsBytes(node));
        } catch (Exception e) {
            throw new IllegalStateException("游标编码失败", e);
        }
    }

    static Cursor decodeCursor(String raw) {
        byte[] bytes;
        try {
            bytes = Base64.getUrlDecoder().decode(raw);
        } catch (IllegalArgumentException urlSafeFailed) {
            try {
                bytes = Base64.getDecoder().decode(raw);
            } catch (IllegalArgumentException standardFailed) {
                throw invalidCursor();
            }
        }
        try {
            JsonNode node = JSON.readTree(bytes);
            if (node == null || !node.path("k").isNumber() || !node.path("id").isTextual()) {
                throw invalidCursor();
            }
            return new Cursor(node.path("s").asInt(0), node.path("k").asLong(), node.path("id").asText());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw invalidCursor();
        }
    }

    private static BusinessException invalidCursor() {
        return new BusinessException(ApiCodes.PARAM_INVALID, "分页游标无效");
    }

    private static int firstIndexAfter(List<JobEntity> sorted, Cursor cursor, String expectCat, String expectSub) {
        for (int i = 0; i < sorted.size(); i++) {
            if (isAfter(sorted.get(i), cursor, expectCat, expectSub)) {
                return i;
            }
        }
        return sorted.size();
    }

    /** 排序键「得分 DESC, 发布毫秒 DESC, id ASC」下，job 是否排在游标之后。 */
    private static boolean isAfter(JobEntity job, Cursor cursor, String expectCat, String expectSub) {
        int s = score(job, expectCat, expectSub);
        if (s != cursor.score()) {
            return s < cursor.score();
        }
        long k = epochMillis(job.getPublishedAt());
        if (k != cursor.publishedMillis()) {
            return k < cursor.publishedMillis();
        }
        return job.getId().compareTo(cursor.id()) > 0;
    }

    // ---------------------------------------------------------------
    // 映射与工具
    // ---------------------------------------------------------------

    private List<JobViews.Summary> toSummaries(List<JobEntity> jobs) {
        Map<String, CompanyEntity> companies = loadCompanies(jobs.stream()
                .map(JobEntity::getCompanyId).filter(Objects::nonNull).distinct().toList());
        return toSummaries(jobs, companies);
    }

    private List<JobViews.Summary> toSummaries(List<JobEntity> jobs, Map<String, CompanyEntity> companies) {
        return jobs.stream().map(job -> toSummary(job, companies.get(job.getCompanyId()))).toList();
    }

    private JobViews.Summary toSummary(JobEntity job, CompanyEntity company) {
        return new JobViews.Summary(
                job.getId(), job.getTitle(), intOf(job.getSalaryMin()), intOf(job.getSalaryMax()), salaryText(job),
                job.getCity(), job.getArea(), job.getExp(), job.getEdu(), job.getCat(), job.getSub(),
                listOf(job.getTags()), briefOf(job, company), hrBrief(job),
                days(job.getPublishedAt()), instantOf(job.getPublishedAt()),
                intOf(job.getViewCount()), intOf(job.getApplyCount()));
    }

    private JobViews.Detail toDetail(JobEntity job, CompanyEntity company, List<JobViews.Summary> similar) {
        return new JobViews.Detail(
                job.getId(), job.getTitle(), intOf(job.getSalaryMin()), intOf(job.getSalaryMax()), salaryText(job),
                job.getCity(), job.getArea(), job.getExp(), job.getEdu(), job.getCat(), job.getSub(),
                listOf(job.getTags()), briefOf(job, company), hrBrief(job),
                days(job.getPublishedAt()), instantOf(job.getPublishedAt()),
                intOf(job.getViewCount()), intOf(job.getApplyCount()),
                job.getCompanyId(), job.getHrId(),
                job.getResponsibilities(), job.getRequirements(), listOf(job.getWelfare()), null,
                similar);
    }

    private UserProfileEntity currentSeekerProfile() {
        UserContext.CurrentUser user = UserContext.get();
        if (user == null || !ROLE_SEEKER.equals(user.role())) {
            return null;
        }
        return userProfileMapper.selectOne(new LambdaQueryWrapper<UserProfileEntity>()
                .eq(UserProfileEntity::getUserId, user.userId())
                .last("LIMIT 1"));
    }

    private JobEntity requireVisibleJob(String jobId) {
        JobEntity job = jobMapper.selectVisibleById(jobId);
        if (job == null) {
            throw new BusinessException(JobCodes.JOB_NOT_FOUND, "职位不存在");
        }
        return job;
    }

    private Map<String, CompanyEntity> loadCompanies(List<String> companyIds) {
        if (companyIds.isEmpty()) {
            return Map.of();
        }
        Map<String, CompanyEntity> map = new HashMap<>();
        for (CompanyEntity company : companyMapper.selectByIds(companyIds)) {
            map.put(company.getId(), company);
        }
        return map;
    }

    private static JobViews.CompanyBrief briefOf(JobEntity job, CompanyEntity company) {
        if (company == null) {
            return new JobViews.CompanyBrief(job.getCompanyId(), null, null, null);
        }
        return new JobViews.CompanyBrief(company.getId(), company.getName(), company.getStage(), company.getSize());
    }

    private static JobViews.HrBrief hrBrief(JobEntity job) {
        return new JobViews.HrBrief(job.getHrId(), job.getHrName(), job.getHrTitle());
    }

    private static int score(JobEntity job, String expectCat, String expectSub) {
        if (expectSub != null && expectSub.equals(job.getSub())) {
            return 2;
        }
        if (expectCat != null && expectCat.equals(job.getCat())) {
            return 1;
        }
        return 0;
    }

    private static long days(LocalDateTime publishedAt) {
        if (publishedAt == null) {
            return 0;
        }
        return Math.max(0, ChronoUnit.DAYS.between(publishedAt, LocalDateTime.now(ZoneOffset.UTC)));
    }

    private static long epochMillis(LocalDateTime time) {
        return time == null ? 0 : time.toInstant(ZoneOffset.UTC).toEpochMilli();
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

    static int clampLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.max(1, Math.min(limit, MAX_LIMIT));
    }

    /** 薪资区间 → [min, max]（K），与 mock dict.ts SAL_RANGE 一致；未知/不限回退 [0,999]。 */
    private static int[] salaryRange(String sal) {
        return switch (sal == null ? "" : sal) {
            case "10K以下" -> new int[]{0, 10};
            case "10-20K" -> new int[]{10, 20};
            case "20-40K" -> new int[]{20, 40};
            case "40K以上" -> new int[]{40, 999};
            default -> new int[]{0, 999};
        };
    }

    /** LIKE 模式：转义 % _ \（MySQL 默认反斜杠转义），两端补 %。 */
    private static String likePattern(String q) {
        String keyword = trimToNull(q);
        if (keyword == null) {
            return null;
        }
        String escaped = keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** 空串与占位默认值（全国/全部/不限）都视为「不过滤」。 */
    private static String notBlankAndNot(String value, String placeholder) {
        String trimmed = trimToNull(value);
        return trimmed == null || trimmed.equals(placeholder) ? null : trimmed;
    }
}
