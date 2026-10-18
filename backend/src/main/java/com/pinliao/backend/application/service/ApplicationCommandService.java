package com.pinliao.backend.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pinliao.backend.application.ApplicationCodes;
import com.pinliao.backend.application.dto.ApplicationRequests;
import com.pinliao.backend.application.entity.ApplicationEntity;
import com.pinliao.backend.application.entity.ConversationEntity;
import com.pinliao.backend.application.entity.InterviewEntity;
import com.pinliao.backend.application.entity.MessageEntity;
import com.pinliao.backend.application.entity.ResumeEntity;
import com.pinliao.backend.application.mapper.ApplicationMapper;
import com.pinliao.backend.application.mapper.ConversationMapper;
import com.pinliao.backend.application.mapper.InterviewMapper;
import com.pinliao.backend.application.mapper.MessageMapper;
import com.pinliao.backend.application.mapper.ResumeMapper;
import com.pinliao.backend.application.view.ApplicationViews;
import com.pinliao.backend.auth.context.Roles;
import com.pinliao.backend.auth.context.UserContext;
import com.pinliao.backend.auth.entity.UserProfileEntity;
import com.pinliao.backend.auth.mapper.UserProfileMapper;
import com.pinliao.backend.common.api.ApiCodes;
import com.pinliao.backend.common.exception.BusinessException;
import com.pinliao.backend.job.JobCodes;
import com.pinliao.backend.job.entity.JobEntity;
import com.pinliao.backend.job.mapper.JobMapper;
import com.pinliao.backend.job.service.JobQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

/**
 * 投递 / 面试写服务（契约 §9.1、§9.4、§9.5、§10.4~§10.5、§8.7）。
 *
 * <p><b>状态机</b>（对齐 mock {@code src/api/mock/applications.ts}）：</p>
 * <ul>
 *   <li>投递：SUBMITTED；重复投递 40911；撤回后重新投递复用原记录并回到 SUBMITTED；</li>
 *   <li>撤回：仅 SUBMITTED / VIEWED / INTERVIEW 可撤回，否则 40913；</li>
 *   <li>HR 标记：VIEWED / INTERVIEW / OFFER / REJECTED；已撤回 → 40913；</li>
 *   <li>约面试：REJECTED / WITHDRAWN → 40914；已有 PENDING → 40915；时间必须晚于当前 → 42210；
 *       创建成功后投递置 INTERVIEW，并写入 INTERVIEW_INVITE 消息；</li>
 *   <li>接受 / 拒绝：仅本人且 PENDING / 幂等终态可操作，其余 40913。</li>
 * </ul>
 *
 * <p><b>会话占位</b>：投递成功（含重新激活）即按 {@code (jobId, userId)} 幂等创建会话，
 * 解决「HR 主动触达 / 约面试需要 conversationId」而 §8 尚未实现的问题（T6 复用该行，不重复建）。</p>
 *
 * <p><b>id 约定</b>：{@code app-new-{seq}} / {@code int-new-{seq}} / {@code conv-new-{seq}} /
 * {@code msg-new-{seq}}，序号 = 库内同前缀最大序号 + 1（递增派生，禁时间戳 / 随机）；
 * 冒烟产物按前缀排除，不污染种子巡检。</p>
 */
@Service
public class ApplicationCommandService {

    static final String STATUS_SUBMITTED = "SUBMITTED";
    static final String STATUS_VIEWED = "VIEWED";
    static final String STATUS_INTERVIEW = "INTERVIEW";
    static final String STATUS_REJECTED = "REJECTED";
    static final String STATUS_WITHDRAWN = "WITHDRAWN";
    static final String STATUS_PENDING = "PENDING";
    static final String STATUS_ACCEPTED = "ACCEPTED";
    static final String STATUS_DECLINED = "DECLINED";

    static final String FORMAT_ONSITE = "ONSITE";
    static final String FORMAT_VIDEO = "VIDEO";
    static final String FORMAT_PHONE = "PHONE";

