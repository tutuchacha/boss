package com.pinliao.backend.application.service;

import com.pinliao.backend.application.entity.ApplicationEntity;
import com.pinliao.backend.application.entity.InterviewEntity;
import com.pinliao.backend.application.mapper.ApplicationMapper;
import com.pinliao.backend.application.mapper.ConversationMapper;
import com.pinliao.backend.application.mapper.InterviewMapper;
import com.pinliao.backend.application.mapper.ResumeMapper;
import com.pinliao.backend.auth.context.UserContext;
import com.pinliao.backend.auth.entity.UserEntity;
import com.pinliao.backend.auth.entity.UserProfileEntity;
import com.pinliao.backend.auth.mapper.UserMapper;
import com.pinliao.backend.auth.mapper.UserProfileMapper;
import com.pinliao.backend.common.exception.BusinessException;
import com.pinliao.backend.job.mapper.JobMapper;
import com.pinliao.backend.job.service.JobQueryService;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * T4：投递 / 面试读服务（§9.2~§9.3、§10.1~§10.3）单元测试。
 *
 * <p>重点覆盖游标分页（不重不漏）、归属隔离（40410）与 HR 视角候选人拼装。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ApplicationQueryServiceTest {

    @Mock
    private ApplicationMapper applicationMapper;
    @Mock
    private InterviewMapper interviewMapper;
    @Mock
    private ConversationMapper conversationMapper;
    @Mock
    private ResumeMapper resumeMapper;
    @Mock
    private JobMapper jobMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private UserProfileMapper userProfileMapper;
    @Mock
    private JobQueryService jobQueryService;

    private ApplicationQueryService service;

    @BeforeEach
    void setUp() {
        service = new ApplicationQueryService(applicationMapper, interviewMapper, conversationMapper, resumeMapper,
                jobMapper, userMapper, userProfileMapper, jobQueryService);
        UserContext.set(new UserContext.CurrentUser("u-seeker-1", "JOB_SEEKER", null, "jti",
                Instant.now().plusSeconds(3600)));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void 我的投递_游标翻页不重不漏() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).withNano(0);
        when(applicationMapper.selectList(any())).thenReturn(new java.util.ArrayList<>(List.of(
                app("app-1", now.minusHours(1)),
                app("app-2", now.minusHours(2)),
                app("app-3", now.minusHours(3)))));
        when(jobMapper.selectById(anyString())).thenReturn(null);

        var first = service.listApplications(null, null, 2);
        assertEquals(2, first.list().size());
        assertEquals(3, first.pagination().total());
        assertTrue(first.pagination().hasMore());
        assertNotEquals(null, first.pagination().nextCursor());

        var second = service.listApplications(null, first.pagination().nextCursor(), 2);
        assertEquals(1, second.list().size());
        assertEquals(3, second.pagination().total());
        assertEquals(false, second.pagination().hasMore());

        List<String> ids = java.util.stream.Stream.concat(first.list().stream(), second.list().stream())
                .map(v -> v.id()).toList();
        assertEquals(List.of("app-1", "app-2", "app-3"), ids);
    }

    @Test
    void 投递详情_他人投递40410() {
        when(applicationMapper.selectById("app-1")).thenReturn(app("app-1", "u-other"));

        BusinessException e = assertThrows(BusinessException.class, () -> service.detail("app-1"));
        assertEquals(40410, e.getCode());
    }

    @Test
    void HR面试列表_含候选人姓名与手机号() {
        UserContext.set(new UserContext.CurrentUser("u-hr-1", "HR", "co-acgiw7", "jti",
                Instant.now().plusSeconds(3600)));
        InterviewEntity interview = new InterviewEntity();
        interview.setId("int-1");
        interview.setApplicationId("app-1");
        interview.setCompanyId("co-acgiw7");
        interview.setStatus("PENDING");
        interview.setFormat("VIDEO");
        interview.setScheduledAt(LocalDateTime.now(ZoneOffset.UTC).plusDays(1));
        interview.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        when(interviewMapper.selectList(any())).thenReturn(new java.util.ArrayList<>(List.of(interview)));

        ApplicationEntity app = app("app-1", "u-cand-1");
        when(applicationMapper.selectById("app-1")).thenReturn(app);
        UserEntity user = new UserEntity();
        user.setId("u-cand-1");
        user.setPhone("13800001101");
        when(userMapper.selectById("u-cand-1")).thenReturn(user);
        UserProfileEntity profile = new UserProfileEntity();
        profile.setUserId("u-cand-1");
        profile.setName("林小满");
        when(userProfileMapper.selectOne(any())).thenReturn(profile);

        var page = service.listHrInterviews(null, null, 20);

        assertEquals(1, page.pagination().total());
        assertEquals("林小满", page.list().get(0).profile().name());
        assertEquals("13800001101", page.list().get(0).user().phone());
    }

    @Test
    void 面试详情_跨公司40410() {
        UserContext.set(new UserContext.CurrentUser("u-hr-1", "HR", "co-acgiw7", "jti",
                Instant.now().plusSeconds(3600)));
        InterviewEntity interview = new InterviewEntity();
        interview.setId("int-1");
        interview.setApplicationId("app-1");
        when(interviewMapper.selectById("int-1")).thenReturn(interview);
        ApplicationEntity app = app("app-1", "u-cand-1");
        app.setCompanyId("co-other");
        when(applicationMapper.selectById("app-1")).thenReturn(app);

        BusinessException e = assertThrows(BusinessException.class, () -> service.interviewDetail("int-1"));
        assertEquals(40410, e.getCode());
    }

    @Test
    void 游标非法_40001() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> ApplicationQueryService.decodeCursor("%%%"));
        assertEquals(40001, e.getCode());
    }

    private static ApplicationEntity app(String id, String userId) {
        ApplicationEntity app = app(id, LocalDateTime.now(ZoneOffset.UTC));
        app.setUserId(userId);
        return app;
    }

    private static ApplicationEntity app(String id, LocalDateTime updatedAt) {
        ApplicationEntity app = new ApplicationEntity();
        app.setId(id);
        app.setUserId("u-seeker-1");
        app.setJobId("job-1");
        app.setCompanyId("co-acgiw7");
        app.setResumeId("resume-seeker");
        app.setStatus("SUBMITTED");
        app.setCreatedAt(updatedAt.minusHours(1));
        app.setUpdatedAt(updatedAt);
        return app;
    }
}
