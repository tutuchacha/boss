package com.pinliao.backend.application.service;

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
import com.pinliao.backend.auth.context.UserContext;
import com.pinliao.backend.auth.mapper.UserProfileMapper;
import com.pinliao.backend.common.exception.BusinessException;
import com.pinliao.backend.job.entity.JobEntity;
import com.pinliao.backend.job.mapper.JobMapper;
import com.pinliao.backend.job.service.JobQueryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T4：投递 / 面试写服务（§9.1 / §9.4 / §9.5 / §10.4~§10.5 / §8.7）单元测试。
 *
 * <p>语义基线 = mock {@code src/api/mock/applications.ts} + {@code hrApplications.ts}：
 * 40911 重复投递、40913 状态冲突、40914 不可约面、40915 已有待确认、42210 简历/时间前置条件。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ApplicationCommandServiceTest {

    @Mock
    private ApplicationMapper applicationMapper;
    @Mock
    private InterviewMapper interviewMapper;
    @Mock
    private ConversationMapper conversationMapper;
    @Mock
    private MessageMapper messageMapper;
    @Mock
    private ResumeMapper resumeMapper;
    @Mock
    private JobMapper jobMapper;
    @Mock
    private UserProfileMapper userProfileMapper;
    @Mock
    private JobQueryService jobQueryService;

    private ApplicationCommandService service;

    @BeforeEach
    void setUp() {
        service = new ApplicationCommandService(applicationMapper, interviewMapper, conversationMapper,
                messageMapper, resumeMapper, jobMapper, userProfileMapper, jobQueryService);
        seeker();
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    // ------------------------------------------------------------------
    // §9.1 投递
    // ------------------------------------------------------------------

    @Test
    void 投递成功_新建投递并占位建会话() {
        when(jobMapper.selectVisibleById("job-1")).thenReturn(job());
        when(resumeMapper.selectById("resume-seeker")).thenReturn(resume("resume-seeker"));
        when(applicationMapper.selectOne(any())).thenReturn(null);
        when(applicationMapper.nextNewSeq()).thenReturn(1L);
        when(conversationMapper.selectOne(any())).thenReturn(null);
        when(conversationMapper.nextNewSeq()).thenReturn(1L);

        var view = service.apply(new ApplicationRequests.Apply("job-1", "resume-seeker", "您好"));

        assertEquals("app-new-1", view.id());
        assertEquals("SUBMITTED", view.status());
        assertEquals("co-acgiw7", view.companyId());
        assertEquals("resume-seeker", view.resumeId());
        assertEquals("您好", view.coverLetter());
        assertEquals("u-seeker-1", view.userId());

        ArgumentCaptor<ApplicationEntity> appCaptor = ArgumentCaptor.forClass(ApplicationEntity.class);
        verify(applicationMapper).insert(appCaptor.capture());
        assertEquals("app-new-1", appCaptor.getValue().getId());

        ArgumentCaptor<ConversationEntity> convCaptor = ArgumentCaptor.forClass(ConversationEntity.class);
        verify(conversationMapper).insert(convCaptor.capture());
        ConversationEntity conversation = convCaptor.getValue();
        assertEquals("conv-new-1", conversation.getId());
        assertEquals("hr-x2270n", conversation.getHrId());
        assertEquals(0, conversation.getUserUnreadCount());
        assertEquals(0, conversation.getHrUnreadCount());
        assertFalse(conversation.getClosedByHr());
    }

    @Test
    void 投递_重复投递40911() {
        when(jobMapper.selectVisibleById("job-1")).thenReturn(job());
        when(resumeMapper.selectById("resume-seeker")).thenReturn(resume("resume-seeker"));
        when(applicationMapper.selectOne(any())).thenReturn(app("app-seed", "u-seeker-1", "job-1", "SUBMITTED"));

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.apply(new ApplicationRequests.Apply("job-1", "resume-seeker", null)));
        assertEquals(40911, e.getCode());
        verify(applicationMapper, never()).insert(any(ApplicationEntity.class));
    }

    @Test
    void 投递_撤回后重新激活复用原记录() {
        when(jobMapper.selectVisibleById("job-1")).thenReturn(job());
        when(resumeMapper.selectById("resume-seeker")).thenReturn(resume("resume-seeker"));
        ApplicationEntity existing = app("app-new-1", "u-seeker-1", "job-1", "WITHDRAWN");
        when(applicationMapper.selectOne(any())).thenReturn(existing);
        when(conversationMapper.selectOne(any())).thenReturn(new ConversationEntity());

        var view = service.apply(new ApplicationRequests.Apply("job-1", "resume-seeker", "重新投递"));

        assertEquals("app-new-1", view.id());
        assertEquals("SUBMITTED", view.status());
        assertEquals("重新投递", view.coverLetter());
        verify(applicationMapper).updateById(existing);
        verify(applicationMapper, never()).insert(any(ApplicationEntity.class));
        verify(conversationMapper, never()).insert(any(ConversationEntity.class));
    }

    @Test
    void 投递_无简历42210() {
        when(jobMapper.selectVisibleById("job-1")).thenReturn(job());
        when(userProfileMapper.selectOne(any())).thenReturn(null);
        when(resumeMapper.selectList(any())).thenReturn(List.of());

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.apply(new ApplicationRequests.Apply("job-1", "", null)));
        assertEquals(42210, e.getCode());
        verify(applicationMapper, never()).insert(any(ApplicationEntity.class));
    }

    @Test
    void 投递_职位不存在40410() {
        when(jobMapper.selectVisibleById("job-404")).thenReturn(null);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.apply(new ApplicationRequests.Apply("job-404", "resume-seeker", null)));
        assertEquals(40410, e.getCode());
    }

    @Test
    void 投递_HR角色40301() {
        hr();

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.apply(new ApplicationRequests.Apply("job-1", "resume-seeker", null)));
        assertEquals(40301, e.getCode());
    }

    // ------------------------------------------------------------------
    // §9.4 撤回 / §9.5 HR 状态
    // ------------------------------------------------------------------

    @Test
    void 撤回成功_面试中可撤回() {
        ApplicationEntity app = app("app-new-1", "u-seeker-1", "job-1", "INTERVIEW");
        when(applicationMapper.selectById("app-new-1")).thenReturn(app);

        var view = service.withdraw("app-new-1");

        assertEquals("WITHDRAWN", view.status());
        verify(applicationMapper).updateById(app);
    }

    @Test
    void 撤回_已撤回40913() {
        when(applicationMapper.selectById("app-new-1"))
                .thenReturn(app("app-new-1", "u-seeker-1", "job-1", "WITHDRAWN"));

        BusinessException e = assertThrows(BusinessException.class, () -> service.withdraw("app-new-1"));
        assertEquals(40913, e.getCode());
    }

    @Test
    void 撤回_他人投递40410() {
        when(applicationMapper.selectById("app-new-1"))
                .thenReturn(app("app-new-1", "u-other", "job-1", "SUBMITTED"));

        BusinessException e = assertThrows(BusinessException.class, () -> service.withdraw("app-new-1"));
        assertEquals(40410, e.getCode());
    }

    @Test
    void HR更新状态成功() {
        hr();
        ApplicationEntity app = app("app-new-1", "u-seeker-1", "job-1", "SUBMITTED");
        when(applicationMapper.selectById("app-new-1")).thenReturn(app);

        var view = service.updateStatus("app-new-1", new ApplicationRequests.StatusChange("VIEWED", null));

        assertEquals("VIEWED", view.status());
        verify(applicationMapper).updateById(app);
    }

    @Test
    void HR更新状态_非法值42201() {
        hr();
        when(applicationMapper.selectById("app-new-1"))
                .thenReturn(app("app-new-1", "u-seeker-1", "job-1", "SUBMITTED"));

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.updateStatus("app-new-1", new ApplicationRequests.StatusChange("FOO", null)));
        assertEquals(42201, e.getCode());
    }

    @Test
    void HR更新状态_已撤回40913() {
        hr();
        when(applicationMapper.selectById("app-new-1"))
                .thenReturn(app("app-new-1", "u-seeker-1", "job-1", "WITHDRAWN"));

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.updateStatus("app-new-1", new ApplicationRequests.StatusChange("VIEWED", null)));
        assertEquals(40913, e.getCode());
    }

    // ------------------------------------------------------------------
    // §8.7 约面试
    // ------------------------------------------------------------------

    @Test
    void 约面试成功_创建PENDING并发送邀请消息() {
        hr();
        ConversationEntity conversation = conversation();
        when(conversationMapper.selectById("conv-new-1")).thenReturn(conversation);
        ApplicationEntity app = app("app-new-1", "u-seeker-1", "job-1", "SUBMITTED");
        when(applicationMapper.selectOne(any())).thenReturn(app);
        when(interviewMapper.selectCount(any())).thenReturn(0L);
        when(interviewMapper.nextNewSeq()).thenReturn(1L);
        when(messageMapper.nextNewSeq()).thenReturn(1L);
        String when = OffsetDateTime.now(ZoneOffset.UTC).plusDays(2).withNano(0).toString();

        var message = service.invite("conv-new-1",
                new ApplicationRequests.Invite(when, "VIDEO", null, "https://meeting.pinliao.dev/r1", "一面"));

        assertEquals("msg-new-1", message.id());
        assertEquals("INTERVIEW_INVITE", message.type());
        assertEquals("HR", message.fromType());
        assertFalse(message.readByUser());
        assertTrue(message.readByHr());
        assertTrue(message.content().contains("int-new-1"));
        assertEquals("INTERVIEW", app.getStatus());
        assertEquals(1, conversation.getUserUnreadCount());

        ArgumentCaptor<InterviewEntity> interviewCaptor = ArgumentCaptor.forClass(InterviewEntity.class);
        verify(interviewMapper).insert(interviewCaptor.capture());
        assertEquals("PENDING", interviewCaptor.getValue().getStatus());
        assertEquals("VIDEO", interviewCaptor.getValue().getFormat());

        ArgumentCaptor<MessageEntity> messageCaptor = ArgumentCaptor.forClass(MessageEntity.class);
        verify(messageMapper).insert(messageCaptor.capture());
        assertEquals("conv-new-1", messageCaptor.getValue().getConversationId());
        assertEquals("hr-x2270n", messageCaptor.getValue().getFromHrId());
    }

    @Test
    void 约面试_已拒绝40914() {
        hr();
        when(conversationMapper.selectById("conv-new-1")).thenReturn(conversation());
        when(applicationMapper.selectOne(any())).thenReturn(app("app-new-1", "u-seeker-1", "job-1", "REJECTED"));

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.invite("conv-new-1", new ApplicationRequests.Invite(
                        OffsetDateTime.now(ZoneOffset.UTC).plusDays(1).toString(), "VIDEO", null, null, null)));
        assertEquals(40914, e.getCode());
    }

    @Test
    void 约面试_已有待确认40915() {
        hr();
        when(conversationMapper.selectById("conv-new-1")).thenReturn(conversation());
        when(applicationMapper.selectOne(any())).thenReturn(app("app-new-1", "u-seeker-1", "job-1", "INTERVIEW"));
        when(interviewMapper.selectCount(any())).thenReturn(1L);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.invite("conv-new-1", new ApplicationRequests.Invite(
                        OffsetDateTime.now(ZoneOffset.UTC).plusDays(1).toString(), "VIDEO", null, null, null)));
        assertEquals(40915, e.getCode());
    }

    @Test
    void 约面试_时间早于当前42210() {
        hr();
        when(conversationMapper.selectById("conv-new-1")).thenReturn(conversation());
        when(applicationMapper.selectOne(any())).thenReturn(app("app-new-1", "u-seeker-1", "job-1", "SUBMITTED"));
        when(interviewMapper.selectCount(any())).thenReturn(0L);

        String past = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1).toString();
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.invite("conv-new-1", new ApplicationRequests.Invite(past, "VIDEO", null, null, null)));
        assertEquals(42210, e.getCode());
    }

    @Test
    void 约面试_会话不存在40410() {
        hr();
        when(conversationMapper.selectById("conv-x")).thenReturn(null);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.invite("conv-x", new ApplicationRequests.Invite(
                        OffsetDateTime.now(ZoneOffset.UTC).plusDays(1).toString(), "VIDEO", null, null, null)));
        assertEquals(40410, e.getCode());
    }

    // ------------------------------------------------------------------
    // §10.4 接受 / §10.5 拒绝
    // ------------------------------------------------------------------

    @Test
    void 接受面试_PENDING转ACCEPTED() {
        when(interviewMapper.selectById("int-new-1")).thenReturn(interview("PENDING"));
        when(applicationMapper.selectById("app-new-1"))
                .thenReturn(app("app-new-1", "u-seeker-1", "job-1", "INTERVIEW"));

        var view = service.accept("int-new-1");

        assertEquals("ACCEPTED", view.status());
    }

    @Test
    void 接受面试_已接受幂等() {
        when(interviewMapper.selectById("int-new-1")).thenReturn(interview("ACCEPTED"));
        when(applicationMapper.selectById("app-new-1"))
                .thenReturn(app("app-new-1", "u-seeker-1", "job-1", "INTERVIEW"));

        assertEquals("ACCEPTED", service.accept("int-new-1").status());
        verify(interviewMapper, never()).updateById(any(InterviewEntity.class));
    }

    @Test
    void 接受面试_非待确认40913() {
        when(interviewMapper.selectById("int-new-1")).thenReturn(interview("DECLINED"));
        when(applicationMapper.selectById("app-new-1"))
                .thenReturn(app("app-new-1", "u-seeker-1", "job-1", "INTERVIEW"));

        BusinessException e = assertThrows(BusinessException.class, () -> service.accept("int-new-1"));
        assertEquals(40913, e.getCode());
    }

    @Test
    void 拒绝面试_PENDING转DECLINED() {
        when(interviewMapper.selectById("int-new-1")).thenReturn(interview("PENDING"));
        when(applicationMapper.selectById("app-new-1"))
                .thenReturn(app("app-new-1", "u-seeker-1", "job-1", "INTERVIEW"));

        var view = service.decline("int-new-1", new ApplicationRequests.Decline("时间冲突"));

        assertEquals("DECLINED", view.status());
    }

    @Test
    void 接受面试_他人邀请40410() {
        when(interviewMapper.selectById("int-new-1")).thenReturn(interview("PENDING"));
        when(applicationMapper.selectById("app-new-1"))
                .thenReturn(app("app-new-1", "u-other", "job-1", "INTERVIEW"));

        BusinessException e = assertThrows(BusinessException.class, () -> service.accept("int-new-1"));
        assertEquals(40410, e.getCode());
    }

    // ------------------------------------------------------------------
    // 测试夹具
    // ------------------------------------------------------------------

    private void seeker() {
        UserContext.set(new UserContext.CurrentUser("u-seeker-1", "JOB_SEEKER", null, "jti",
                Instant.now().plusSeconds(3600)));
    }

    private void hr() {
        UserContext.set(new UserContext.CurrentUser("u-hr-1", "HR", "co-acgiw7", "jti",
                Instant.now().plusSeconds(3600)));
    }

    private static JobEntity job() {
        JobEntity job = new JobEntity();
        job.setId("job-1");
        job.setCompanyId("co-acgiw7");
        job.setHrId("hr-x2270n");
        job.setHrName("陈女士");
        return job;
    }

    private static ResumeEntity resume(String id) {
        ResumeEntity resume = new ResumeEntity();
        resume.setId(id);
        resume.setUserId("u-seeker-1");
        resume.setName("我的简历");
        resume.setContent("{\"basics\":{\"name\":\"林晓\"}}");
        resume.setIsDefault(true);
        return resume;
    }

    private static ApplicationEntity app(String id, String userId, String jobId, String status) {
        ApplicationEntity app = new ApplicationEntity();
        app.setId(id);
        app.setUserId(userId);
        app.setJobId(jobId);
        app.setCompanyId("co-acgiw7");
        app.setResumeId("resume-seeker");
        app.setStatus(status);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).withNano(0);
        app.setCreatedAt(now);
        app.setUpdatedAt(now);
        return app;
    }

    private static ConversationEntity conversation() {
        ConversationEntity conversation = new ConversationEntity();
        conversation.setId("conv-new-1");
        conversation.setJobId("job-1");
        conversation.setUserId("u-seeker-1");
        conversation.setHrId("hr-x2270n");
        conversation.setCompanyId("co-acgiw7");
        conversation.setUserUnreadCount(0);
        conversation.setHrUnreadCount(0);
        conversation.setLastMessageAt(LocalDateTime.now(ZoneOffset.UTC));
        return conversation;
    }

    private static InterviewEntity interview(String status) {
        InterviewEntity interview = new InterviewEntity();
        interview.setId("int-new-1");
        interview.setApplicationId("app-new-1");
        interview.setCompanyId("co-acgiw7");
        interview.setStatus(status);
        interview.setScheduledAt(LocalDateTime.now(ZoneOffset.UTC).plusDays(1));
        interview.setFormat("VIDEO");
        interview.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        return interview;
    }
}
