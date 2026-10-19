package com.pinliao.backend.recruiter.service;

import com.pinliao.backend.application.entity.ApplicationEntity;
import com.pinliao.backend.application.entity.ConversationEntity;
import com.pinliao.backend.application.entity.InterviewEntity;
import com.pinliao.backend.application.mapper.ApplicationMapper;
import com.pinliao.backend.application.mapper.ConversationMapper;
import com.pinliao.backend.application.mapper.InterviewMapper;
import com.pinliao.backend.auth.context.UserContext;
import com.pinliao.backend.common.exception.BusinessException;
import com.pinliao.backend.job.entity.JobEntity;
import com.pinliao.backend.job.mapper.JobMapper;
import com.pinliao.backend.recruiter.view.RecruiterViews;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * T8：§4.4「我收到的投递」单元测试。
 *
 * <p>重点覆盖：卡片装配（候选人 / 职位摘要 / 会话 id / 面试优先级）、更新倒序、
 * 关键字过滤（候选人姓名 + 职位名称）与参数 / 角色校验。</p>
 *
 * <p>说明：status / jobId 过滤由 SQL 条件完成（单测中 Mapper 被 mock，不参与过滤），
 * 因此本测试只断言服务层的排序、装配与关键字过滤；真实过滤由冒烟脚本覆盖。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HrApplicationQueryServiceTest {

    @Mock
    private ApplicationMapper applicationMapper;
    @Mock
    private JobMapper jobMapper;
    @Mock
    private ConversationMapper conversationMapper;
    @Mock
    private InterviewMapper interviewMapper;
    @Mock
    private CandidateSummaryService candidateSummaryService;

    private HrApplicationQueryService service;

    @BeforeEach
    void setUp() {
        service = new HrApplicationQueryService(applicationMapper, jobMapper, conversationMapper, interviewMapper,
                candidateSummaryService);
        manager();
        when(candidateSummaryService.summarise(anyString())).thenAnswer(invocation ->
                candidate(invocation.getArgument(0), "候选人"));
        when(jobMapper.selectById(anyString())).thenReturn(job("job-1", "前端工程师"));
        when(conversationMapper.selectOne(any())).thenReturn(null);
        when(interviewMapper.selectList(any())).thenReturn(new ArrayList<>());
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void 列表_按更新时间倒序并装配候选人与面试() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).withNano(0);
        when(applicationMapper.selectList(any())).thenReturn(new ArrayList<>(List.of(
                app("app-1", "u-cand-1", "job-1", "SUBMITTED", now.minusHours(4)),
                app("app-2", "u-cand-2", "job-1", "OFFER", now.minusHours(9)))));

        ConversationEntity conversation = new ConversationEntity();
        conversation.setId("conv-1");
        when(conversationMapper.selectOne(any())).thenReturn(conversation);

        // app-1：存在更新的已完成面试，但待确认面试优先（对齐 mock）
        when(interviewMapper.selectList(any())).thenReturn(new ArrayList<>(List.of(
                interview("int-done", "app-1", "COMPLETED", now.minusHours(1)),
                interview("int-pending", "app-1", "PENDING", now.minusHours(5)))));

        List<RecruiterViews.ApplicationCard> cards = service.list(null, null, null, null);
        assertEquals(2, cards.size());
        assertEquals("app-1", cards.get(0).application().id(), "updatedAt DESC 排序");
        assertEquals("job-1", cards.get(0).job().id());
        assertEquals("20-30K", cards.get(0).job().salary());
        assertEquals("conv-1", cards.get(0).conversationId());
        assertEquals("int-pending", cards.get(0).interview().id(), "待确认面试优先");
        assertEquals("u-cand-2", cards.get(1).candidate().userId());
    }

    @Test
    void 列表_关键字按候选人姓名与职位名称过滤() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).withNano(0);
        when(applicationMapper.selectList(any())).thenReturn(new ArrayList<>(List.of(
                app("app-1", "u-cand-1", "job-1", "SUBMITTED", now.minusHours(1)),
                app("app-2", "u-cand-2", "job-1", "SUBMITTED", now.minusHours(2)))));
        when(candidateSummaryService.summarise("u-cand-1"))
                .thenReturn(candidate("u-cand-1", "林小满"));
        when(candidateSummaryService.summarise("u-cand-2"))
                .thenReturn(candidate("u-cand-2", "周叙白"));

        List<RecruiterViews.ApplicationCard> byName = service.list(null, null, "林小满", null);
        assertEquals(List.of("app-1"), byName.stream().map(c -> c.application().id()).toList());

        List<RecruiterViews.ApplicationCard> byTitle = service.list(null, null, "前端", null);
        assertEquals(2, byTitle.size());

        assertEquals(1, service.list(null, null, null, 1).size(), "limit 作为上限保护");
    }

    @Test
    void 列表_状态非法报42201_非招聘者报40301() {
        BusinessException invalid = assertThrows(BusinessException.class,
                () -> service.list("UNKNOWN", null, null, null));
        assertEquals(42201, invalid.getCode());

        seeker();
        BusinessException forbidden = assertThrows(BusinessException.class,
                () -> service.list(null, null, null, null));
        assertEquals(40301, forbidden.getCode());
    }

    @Test
    void 列表_无投递返回空且不报错() {
        when(applicationMapper.selectList(any())).thenReturn(new ArrayList<>());
        assertEquals(List.of(), service.list(null, null, null, null));
        assertNull(service.list("OFFER", null, null, null).stream().findFirst().orElse(null));
    }

    // ------------------------------------------------------------------

    private static ApplicationEntity app(String id, String userId, String jobId, String status,
                                         LocalDateTime updatedAt) {
        ApplicationEntity app = new ApplicationEntity();
        app.setId(id);
        app.setUserId(userId);
        app.setJobId(jobId);
        app.setCompanyId("co-1");
        app.setResumeId("resume-" + userId);
        app.setStatus(status);
        app.setCreatedAt(updatedAt.minusHours(1));
        app.setUpdatedAt(updatedAt);
        return app;
    }

    private static InterviewEntity interview(String id, String applicationId, String status,
                                             LocalDateTime createdAt) {
        InterviewEntity interview = new InterviewEntity();
        interview.setId(id);
        interview.setApplicationId(applicationId);
        interview.setCompanyId("co-1");
        interview.setStatus(status);
        interview.setFormat("VIDEO");
        interview.setScheduledAt(createdAt.plusDays(2));
        interview.setCreatedAt(createdAt);
        interview.setUpdatedAt(createdAt);
        return interview;
    }

    private static JobEntity job(String id, String title) {
        JobEntity job = new JobEntity();
        job.setId(id);
        job.setCompanyId("co-1");
        job.setTitle(title);
        job.setSalaryMin(20);
        job.setSalaryMax(30);
        job.setCity("上海");
        return job;
    }

    private static RecruiterViews.Candidate candidate(String userId, String name) {
        return new RecruiterViews.Candidate(userId, name, "前端开发", "上海", 3, "本科", 85);
    }

    private static void manager() {
        UserContext.set(new UserContext.CurrentUser("u-hr-1", "HR", "co-1", "jti",
                Instant.now().plusSeconds(3600)));
    }

    private static void seeker() {
        UserContext.set(new UserContext.CurrentUser("u-seeker-1", "JOB_SEEKER", null, "jti",
                Instant.now().plusSeconds(3600)));
    }
}
