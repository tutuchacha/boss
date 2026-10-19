package com.pinliao.backend.recruiter.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pinliao.backend.application.entity.ApplicationEntity;
import com.pinliao.backend.application.entity.ConversationEntity;
import com.pinliao.backend.application.entity.InterviewEntity;
import com.pinliao.backend.application.mapper.ApplicationMapper;
import com.pinliao.backend.application.mapper.ConversationMapper;
import com.pinliao.backend.application.mapper.InterviewMapper;
import com.pinliao.backend.application.view.ApplicationViews;
import com.pinliao.backend.auth.context.Roles;
import com.pinliao.backend.auth.context.UserContext;
import com.pinliao.backend.common.api.ApiCodes;
import com.pinliao.backend.common.exception.BusinessException;
import com.pinliao.backend.job.entity.JobEntity;
import com.pinliao.backend.job.mapper.JobMapper;
import com.pinliao.backend.recruiter.view.RecruiterViews;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * §4.4 我收到的投递（HR / COMPANY_ADMIN，公司维度）。
 *
 * <p><b>出参口径</b>：契约写「分页 {@code ApplicationWithJob[]}」，但前端 service
 * （{@code src/api/hrApplications.ts} / mock {@code companyApplicationViews}）按
 * {@code HrApplicationView} 数组使用（投递 + 候选人画像 + 职位摘要 + 会话 id + 最近面试），
 * 因此这里直接返回该超集（T8 决策：避免 T9 二次适配）。演示规模全量返回数组，
 * {@code limit} 作为上限保护；{@code cursor} 保留但未启用游标分页。</p>
 *
 * <p>归属：company_id 取自登录上下文（租户注入同时生效）；排序对齐 mock
 * {@code updatedAt DESC}（追加 id ASC 保证稳定）。</p>
 */
@Service
public class HrApplicationQueryService {

    /** 投递状态枚举（与 {@code ApplicationQueryService.APPLICATION_STATUSES} / 契约 §9 一致）。 */
    static final Set<String> APPLICATION_STATUSES = Set.of(
            "SUBMITTED", "VIEWED", "INTERVIEW", "OFFER", "REJECTED", "WITHDRAWN");

    private static final int MAX_LIMIT = 100;
    private static final String INTERVIEW_PENDING = "PENDING";

    private final ApplicationMapper applicationMapper;
    private final JobMapper jobMapper;
    private final ConversationMapper conversationMapper;
    private final InterviewMapper interviewMapper;
    private final CandidateSummaryService candidateSummaryService;

    public HrApplicationQueryService(ApplicationMapper applicationMapper,
                                     JobMapper jobMapper,
                                     ConversationMapper conversationMapper,
                                     InterviewMapper interviewMapper,
                                     CandidateSummaryService candidateSummaryService) {
        this.applicationMapper = applicationMapper;
        this.jobMapper = jobMapper;
        this.conversationMapper = conversationMapper;
        this.interviewMapper = interviewMapper;
        this.candidateSummaryService = candidateSummaryService;
    }

    /**
     * §4.4 我（所在公司）收到的投递。
     *
     * @param status 可选：投递状态，缺省或 ALL 为全部（非法值 42201）
     * @param jobId  可选：只看某个职位的投递
     * @param q      可选扩展参数：按「候选人姓名 + 职位名称」模糊匹配（大小写不敏感，对齐 mock）
     * @param limit  可选上限保护（1~100，缺省不限）
     */
    public List<RecruiterViews.ApplicationCard> list(String status, String jobId, String q, Integer limit) {
        UserContext.CurrentUser user = Roles.requireManager();
        String normalizedStatus = normalizeStatus(status);
        String jobFilter = trimToNull(jobId);
        String keyword = lowerOrNull(q);
        Integer cap = limit == null ? null : Math.max(1, Math.min(limit, MAX_LIMIT));

        List<ApplicationEntity> rows = applicationMapper.selectList(new LambdaQueryWrapper<ApplicationEntity>()
                .eq(ApplicationEntity::getCompanyId, user.companyId())
                .isNull(ApplicationEntity::getDeletedAt)
                .eq(normalizedStatus != null, ApplicationEntity::getStatus, normalizedStatus)
                .eq(jobFilter != null, ApplicationEntity::getJobId, jobFilter));
        rows.sort(Comparator.comparing(ApplicationEntity::getUpdatedAt,
                        Comparator.nullsFirst(Comparator.naturalOrder()))
                .reversed()
                .thenComparing(ApplicationEntity::getId));

        List<RecruiterViews.ApplicationCard> cards = new ArrayList<>();
        for (ApplicationEntity app : rows) {
            RecruiterViews.ApplicationCard card = toCard(app, user.companyId());
            if (keyword != null && !matchesKeyword(card, keyword)) {
                continue;
            }
            cards.add(card);
            if (cap != null && cards.size() >= cap) {
                break;
            }
        }
        return cards;
    }

