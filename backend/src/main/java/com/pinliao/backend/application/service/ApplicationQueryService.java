package com.pinliao.backend.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pinliao.backend.application.ApplicationCodes;
import com.pinliao.backend.application.entity.ApplicationEntity;
import com.pinliao.backend.application.entity.ConversationEntity;
import com.pinliao.backend.application.entity.InterviewEntity;
import com.pinliao.backend.application.entity.ResumeEntity;
import com.pinliao.backend.application.mapper.ApplicationMapper;
import com.pinliao.backend.application.mapper.ConversationMapper;
import com.pinliao.backend.application.mapper.InterviewMapper;
import com.pinliao.backend.application.mapper.ResumeMapper;
import com.pinliao.backend.application.view.ApplicationViews;
import com.pinliao.backend.auth.context.Roles;
import com.pinliao.backend.auth.context.UserContext;
import com.pinliao.backend.auth.entity.UserEntity;
import com.pinliao.backend.auth.entity.UserProfileEntity;
import com.pinliao.backend.auth.mapper.UserMapper;
import com.pinliao.backend.auth.mapper.UserProfileMapper;
import com.pinliao.backend.common.api.ApiCodes;
import com.pinliao.backend.common.api.PageViews;
import com.pinliao.backend.common.exception.BusinessException;
import com.pinliao.backend.job.entity.JobEntity;
import com.pinliao.backend.job.mapper.JobMapper;
import com.pinliao.backend.job.service.JobQueryService;
import com.pinliao.backend.job.view.JobViews;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * 投递 / 面试读服务（契约 §9.2~§9.3、§10.1~§10.3）。
 *
 * <p><b>游标分页</b>：base64(JSON{k,id})，排序键 = 「updatedAt / createdAt DESC, id ASC」；
 * 演示规模下全量取回后内存切片（与 T3a 职位列表同一策略），对外游标格式稳定。</p>
 *
 * <p><b>归属</b>：求职者按 user_id；HR 按租户注入 + 显式 companyId 兜底；
 * 不存在 / 非本人 / 非本公司统一 40410，不泄露存在性（对齐 mock）。</p>
 */
@Service
public class ApplicationQueryService {

    static final int DEFAULT_LIMIT = 20;
    static final int MAX_LIMIT = 100;

    private static final Set<String> APPLICATION_STATUSES = Set.of(
            "SUBMITTED", "VIEWED", "INTERVIEW", "OFFER", "REJECTED", "WITHDRAWN");
    private static final Set<String> INTERVIEW_STATUSES = Set.of(
            "PENDING", "ACCEPTED", "DECLINED", "COMPLETED", "CANCELLED");
    private static final ObjectMapper JSON = new ObjectMapper();

    private final ApplicationMapper applicationMapper;
    private final InterviewMapper interviewMapper;
    private final ConversationMapper conversationMapper;
    private final ResumeMapper resumeMapper;
    private final JobMapper jobMapper;
    private final UserMapper userMapper;
    private final UserProfileMapper userProfileMapper;
    private final JobQueryService jobQueryService;

    public ApplicationQueryService(ApplicationMapper applicationMapper, InterviewMapper interviewMapper,
                                   ConversationMapper conversationMapper, ResumeMapper resumeMapper,
                                   JobMapper jobMapper, UserMapper userMapper,
                                   UserProfileMapper userProfileMapper, JobQueryService jobQueryService) {
        this.applicationMapper = applicationMapper;
        this.interviewMapper = interviewMapper;
        this.conversationMapper = conversationMapper;
        this.resumeMapper = resumeMapper;
        this.jobMapper = jobMapper;
        this.userMapper = userMapper;
        this.userProfileMapper = userProfileMapper;
        this.jobQueryService = jobQueryService;
    }

    /** §9.2 我的投递列表（求职者；updatedAt DESC, id ASC）。 */
    public PageViews.Page<ApplicationViews.WithJob> listApplications(String status, String cursor, Integer limit) {
        UserContext.CurrentUser user = Roles.requireSeeker();
        String normalized = normalizeStatus(status, APPLICATION_STATUSES);
        List<ApplicationEntity> rows = applicationMapper.selectList(new LambdaQueryWrapper<ApplicationEntity>()
                .eq(ApplicationEntity::getUserId, user.userId())
                .isNull(ApplicationEntity::getDeletedAt)
                .eq(normalized != null, ApplicationEntity::getStatus, normalized));
        rows.sort(Comparator.comparing((ApplicationEntity a) -> epochMillis(a.getUpdatedAt())).reversed()
                .thenComparing(ApplicationEntity::getId));

        Slice<ApplicationEntity> slice = slice(rows, cursor, limit,
                ApplicationEntity::getId, a -> epochMillis(a.getUpdatedAt()));
        List<ApplicationViews.WithJob> views = slice.items().stream()
                .map(a -> ApplicationViews.WithJob.from(a, jobSummary(a.getJobId())))
                .toList();
        return new PageViews.Page<>(views,
                new PageViews.Pagination(slice.nextCursor(), slice.hasMore(), rows.size()));
    }