    private static final Set<String> WITHDRAWABLE = Set.of(STATUS_SUBMITTED, STATUS_VIEWED, STATUS_INTERVIEW);
    private static final Set<String> HR_STATUSES = Set.of(STATUS_VIEWED, STATUS_INTERVIEW, "OFFER", STATUS_REJECTED);
    private static final Set<String> INVITE_BLOCKED = Set.of(STATUS_REJECTED, STATUS_WITHDRAWN);
    private static final Set<String> FORMATS = Set.of(FORMAT_ONSITE, FORMAT_VIDEO, FORMAT_PHONE);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final ApplicationMapper applicationMapper;
    private final InterviewMapper interviewMapper;
    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final ResumeMapper resumeMapper;
    private final JobMapper jobMapper;
    private final UserProfileMapper userProfileMapper;
    private final JobQueryService jobQueryService;

    public ApplicationCommandService(ApplicationMapper applicationMapper, InterviewMapper interviewMapper,
                                     ConversationMapper conversationMapper, MessageMapper messageMapper,
                                     ResumeMapper resumeMapper, JobMapper jobMapper,
                                     UserProfileMapper userProfileMapper, JobQueryService jobQueryService) {
        this.applicationMapper = applicationMapper;
        this.interviewMapper = interviewMapper;
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.resumeMapper = resumeMapper;
        this.jobMapper = jobMapper;
        this.userProfileMapper = userProfileMapper;
        this.jobQueryService = jobQueryService;
    }

    // ------------------------------------------------------------------
    // §9.1 投递
    // ------------------------------------------------------------------

    /** 投递职位；已投递 40911，撤回后重新投递复用原记录。 */
    @Transactional
    public ApplicationViews.Application apply(ApplicationRequests.Apply input) {
        UserContext.CurrentUser user = Roles.requireSeeker();
        String jobId = trimToNull(input.jobId());
        if (jobId == null) {
            throw paramInvalid("请选择职位");
        }
        JobEntity job = jobMapper.selectVisibleById(jobId);
        if (job == null) {
            throw new BusinessException(JobCodes.JOB_NOT_FOUND, "职位不存在或已下线");
        }
        String coverLetter = trimToNull(input.coverLetter());
        if (coverLetter != null && coverLetter.length() > 500) {
            throw paramInvalid("打招呼语不能超过 500 字");
        }
        String resumeId = resolveResumeId(user.userId(), input.resumeId());

        ApplicationEntity existing = applicationMapper.selectOne(new LambdaQueryWrapper<ApplicationEntity>()
                .eq(ApplicationEntity::getUserId, user.userId())
                .eq(ApplicationEntity::getJobId, jobId)
                .last("LIMIT 1"));
        if (existing != null && !STATUS_WITHDRAWN.equals(existing.getStatus())) {
            throw new BusinessException(ApplicationCodes.ALREADY_APPLIED, "您已投递过该职位");
        }

        LocalDateTime now = nowMillis();
        if (existing != null) {
            existing.setStatus(STATUS_SUBMITTED);
            existing.setResumeId(resumeId);
            existing.setCoverLetter(coverLetter);
            existing.setUpdatedAt(now);
            applicationMapper.updateById(existing);
            ensureConversation(job, user.userId(), now);
            return ApplicationViews.Application.from(existing);
        }

        ApplicationEntity app = new ApplicationEntity();
        app.setId("app-new-" + applicationMapper.nextNewSeq());
        app.setUserId(user.userId());
        app.setJobId(jobId);
        app.setCompanyId(job.getCompanyId());
        app.setResumeId(resumeId);
        app.setStatus(STATUS_SUBMITTED);
        app.setCoverLetter(coverLetter);
        app.setCreatedAt(now);
        app.setUpdatedAt(now);
        applicationMapper.insert(app);
        ensureConversation(job, user.userId(), now);
        return ApplicationViews.Application.from(app);
    }

    // ------------------------------------------------------------------
    // §9.4 撤回 / §9.5 HR 状态
    // ------------------------------------------------------------------

    /** §9.4 撤回投递（仅待处理 / 已查看 / 面试中）。 */
    @Transactional
    public ApplicationViews.Application withdraw(String applicationId) {
        UserContext.CurrentUser user = Roles.requireSeeker();
        ApplicationEntity app = applicationMapper.selectById(applicationId);
        if (app == null || !user.userId().equals(app.getUserId())) {
            throw applicationNotFound();
        }
        if (!WITHDRAWABLE.contains(app.getStatus())) {
            throw new BusinessException(ApplicationCodes.STATUS_CONFLICT, "当前状态不可撤回");
        }
        app.setStatus(STATUS_WITHDRAWN);
        app.setUpdatedAt(nowMillis());
        applicationMapper.updateById(app);
        return ApplicationViews.Application.from(app);
    }

