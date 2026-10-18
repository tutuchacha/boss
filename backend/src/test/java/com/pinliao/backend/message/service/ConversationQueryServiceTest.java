package com.pinliao.backend.message.service;

import com.pinliao.backend.application.entity.ConversationEntity;
import com.pinliao.backend.application.entity.MessageEntity;
import com.pinliao.backend.application.mapper.ConversationMapper;
import com.pinliao.backend.application.mapper.MessageMapper;
import com.pinliao.backend.application.mapper.ResumeMapper;
import com.pinliao.backend.auth.context.UserContext;
import com.pinliao.backend.auth.entity.CompanyEntity;
import com.pinliao.backend.auth.entity.HrProfileEntity;
import com.pinliao.backend.auth.entity.UserProfileEntity;
import com.pinliao.backend.auth.mapper.CompanyMapper;
import com.pinliao.backend.auth.mapper.HrProfileMapper;
import com.pinliao.backend.auth.mapper.UserProfileMapper;
import com.pinliao.backend.common.api.PageViews;
import com.pinliao.backend.common.exception.BusinessException;
import com.pinliao.backend.job.entity.JobEntity;
import com.pinliao.backend.job.mapper.JobMapper;
import com.pinliao.backend.message.view.MessageViews;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** T6：会话 / 消息读服务（§8.1 / §8.2）单元测试。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConversationQueryServiceTest {

    @Mock
    private ConversationMapper conversationMapper;
    @Mock
    private MessageMapper messageMapper;
    @Mock
    private JobMapper jobMapper;
    @Mock
    private CompanyMapper companyMapper;
    @Mock
    private HrProfileMapper hrProfileMapper;
    @Mock
    private UserProfileMapper userProfileMapper;
    @Mock
    private ResumeMapper resumeMapper;

    private ConversationQueryService service;

    @BeforeEach
    void setUp() {
        service = new ConversationQueryService(conversationMapper, messageMapper, jobMapper, companyMapper,
                hrProfileMapper, userProfileMapper, resumeMapper);
        seeker();
        when(jobMapper.selectById(any())).thenReturn(job());
        CompanyEntity company = new CompanyEntity();
        company.setId("co-acgiw7");
        company.setName("云杉科技");
        when(companyMapper.selectById(any())).thenReturn(company);
        HrProfileEntity hr = new HrProfileEntity();
        hr.setId("hr-x2270n");
        hr.setName("陈女士");
        hr.setTitle("技术招聘经理");
        when(hrProfileMapper.selectById(any())).thenReturn(hr);
        when(userProfileMapper.selectOne(any())).thenReturn(profile());
        when(resumeMapper.selectCount(any())).thenReturn(1L);
        when(messageMapper.selectOne(any())).thenReturn(message("msg-last", "conv-b", 1000,
                "HR", "好的"));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void 会话列表_求职者按lastMessageAt倒序并组装职位与候选人() {
        when(conversationMapper.selectList(any())).thenReturn(List.of(
                conversation("conv-a", "u-seeker-1", 1000),
                conversation("conv-b", "u-seeker-1", 2000)));

        PageViews.Page<MessageViews.ListItem> page = service.list(null, null);

        assertEquals(2, page.list().size());
        assertEquals("conv-b", page.list().get(0).conversation().id());
        assertEquals("conv-a", page.list().get(1).conversation().id());
        assertEquals(2, page.pagination().total());
        MessageViews.ListItem item = page.list().get(0);
        assertEquals("前端工程师", item.job().title());
        assertEquals("20-35K", item.job().salary());
        assertEquals("云杉科技", item.company().name());
        assertEquals("林晓", item.candidate().name());
        assertEquals(85, item.candidate().resumeCompleteness());
        assertEquals("msg-last", item.lastMessage().id());
    }

    @Test
    void 会话列表_HR视角返回本公司候选人与分页() {
        hr();
        when(conversationMapper.selectList(any())).thenReturn(List.of(
                conversation("conv-hr-1", "u-cand-1", 2000),
                conversation("conv-hr-4", "u-cand-2", 1000)));

        PageViews.Page<MessageViews.ListItem> page = service.list(null, 1);

        assertEquals(1, page.list().size());
        assertEquals("conv-hr-1", page.list().get(0).conversation().id());
        assertTrue(page.pagination().hasMore());
        assertEquals(2, page.pagination().total());
    }

    @Test
    void 消息列表_按createdAt倒序且支持游标翻页() {
        when(conversationMapper.selectById("conv-seed-003")).thenReturn(conversation("conv-seed-003", "u-seeker-1", 1));
        when(messageMapper.selectList(any())).thenReturn(List.of(
                message("m1", "conv-seed-003", 1000, "USER", "old"),
                message("m3", "conv-seed-003", 3000, "HR", "new"),
                message("m2", "conv-seed-003", 2000, "USER", "mid")));

        PageViews.Page<MessageViews.Message> first = service.messages("conv-seed-003", null, 2);
        assertEquals(List.of("m3", "m2"), first.list().stream().map(MessageViews.Message::id).toList());
        assertTrue(first.pagination().hasMore());

        PageViews.Page<MessageViews.Message> second = service.messages("conv-seed-003",
                first.pagination().nextCursor(), 2);
        assertEquals(List.of("m1"), second.list().stream().map(MessageViews.Message::id).toList());
        assertFalse(second.pagination().hasMore());
        assertNull(second.pagination().nextCursor());
    }

    @Test
    void 消息列表_非参与方40410() {
        when(conversationMapper.selectById("conv-other")).thenReturn(conversation("conv-other", "u-other", 1));

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.messages("conv-other", null, null));
        assertEquals(40410, e.getCode());
    }

    @Test
    void 会话详情_跨公司HR40410() {
        hr();
        ConversationEntity other = conversation("conv-x", "u-seeker-1", 1);
        other.setCompanyId("co-other");
        when(conversationMapper.selectById("conv-x")).thenReturn(other);

        BusinessException e = assertThrows(BusinessException.class, () -> service.detail("conv-x"));
        assertEquals(40410, e.getCode());
    }

    @Test
    void 游标解码_非法值40001() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> ConversationQueryService.decodeCursor("not-base64-json"));
        assertEquals(40001, e.getCode());
    }

    private void seeker() {
        UserContext.set(new UserContext.CurrentUser("u-seeker-1", "JOB_SEEKER", null, "jti",
                Instant.now().plusSeconds(3600)));
    }

    private void hr() {
        UserContext.set(new UserContext.CurrentUser("u-hr-1", "HR", "co-acgiw7", "jti",
                Instant.now().plusSeconds(3600)));
    }

    private static ConversationEntity conversation(String id, String userId, long millis) {
        ConversationEntity c = new ConversationEntity();
        c.setId(id);
        c.setJobId("job-1");
        c.setUserId(userId);
        c.setHrId("hr-x2270n");
        c.setCompanyId("co-acgiw7");
        c.setLastMessageAt(LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneOffset.UTC));
        c.setUserUnreadCount(1);
        c.setHrUnreadCount(0);
        c.setClosedByHr(false);
        c.setCreatedAt(c.getLastMessageAt());
        return c;
    }

    private static MessageEntity message(String id, String convId, long millis, String fromType, String content) {
        MessageEntity m = new MessageEntity();
        m.setId(id);
        m.setConversationId(convId);
        m.setFromType(fromType);
        m.setType("TEXT");
        m.setContent(content);
        m.setCreatedAt(LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneOffset.UTC));
        m.setReadByUser(true);
        m.setReadByHr(false);
        return m;
    }

    private static JobEntity job() {
        JobEntity job = new JobEntity();
        job.setId("job-1");
        job.setCompanyId("co-acgiw7");
        job.setHrId("hr-x2270n");
        job.setHrName("陈女士");
        job.setHrTitle("技术招聘经理");
        job.setTitle("前端工程师");
        job.setSalaryMin(20);
        job.setSalaryMax(35);
        job.setCity("上海");
        job.setArea("浦东新区");
        return job;
    }

    private static UserProfileEntity profile() {
        UserProfileEntity profile = new UserProfileEntity();
        profile.setId("up-seeker-1");
        profile.setUserId("u-seeker-1");
        profile.setName("林晓");
        profile.setExpectSub("前端开发");
        profile.setExpectCity("上海");
        profile.setWorkYears(4);
        profile.setEdu("本科");
        profile.setDefaultResumeId("resume-seeker");
        return profile;
    }
}