    /** §9.3 投递详情（本人 / 本公司；含职位、简历、全部面试与会话占位 id）。 */
    public ApplicationViews.Detail detail(String applicationId) {
        UserContext.CurrentUser user = UserContext.require();
        ApplicationEntity app = applicationMapper.selectById(applicationId);
        if (app == null || !canAccess(user, app)) {
            throw applicationNotFound();
        }
        JobEntity job = jobMapper.selectById(app.getJobId());
        ResumeEntity resume = resumeMapper.selectById(app.getResumeId());
        ConversationEntity conversation = conversationMapper.selectOne(new LambdaQueryWrapper<ConversationEntity>()
                .eq(ConversationEntity::getJobId, app.getJobId())
                .eq(ConversationEntity::getUserId, app.getUserId())
                .last("LIMIT 1"));
        List<ApplicationViews.Interview> interviews = sortInterviews(interviewMapper.selectList(
                        new LambdaQueryWrapper<InterviewEntity>()
                                .eq(InterviewEntity::getApplicationId, app.getId())))
                .stream().map(ApplicationViews.Interview::from).toList();
        return ApplicationViews.Detail.of(app,
                conversation == null ? null : conversation.getId(),
                job == null ? null : jobQueryService.detailOf(job),
                resume == null ? null : ApplicationViews.Resume.from(resume, parseContent(resume.getContent())),
                interviews);
    }

    /** §10.1 求职者面试列表（createdAt DESC, id ASC；含职位摘要）。 */
    public PageViews.Page<ApplicationViews.InterviewWithJob> listInterviews(String status, String cursor, Integer limit) {
        UserContext.CurrentUser user = Roles.requireSeeker();
        String normalized = normalizeStatus(status, INTERVIEW_STATUSES);
        List<InterviewEntity> rows = interviewMapper.selectByUser(user.userId(), normalized);
        rows.sort(Comparator.comparing((InterviewEntity i) -> epochMillis(i.getCreatedAt())).reversed()
                .thenComparing(InterviewEntity::getId));

        Slice<InterviewEntity> slice = slice(rows, cursor, limit,
                InterviewEntity::getId, i -> epochMillis(i.getCreatedAt()));
        List<ApplicationViews.InterviewWithJob> views = slice.items().stream()
                .map(i -> ApplicationViews.InterviewWithJob.from(i, jobSummaryOfApplication(i.getApplicationId())))
                .toList();
        return new PageViews.Page<>(views,
                new PageViews.Pagination(slice.nextCursor(), slice.hasMore(), rows.size()));
    }

    /** §10.2 招聘者面试列表（本公司；createdAt DESC, id ASC；含候选人）。 */
    public PageViews.Page<ApplicationViews.InterviewWithCandidate> listHrInterviews(
            String status, String cursor, Integer limit) {
        UserContext.CurrentUser user = Roles.requireManager();
        String normalized = normalizeStatus(status, INTERVIEW_STATUSES);
        List<InterviewEntity> rows = interviewMapper.selectList(new LambdaQueryWrapper<InterviewEntity>()
                .eq(InterviewEntity::getCompanyId, user.companyId())
                .eq(normalized != null, InterviewEntity::getStatus, normalized));
        rows.sort(Comparator.comparing((InterviewEntity i) -> epochMillis(i.getCreatedAt())).reversed()
                .thenComparing(InterviewEntity::getId));

        Slice<InterviewEntity> slice = slice(rows, cursor, limit,
                InterviewEntity::getId, i -> epochMillis(i.getCreatedAt()));
        List<ApplicationViews.InterviewWithCandidate> views = slice.items().stream()
                .map(this::candidateView)
                .toList();
        return new PageViews.Page<>(views,
                new PageViews.Pagination(slice.nextCursor(), slice.hasMore(), rows.size()));
    }

    /** §10.3 面试详情（参与方：求职者本人 / 本公司 HR）。 */
    public ApplicationViews.Interview interviewDetail(String interviewId) {
        UserContext.CurrentUser user = UserContext.require();
        InterviewEntity interview = interviewMapper.selectById(interviewId);
        if (interview == null) {
            throw interviewNotFound();
        }
        ApplicationEntity app = applicationMapper.selectById(interview.getApplicationId());
        if (app == null) {
            throw interviewNotFound();
        }
        if (Roles.isSeeker(user)) {
            if (!user.userId().equals(app.getUserId())) {
                throw interviewNotFound();
            }
        } else if (Roles.isManager(user)) {
            if (user.companyId() == null || !user.companyId().equals(app.getCompanyId())) {
                throw interviewNotFound();
            }
        } else {
            throw new BusinessException(ApiCodes.FORBIDDEN, "无权查看该面试");
        }
        return ApplicationViews.Interview.from(interview);
    }

    // ------------------------------------------------------------------
    // 游标 / 映射
    // ------------------------------------------------------------------

    /** 游标：更新时间 / 创建时间毫秒 + id。 */
    record Cursor(long k, String id) {
    }