    /** §9.5 HR 更新投递状态（已撤回 40913；非法状态 42201）。 */
    @Transactional
    public ApplicationViews.Application updateStatus(String applicationId, ApplicationRequests.StatusChange input) {
        UserContext.CurrentUser user = Roles.requireManager();
        ApplicationEntity app = applicationMapper.selectById(applicationId);
        if (app == null || !user.companyId().equals(app.getCompanyId())) {
            throw applicationNotFound();
        }
        String status = trimToNull(input.status());
        if (status == null || !HR_STATUSES.contains(status)) {
            throw paramInvalid("投递状态无效");
        }
        if (STATUS_WITHDRAWN.equals(app.getStatus())) {
            throw new BusinessException(ApplicationCodes.STATUS_CONFLICT, "候选已撤回投递，无法变更状态");
        }
        // note 暂不落库（契约预留操作备注；审计日志在后续迭代补）
        app.setStatus(status);
        app.setUpdatedAt(nowMillis());
        applicationMapper.updateById(app);
        return ApplicationViews.Application.from(app);
    }

    // ------------------------------------------------------------------
    // §8.7 约面试（创建 Interview + 更新投递 + 邀请消息）
    // ------------------------------------------------------------------

    /** §8.7 约面试；返回契约规定的 INTERVIEW_INVITE 消息。 */
    @Transactional
    public ApplicationViews.Message invite(String conversationId, ApplicationRequests.Invite input) {
        UserContext.CurrentUser user = Roles.requireManager();
        ConversationEntity conversation = conversationMapper.selectById(conversationId);
        if (conversation == null || !user.companyId().equals(conversation.getCompanyId())) {
            throw new BusinessException(ApplicationCodes.APPLICATION_NOT_FOUND, "会话不存在");
        }
        ApplicationEntity app = applicationMapper.selectOne(new LambdaQueryWrapper<ApplicationEntity>()
                .eq(ApplicationEntity::getJobId, conversation.getJobId())
                .eq(ApplicationEntity::getUserId, conversation.getUserId())
                .last("LIMIT 1"));
        if (app == null || !user.companyId().equals(app.getCompanyId())) {
            throw new BusinessException(ApplicationCodes.APPLICATION_NOT_FOUND, "投递记录不存在");
        }
        if (INVITE_BLOCKED.contains(app.getStatus())) {
            throw new BusinessException(ApplicationCodes.INVITE_NOT_ALLOWED, "当前状态不可发起面试");
        }
        long pending = interviewMapper.selectCount(new LambdaQueryWrapper<InterviewEntity>()
                .eq(InterviewEntity::getApplicationId, app.getId())
                .eq(InterviewEntity::getStatus, STATUS_PENDING));
        if (pending > 0) {
            throw new BusinessException(ApplicationCodes.PENDING_INVITE_EXISTS, "已有待确认的面试邀请，请等待候选人回应");
        }

        String format = trimToNull(input.format());
        if (format == null || !FORMATS.contains(format)) {
            throw paramInvalid("面试形式无效");
        }
        LocalDateTime scheduledAt = parseScheduledAt(input.scheduledAt());
        LocalDateTime now = nowMillis();
        if (!scheduledAt.isAfter(now)) {
            throw new BusinessException(ApplicationCodes.INVALID_SCHEDULE, "面试时间需晚于当前时间");
        }
        String location = trimToNull(input.location());
        String onlineUrl = trimToNull(input.onlineUrl());
        String note = trimToNull(input.note());
        if (FORMAT_ONSITE.equals(format) && location == null) {
            throw paramInvalid("现场面试请填写面试地点");
        }
        if (location != null && location.length() > 256 || onlineUrl != null && onlineUrl.length() > 512
                || note != null && note.length() > 500) {
            throw paramInvalid("面试信息超出长度限制");
        }

        InterviewEntity interview = new InterviewEntity();
        interview.setId("int-new-" + interviewMapper.nextNewSeq());
        interview.setApplicationId(app.getId());
        interview.setCompanyId(app.getCompanyId());
        interview.setScheduledAt(scheduledAt);
        interview.setFormat(format);
        interview.setLocation(location);
        interview.setOnlineUrl(onlineUrl);
        interview.setNote(note);
        interview.setStatus(STATUS_PENDING);
        interview.setCreatedAt(now);
        interview.setUpdatedAt(now);
        interviewMapper.insert(interview);

        app.setStatus(STATUS_INTERVIEW);
        app.setUpdatedAt(now);
        applicationMapper.updateById(app);

        MessageEntity message = new MessageEntity();
        message.setId("msg-new-" + messageMapper.nextNewSeq());
        message.setConversationId(conversation.getId());
        message.setCompanyId(conversation.getCompanyId());
        message.setFromType("HR");
        message.setFromHrId(conversation.getHrId());
        message.setType("INTERVIEW_INVITE");
        message.setContent(inviteContent(interview));
        message.setCreatedAt(now);
        message.setReadByUser(false);
        message.setReadByHr(true);
        messageMapper.insert(message);

        conversation.setLastMessageAt(now);
        conversation.setUserUnreadCount(intOf(conversation.getUserUnreadCount()) + 1);
        conversationMapper.updateById(conversation);

        return ApplicationViews.Message.from(message);
    }