    private RecruiterViews.ApplicationCard toCard(ApplicationEntity app, String companyId) {
        RecruiterViews.Candidate candidate = candidateSummaryService.summarise(app.getUserId());
        return new RecruiterViews.ApplicationCard(
                ApplicationViews.Application.from(app),
                candidate,
                jobBrief(app.getJobId()),
                conversationId(app, companyId),
                latestInterview(app.getId()));
    }

    /** 职位摘要：职位行缺失时按 mock 口径给「职位已删除」占位。 */
    private RecruiterViews.JobBrief jobBrief(String jobId) {
        JobEntity job = jobMapper.selectById(jobId);
        if (job == null) {
            return new RecruiterViews.JobBrief(jobId, "职位已删除", "", "");
        }
        int min = job.getSalaryMin() == null ? 0 : job.getSalaryMin();
        int max = job.getSalaryMax() == null ? 0 : job.getSalaryMax();
        return new RecruiterViews.JobBrief(job.getId(), job.getTitle(), min + "-" + max + "K", job.getCity());
    }

    /** 「候选人 × 职位」会话 id（尚未创建则为 null，「发消息」时由 T6 按需创建）。 */
    private String conversationId(ApplicationEntity app, String companyId) {
        ConversationEntity conversation = conversationMapper.selectOne(new LambdaQueryWrapper<ConversationEntity>()
                .eq(ConversationEntity::getJobId, app.getJobId())
                .eq(ConversationEntity::getUserId, app.getUserId())
                .eq(ConversationEntity::getCompanyId, companyId)
                .last("LIMIT 1"));
        return conversation == null ? null : conversation.getId();
    }

    /** 关联面试：待确认优先，否则最近一条（对齐 mock {@code pendingInterviewOfApplication ?? [0]}）。 */
    private ApplicationViews.Interview latestInterview(String applicationId) {
        List<InterviewEntity> interviews = interviewMapper.selectList(new LambdaQueryWrapper<InterviewEntity>()
                .eq(InterviewEntity::getApplicationId, applicationId));
        if (interviews.isEmpty()) {
            return null;
        }
        interviews.sort(Comparator.comparing(InterviewEntity::getCreatedAt,
                        Comparator.nullsFirst(Comparator.naturalOrder()))
                .reversed()
                .thenComparing(Comparator.comparing(InterviewEntity::getId).reversed()));
        for (InterviewEntity interview : interviews) {
            if (INTERVIEW_PENDING.equals(interview.getStatus())) {
                return ApplicationViews.Interview.from(interview);
            }
        }
        return ApplicationViews.Interview.from(interviews.get(0));
    }

    private static boolean matchesKeyword(RecruiterViews.ApplicationCard card, String keyword) {
        String name = card.candidate().name() == null ? "" : card.candidate().name();
        String title = card.job().title() == null ? "" : card.job().title();
        return (name + " " + title).toLowerCase(Locale.ROOT).contains(keyword);
    }

    private static String normalizeStatus(String status) {
        String normalized = trimToNull(status);
        if (normalized == null || "ALL".equalsIgnoreCase(normalized)) {
            return null;
        }
        String upper = normalized.toUpperCase(Locale.ROOT);
        if (!APPLICATION_STATUSES.contains(upper)) {
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