    record Slice<T>(List<T> items, String nextCursor, boolean hasMore) {
    }

    private static <T> Slice<T> slice(List<T> rows, String rawCursor, Integer limit,
                                      Function<T, String> idOf, Function<T, Long> keyOf) {
        int size = clampLimit(limit);
        int start = 0;
        Cursor cursor = trimToNull(rawCursor) == null ? null : decodeCursor(rawCursor);
        if (cursor != null) {
            start = firstIndexAfter(rows, cursor, idOf, keyOf);
        }
        int end = Math.min(start + size, rows.size());
        List<T> items = new ArrayList<>(rows.subList(start, end));
        boolean hasMore = end < rows.size();
        String nextCursor = null;
        if (hasMore && !items.isEmpty()) {
            T last = items.get(items.size() - 1);
            nextCursor = encodeCursor(keyOf.apply(last), idOf.apply(last));
        }
        return new Slice<>(items, nextCursor, hasMore);
    }

    private static <T> int firstIndexAfter(List<T> rows, Cursor cursor,
                                           Function<T, String> idOf, Function<T, Long> keyOf) {
        for (int i = 0; i < rows.size(); i++) {
            T row = rows.get(i);
            long k = keyOf.apply(row);
            if (k < cursor.k() || (k == cursor.k() && idOf.apply(row).compareTo(cursor.id()) > 0)) {
                return i;
            }
        }
        return rows.size();
    }

    static String encodeCursor(long keyMillis, String id) {
        try {
            ObjectNode node = JSON.createObjectNode();
            node.put("k", keyMillis);
            node.put("id", id);
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
            return new Cursor(node.path("k").asLong(), node.path("id").asText());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw invalidCursor();
        }
    }

    private static BusinessException invalidCursor() {
        return new BusinessException(ApiCodes.PARAM_INVALID, "分页游标无效");
    }

    private static String normalizeStatus(String status, Set<String> allowed) {
        String normalized = trimToNull(status);
        if (normalized == null || "ALL".equalsIgnoreCase(normalized)) {
            return null;
        }
        if (!allowed.contains(normalized)) {
            throw new BusinessException(ApiCodes.FIELD_VALIDATION, "状态筛选值无效");
        }
        return normalized;
    }

    private static int clampLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.max(1, Math.min(limit, MAX_LIMIT));
    }

    private boolean canAccess(UserContext.CurrentUser user, ApplicationEntity app) {
        if (Roles.isSeeker(user)) {
            return user.userId().equals(app.getUserId());
        }
        if (Roles.isManager(user)) {
            return user.companyId() != null && user.companyId().equals(app.getCompanyId());
        }
        return false;
    }

    private JobViews.Summary jobSummary(String jobId) {
        JobEntity job = jobMapper.selectById(jobId);
        return job == null ? null : jobQueryService.summaryOf(job);
    }

    private JobViews.Summary jobSummaryOfApplication(String applicationId) {
        ApplicationEntity app = applicationMapper.selectById(applicationId);
        return app == null ? null : jobSummary(app.getJobId());
    }

    private ApplicationViews.InterviewWithCandidate candidateView(InterviewEntity interview) {
        ApplicationEntity app = applicationMapper.selectById(interview.getApplicationId());
        UserEntity user = app == null ? null : userMapper.selectById(app.getUserId());
        UserProfileEntity profile = app == null ? null : userProfileMapper.selectOne(
                new LambdaQueryWrapper<UserProfileEntity>()
                        .eq(UserProfileEntity::getUserId, app.getUserId())
                        .last("LIMIT 1"));
        ApplicationViews.CandidateUser candidateUser = new ApplicationViews.CandidateUser(
                app == null ? null : app.getUserId(), user == null ? null : user.getPhone());
        ApplicationViews.CandidateProfile candidateProfile = new ApplicationViews.CandidateProfile(
                profile == null ? "候选人" : profile.getName(), profile == null ? null : profile.getAvatarUrl());
        return ApplicationViews.InterviewWithCandidate.from(interview, candidateUser, candidateProfile);
    }

    private static List<InterviewEntity> sortInterviews(List<InterviewEntity> rows) {
        List<InterviewEntity> sorted = new ArrayList<>(rows);
        sorted.sort(Comparator.comparing((InterviewEntity i) -> epochMillis(i.getCreatedAt())).reversed()
                .thenComparing(InterviewEntity::getId));
        return sorted;
    }

    private static JsonNode parseContent(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        try {
            return JSON.readTree(content);
        } catch (Exception e) {
            return null;
        }
    }

    private static long epochMillis(LocalDateTime time) {
        return time == null ? 0 : time.toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    private static BusinessException applicationNotFound() {
        return new BusinessException(ApplicationCodes.APPLICATION_NOT_FOUND, "投递记录不存在");
    }

    private static BusinessException interviewNotFound() {
        return new BusinessException(ApplicationCodes.APPLICATION_NOT_FOUND, "面试邀请不存在");
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
