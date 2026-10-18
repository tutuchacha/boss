package com.pinliao.backend.message.service;

import com.fasterxml.jackson.databind.node.TextNode;
import com.pinliao.backend.application.ApplicationCodes;
import com.pinliao.backend.application.entity.ConversationEntity;
import com.pinliao.backend.application.entity.MessageEntity;
import com.pinliao.backend.application.entity.ResumeEntity;
import com.pinliao.backend.application.mapper.ConversationMapper;
import com.pinliao.backend.application.mapper.MessageMapper;
import com.pinliao.backend.application.mapper.ResumeMapper;
import com.pinliao.backend.auth.context.UserContext;
import com.pinliao.backend.auth.entity.CompanyMemberEntity;
import com.pinliao.backend.auth.entity.HrProfileEntity;
import com.pinliao.backend.auth.entity.UserProfileEntity;
import com.pinliao.backend.auth.mapper.CompanyMemberMapper;
import com.pinliao.backend.auth.mapper.HrProfileMapper;
import com.pinliao.backend.auth.mapper.UserProfileMapper;
import com.pinliao.backend.common.exception.BusinessException;
import com.pinliao.backend.job.entity.JobEntity;
import com.pinliao.backend.job.mapper.JobMapper;
import com.pinliao.backend.message.dto.MessageRequests;
import com.pinliao.backend.message.realtime.MessageRealtime;
import com.pinliao.backend.message.view.MessageViews;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T6：会话 / 消息写服务（§8.3~§8.8）单元测试。
 *
 * <p>语义基线 = mock {@code src/api/mock/conversations.ts} / {@code hrConversations.ts}：
 * 40912 重复会话、40410 越权不可见、TEXT 才自动回复、已读只清自己的未读、
 * RESUME 快捷操作使用默认简历。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConversationCommandServiceTest {

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
    private HrProfileMapper hrProfileMapper;
    @Mock
    private CompanyMemberMapper companyMemberMapper;
    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOps;
    @Mock
    private MessageRealtime realtime;
    @Mock
    private ConversationAutoReplyService autoReplyService;

    private ConversationCommandService service;

    @BeforeEach
    void setUp() {
        service = new ConversationCommandService(conversationMapper, messageMapper, resumeMapper, jobMapper,
                userProfileMapper, hrProfileMapper, companyMemberMapper, redis, realtime, autoReplyService);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString())).thenReturn(1L);
        seeker();
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    // ------------------------------------------------------------------
    // §8.3 发起会话
    // ------------------------------------------------------------------

    @Test
    void 发起会话_成功_系统消息与首条消息() {
        when(jobMapper.selectVisibleById("job-1")).thenReturn(job("job-1"));
        when(conversationMapper.selectOne(any())).thenReturn(null);
        when(conversationMapper.nextNewSeq()).thenReturn(5L);
        when(messageMapper.nextNewSeq()).thenReturn(10L, 11L);
        when(companyMemberMapper.selectList(any())).thenReturn(List.of(member("u-hr-1")));

        MessageViews.Conversation view = service.start(new MessageRequests.Start(" job-1 ", " 您好 "));

        assertEquals("conv-new-5", view.id());
        assertEquals("u-seeker-1", view.userId());
        assertEquals(1, view.hrUnreadCount());
        ArgumentCaptor<ConversationEntity> convCaptor = ArgumentCaptor.forClass(ConversationEntity.class);
        verify(conversationMapper).insert(convCaptor.capture());
        assertEquals("conv-new-5", convCaptor.getValue().getId());

        ArgumentCaptor<MessageEntity> msgCaptor = ArgumentCaptor.forClass(MessageEntity.class);
        verify(messageMapper, org.mockito.Mockito.times(2)).insert(msgCaptor.capture());
        List<MessageEntity> inserted = msgCaptor.getAllValues();
        assertEquals("SYSTEM", inserted.get(0).getType());
        assertTrue(inserted.get(0).getContent().contains("陈女士"));
        assertEquals("TEXT", inserted.get(1).getType());
        assertEquals("您好", inserted.get(1).getContent());
        assertEquals("USER", inserted.get(1).getFromType());
        assertTrue(inserted.get(1).getReadByUser());
        assertFalse(inserted.get(1).getReadByHr());
        verify(realtime).pushMessage(eq("u-hr-1"), any());
        verify(autoReplyService).schedule("conv-new-5", "USER", "您好");
    }

    @Test
    void 发起会话_已存在40912() {
        when(jobMapper.selectVisibleById("job-1")).thenReturn(job("job-1"));
        when(conversationMapper.selectOne(any())).thenReturn(conversation("conv-seed-003", "co-acgiw7"));

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.start(new MessageRequests.Start("job-1", null)));
        assertEquals(40912, e.getCode());
        verify(conversationMapper, never()).insert(any(ConversationEntity.class));
    }

    @Test
    void 发起会话_职位不可见40410() {
        when(jobMapper.selectVisibleById("job-404")).thenReturn(null);
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.start(new MessageRequests.Start("job-404", null)));
        assertEquals(40410, e.getCode());
    }

    @Test
    void 发起会话_无首条消息只建会话() {
        when(jobMapper.selectVisibleById("job-1")).thenReturn(job("job-1"));
        when(conversationMapper.selectOne(any())).thenReturn(null);
        when(conversationMapper.nextNewSeq()).thenReturn(6L);

        service.start(new MessageRequests.Start("job-1", null));

        verify(messageMapper, never()).insert(any(MessageEntity.class));
        verify(autoReplyService, never()).schedule(anyString(), anyString(), anyString());
    }

    // ------------------------------------------------------------------
    // §8.4 发送消息
    // ------------------------------------------------------------------

    @Test
    void 求职者发消息_增加HR未读并推送() {
        ConversationEntity conversation = conversation("conv-seed-003", "co-acgiw7");
        conversation.setHrUnreadCount(2);
        when(conversationMapper.selectById("conv-seed-003")).thenReturn(conversation);
        when(messageMapper.nextNewSeq()).thenReturn(20L);
        when(companyMemberMapper.selectList(any())).thenReturn(List.of(member("u-hr-1")));

        MessageViews.Message message = service.send("conv-seed-003",
                new MessageRequests.Send("TEXT", new TextNode(" 请问在吗 "), null));

        assertEquals("msg-new-20", message.id());
        assertEquals("USER", message.fromType());
        assertEquals("请问在吗", message.content());
        assertTrue(message.readByUser());
        assertFalse(message.readByHr());
        assertEquals(3, conversation.getHrUnreadCount());
        assertEquals(0, conversation.getUserUnreadCount());
        verify(conversationMapper).updateById(conversation);
        verify(realtime).pushMessage(eq("u-hr-1"), any());
        verify(autoReplyService).schedule("conv-seed-003", "USER", "请问在吗");
    }

    @Test
    void HR发消息_增加求职者未读并推送() {
        hr();
        ConversationEntity conversation = conversation("conv-hr-1", "co-acgiw7");
        conversation.setUserId("u-cand-1");
        when(conversationMapper.selectById("conv-hr-1")).thenReturn(conversation);
        when(messageMapper.nextNewSeq()).thenReturn(21L);
        HrProfileEntity profile = new HrProfileEntity();
        profile.setId("hr-x2270n");
        profile.setUserId("u-hr-1");
        when(hrProfileMapper.selectOne(any())).thenReturn(profile);

        MessageViews.Message message = service.send("conv-hr-1",
                new MessageRequests.Send("TEXT", new TextNode("方便约面吗"), null));

        assertEquals("HR", message.fromType());
        assertEquals("hr-x2270n", message.fromHrId());
        assertFalse(message.readByUser());
        assertTrue(message.readByHr());
        assertEquals(1, conversation.getUserUnreadCount());
        verify(realtime).pushMessage(eq("u-cand-1"), any());
        verify(autoReplyService).schedule("conv-hr-1", "HR", "方便约面吗");
    }

    @Test
    void 发送_非本人会话40410() {
        ConversationEntity other = conversation("conv-other", "co-x");
        other.setUserId("u-other");
        when(conversationMapper.selectById("conv-other")).thenReturn(other);
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.send("conv-other", new MessageRequests.Send("TEXT", new TextNode("hi"), null)));
        assertEquals(40410, e.getCode());
        verify(messageMapper, never()).insert(any(MessageEntity.class));
    }

    @Test
    void 发送_类型或内容非法42201() {
        when(conversationMapper.selectById("conv-seed-003")).thenReturn(conversation("conv-seed-003", "co-acgiw7"));
        BusinessException type = assertThrows(BusinessException.class, () -> service.send("conv-seed-003",
                new MessageRequests.Send("SYSTEM", new TextNode("x"), null)));
        assertEquals(42201, type.getCode());

        BusinessException blank = assertThrows(BusinessException.class, () -> service.send("conv-seed-003",
                new MessageRequests.Send("TEXT", new TextNode("  "), null)));
        assertEquals(42201, blank.getCode());
        verify(messageMapper, never()).insert(any(MessageEntity.class));
    }

    @Test
    void 发送_idempotencyKey命中返回旧消息() {
        when(conversationMapper.selectById("conv-seed-003")).thenReturn(conversation("conv-seed-003", "co-acgiw7"));
        when(valueOps.get(anyString())).thenReturn("msg-old");
        MessageEntity old = new MessageEntity();
        old.setId("msg-old");
        old.setConversationId("conv-seed-003");
        old.setFromType("USER");
        old.setType("TEXT");
        old.setContent("旧消息");
        old.setCreatedAt(LocalDateTime.now());
        when(messageMapper.selectById("msg-old")).thenReturn(old);

        MessageViews.Message message = service.send("conv-seed-003",
                new MessageRequests.Send("TEXT", new TextNode("重复"), "idem-1"));

        assertEquals("msg-old", message.id());
        verify(messageMapper, never()).insert(any(MessageEntity.class));
        verify(valueOps, never()).increment(anyString());
    }

    @Test
    void 发送_限流42901() {
        when(conversationMapper.selectById("conv-seed-003")).thenReturn(conversation("conv-seed-003", "co-acgiw7"));
        when(valueOps.increment(anyString())).thenReturn(31L);

        BusinessException e = assertThrows(BusinessException.class, () -> service.send("conv-seed-003",
                new MessageRequests.Send("TEXT", new TextNode("hi"), null)));
        assertEquals(42901, e.getCode());
        verify(messageMapper, never()).insert(any(MessageEntity.class));
    }

    // ------------------------------------------------------------------
    // §8.5 / §8.6 / §8.8
    // ------------------------------------------------------------------

    @Test
    void 标记已读_只清本人计数并更新对应readFlag() {
        ConversationEntity conversation = conversation("conv-seed-003", "co-acgiw7");
        conversation.setUserUnreadCount(2);
        when(conversationMapper.selectById("conv-seed-003")).thenReturn(conversation);
        MessageEntity fromUser = new MessageEntity();
        fromUser.setId("m1");
        fromUser.setFromType("USER");
        fromUser.setReadByUser(true);
        fromUser.setReadByHr(false);
        MessageEntity fromHr = new MessageEntity();
        fromHr.setId("m2");
        fromHr.setFromType("HR");
        fromHr.setReadByUser(false);
        fromHr.setReadByHr(true);
        when(messageMapper.selectList(any())).thenReturn(List.of(fromUser, fromHr));

        MessageViews.ReadResult result = service.markRead("conv-seed-003");

        assertEquals(0, result.unreadCount());
        assertEquals(0, conversation.getUserUnreadCount());
        assertTrue(fromHr.getReadByUser());
        verify(messageMapper).updateById(fromHr);
        verify(messageMapper, never()).updateById(fromUser);
    }

    @Test
    void typing_推送到对方用户队列() {
        ConversationEntity conversation = conversation("conv-seed-003", "co-acgiw7");
        when(conversationMapper.selectById("conv-seed-003")).thenReturn(conversation);
        when(companyMemberMapper.selectList(any())).thenReturn(List.of(member("u-hr-1")));

        service.typing("conv-seed-003", new MessageRequests.Typing(true));

        ArgumentCaptor<MessageViews.Typing> captor = ArgumentCaptor.forClass(MessageViews.Typing.class);
        verify(realtime).pushTyping(eq(List.of("u-hr-1")), captor.capture());
        assertEquals("conv-seed-003", captor.getValue().conversationId());
        assertEquals("USER", captor.getValue().fromType());
        assertTrue(captor.getValue().isTyping());
    }

    @Test
    void 关闭会话_HR本公司成功_求职者40301() {
        hr();
        ConversationEntity conversation = conversation("conv-hr-1", "co-acgiw7");
        when(conversationMapper.selectById("conv-hr-1")).thenReturn(conversation);

        MessageViews.Conversation view = service.close("conv-hr-1");

        assertTrue(view.closedByHr());
        verify(conversationMapper).updateById(conversation);

        seeker();
        BusinessException e = assertThrows(BusinessException.class, () -> service.close("conv-hr-1"));
        assertEquals(40301, e.getCode());
    }

    // ------------------------------------------------------------------
    // §8.7 发送简历
    // ------------------------------------------------------------------

    @Test
    void 发送简历_默认简历生成RESUME消息() {
        ConversationEntity conversation = conversation("conv-seed-003", "co-acgiw7");
        when(conversationMapper.selectById("conv-seed-003")).thenReturn(conversation);
        when(messageMapper.nextNewSeq()).thenReturn(30L);
        UserProfileEntity profile = new UserProfileEntity();
        profile.setUserId("u-seeker-1");
        profile.setDefaultResumeId("resume-seeker");
        when(userProfileMapper.selectOne(any())).thenReturn(profile);
        ResumeEntity resume = new ResumeEntity();
        resume.setId("resume-seeker");
        resume.setUserId("u-seeker-1");
        resume.setName("默认简历");
        when(resumeMapper.selectById("resume-seeker")).thenReturn(resume);
        when(companyMemberMapper.selectList(any())).thenReturn(List.of(member("u-hr-1")));

        MessageViews.Message message = service.sendResume("conv-seed-003", new MessageRequests.SendResume(null));

        assertEquals("RESUME", message.type());
        assertTrue(message.content().contains("\"resumeId\":\"resume-seeker\""));
        assertEquals(1, conversation.getHrUnreadCount());
        verify(realtime).pushMessage(eq("u-hr-1"), any());
    }

    @Test
    void 发送简历_无简历42210() {
        when(conversationMapper.selectById("conv-seed-003")).thenReturn(conversation("conv-seed-003", "co-acgiw7"));
        when(userProfileMapper.selectOne(any())).thenReturn(null);
        when(resumeMapper.selectList(any())).thenReturn(List.of());

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.sendResume("conv-seed-003", new MessageRequests.SendResume(null)));
        assertEquals(ApplicationCodes.RESUME_INCOMPLETE, e.getCode());
    }

    // ------------------------------------------------------------------
    // fixtures
    // ------------------------------------------------------------------

    private void seeker() {
        UserContext.set(new UserContext.CurrentUser("u-seeker-1", "JOB_SEEKER", null, "jti",
                Instant.now().plusSeconds(3600)));
    }

    private void hr() {
        UserContext.set(new UserContext.CurrentUser("u-hr-1", "HR", "co-acgiw7", "jti",
                Instant.now().plusSeconds(3600)));
    }

    private static ConversationEntity conversation(String id, String companyId) {
        ConversationEntity c = new ConversationEntity();
        c.setId(id);
        c.setJobId("job-1");
        c.setUserId("u-seeker-1");
        c.setHrId("hr-x2270n");
        c.setCompanyId(companyId);
        c.setLastMessageAt(LocalDateTime.now());
        c.setUserUnreadCount(0);
        c.setHrUnreadCount(0);
        c.setClosedByHr(false);
        c.setCreatedAt(LocalDateTime.now());
        return c;
    }

    private static JobEntity job(String id) {
        JobEntity job = new JobEntity();
        job.setId(id);
        job.setCompanyId("co-acgiw7");
        job.setHrId("hr-x2270n");
        job.setHrName("陈女士");
        job.setHrTitle("技术招聘经理");
        job.setTitle("前端工程师");
        job.setSalaryMin(20);
        job.setSalaryMax(35);
        job.setCity("上海");
        job.setArea("浦东新区");
        job.setStatus("OPEN");
        return job;
    }

    private static CompanyMemberEntity member(String userId) {
        CompanyMemberEntity member = new CompanyMemberEntity();
        member.setUserId(userId);
        member.setCompanyId("co-acgiw7");
        member.setRole("ADMIN");
        return member;
    }
}