    // ------------------------------------------------------------------
    // §10.4 接受 / §10.5 拒绝
    // ------------------------------------------------------------------

    /** §10.4 接受面试（PENDING → ACCEPTED；已接受幂等）。 */
    @Transactional
    public ApplicationViews.Interview accept(String interviewId) {
        UserContext.CurrentUser user = Roles.requireSeeker();
        InterviewEntity interview = requireOwnInterview(interviewId, user.userId());
        if (STATUS_ACCEPTED.equals(interview.getStatus())) {
            return ApplicationViews.Interview.from(interview);
        }
        if (!STATUS_PENDING.equals(interview.getStatus())) {
            throw new BusinessException(ApplicationCodes.STATUS_CONFLICT, "当前状态不可接受面试邀请");
        }
        interview.setStatus(STATUS_ACCEPTED);
        interview.setUpdatedAt(nowMillis());
        interviewMapper.updateById(interview);
        return ApplicationViews.Interview.from(interview);
    }

    /** §10.5 拒绝面试（PENDING → DECLINED；已拒绝幂等）。 */
    @Transactional
    public ApplicationViews.Interview decline(String interviewId, ApplicationRequests.Decline input) {
        UserContext.CurrentUser user = Roles.requireSeeker();
        InterviewEntity interview = requireOwnInterview(interviewId, user.userId());
        if (STATUS_DECLINED.equals(interview.getStatus())) {
            return ApplicationViews.Interview.from(interview);
        }
        if (!STATUS_PENDING.equals(interview.getStatus())) {
            throw new BusinessException(ApplicationCodes.STATUS_CONFLICT, "当前状态不可拒绝面试邀请");
        }
        // reason 暂不落库（interviews 无原因列；后续可入审计日志）
        interview.setStatus(STATUS_DECLINED);
        interview.setUpdatedAt(nowMillis());
        interviewMapper.updateById(interview);
        return ApplicationViews.Interview.from(interview);
    }

    // ------------------------------------------------------------------
    // 内部：简历 / 会话 / 校验 / 工具
    // ------------------------------------------------------------------

    /** resumeId 缺省 → 默认简历 → 任一未删除简历；不存在 / 不属于本人 / content 为空 → 42210。 */
    private String resolveResumeId(String userId, String requestedResumeId) {
        String requested = trimToNull(requestedResumeId);
        ResumeEntity resume = null;
        if (requested != null) {
            resume = resumeMapper.selectById(requested);
            if (resume == null || !userId.equals(resume.getUserId()) || resume.getDeletedAt() != null) {
                throw resumeIncomplete("简历不存在或不属于当前用户");
            }
        } else {
            UserProfileEntity profile = userProfileMapper.selectOne(new LambdaQueryWrapper<UserProfileEntity>()
                    .eq(UserProfileEntity::getUserId, userId)
                    .last("LIMIT 1"));
            if (profile != null && trimToNull(profile.getDefaultResumeId()) != null) {
                resume = resumeMapper.selectById(profile.getDefaultResumeId());
                if (resume != null && (!userId.equals(resume.getUserId()) || resume.getDeletedAt() != null)) {
                    resume = null;
                }
            }
            if (resume == null) {
                List<ResumeEntity> resumes = resumeMapper.selectList(new LambdaQueryWrapper<ResumeEntity>()
                        .eq(ResumeEntity::getUserId, userId)
                        .isNull(ResumeEntity::getDeletedAt)
                        .orderByDesc(ResumeEntity::getIsDefault)
                        .orderByAsc(ResumeEntity::getCreatedAt)
                        .last("LIMIT 1"));
                resume = resumes.isEmpty() ? null : resumes.get(0);
            }
        }
        if (resume == null || resume.getContent() == null || resume.getContent().isBlank()
                || "{}".equals(resume.getContent().trim())) {
            throw resumeIncomplete("请先完善简历后再投递");
        }
        return resume.getId();
    }

    /** 投递即占位建会话（幂等；T6 的会话列表/消息直接复用该行）。 */
    private void ensureConversation(JobEntity job, String userId, LocalDateTime now) {
        ConversationEntity existing = conversationMapper.selectOne(new LambdaQueryWrapper<ConversationEntity>()
                .eq(ConversationEntity::getJobId, job.getId())
                .eq(ConversationEntity::getUserId, userId)
                .last("LIMIT 1"));
        if (existing != null) {
            return;
        }
        ConversationEntity conversation = new ConversationEntity();
        conversation.setId("conv-new-" + conversationMapper.nextNewSeq());
        conversation.setJobId(job.getId());
        conversation.setUserId(userId);
        conversation.setHrId(job.getHrId() == null ? "hr-system" : job.getHrId());
        conversation.setCompanyId(job.getCompanyId());
        conversation.setLastMessageAt(now);
        conversation.setUserUnreadCount(0);
        conversation.setHrUnreadCount(0);
        conversation.setClosedByHr(false);
        conversation.setCreatedAt(now);
        conversationMapper.insert(conversation);
    }

    private InterviewEntity requireOwnInterview(String interviewId, String userId) {
        InterviewEntity interview = interviewMapper.selectById(interviewId);
        if (interview == null) {
            throw new BusinessException(ApplicationCodes.APPLICATION_NOT_FOUND, "面试邀请不存在");
        }
        ApplicationEntity app = applicationMapper.selectById(interview.getApplicationId());
        if (app == null || !userId.equals(app.getUserId()) || app.getDeletedAt() != null) {
            throw new BusinessException(ApplicationCodes.APPLICATION_NOT_FOUND, "面试邀请不存在");
        }
        return interview;
    }

    private static LocalDateTime parseScheduledAt(String value) {
        String raw = trimToNull(value);
        if (raw == null) {
            throw paramInvalid("请选择面试时间");
        }
        try {
            return OffsetDateTime.parse(raw).toInstant().atZone(ZoneOffset.UTC).toLocalDateTime()
                    .truncatedTo(ChronoUnit.MILLIS);
        } catch (DateTimeParseException e) {
            throw paramInvalid("面试时间格式错误");
        }
    }

    private static String inviteContent(InterviewEntity interview) {
        ObjectNode payload = JSON.createObjectNode();
        payload.put("interviewId", interview.getId());
        payload.put("scheduledAt", interview.getScheduledAt().toInstant(ZoneOffset.UTC).toString());
        payload.put("format", interview.getFormat());
        if (interview.getLocation() != null) {
            payload.put("location", interview.getLocation());
        }
        if (interview.getOnlineUrl() != null) {
            payload.put("onlineUrl", interview.getOnlineUrl());
        }
        if (interview.getNote() != null) {
            payload.put("note", interview.getNote());
        }
        try {
            return JSON.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("面试邀请消息序列化失败", e);
        }
    }

    private static LocalDateTime nowMillis() {
        return LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS);
    }

    private static int intOf(Integer value) {
        return value == null ? 0 : value;
    }

    private static BusinessException applicationNotFound() {
        return new BusinessException(ApplicationCodes.APPLICATION_NOT_FOUND, "投递记录不存在");
    }

    private static BusinessException resumeIncomplete(String message) {
        return new BusinessException(ApplicationCodes.RESUME_INCOMPLETE, message);
    }

    private static BusinessException paramInvalid(String message) {
        return new BusinessException(ApiCodes.FIELD_VALIDATION, message);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
