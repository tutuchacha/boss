package com.pinliao.backend.message.service;

import com.pinliao.backend.application.entity.ConversationEntity;
import com.pinliao.backend.application.entity.MessageEntity;
import com.pinliao.backend.application.mapper.ConversationMapper;
import com.pinliao.backend.application.mapper.MessageMapper;
import com.pinliao.backend.auth.entity.CompanyMemberEntity;
import com.pinliao.backend.auth.mapper.CompanyMemberMapper;
import com.pinliao.backend.job.entity.JobEntity;
import com.pinliao.backend.job.mapper.JobMapper;
import com.pinliao.backend.message.realtime.MessageRealtime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** T6：聊天自动回复演示语义（对齐 mock mockHrReply / mockCandidateReply）。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConversationAutoReplyServiceTest {

    @Mock
    private ConversationMapper conversationMapper;
    @Mock
    private MessageMapper messageMapper;
    @Mock
    private JobMapper jobMapper;
    @Mock
    private CompanyMemberMapper companyMemberMapper;
    @Mock
    private MessageRealtime realtime;

    private ConversationAutoReplyService service;

    @BeforeEach
    void setUp() {
        service = new ConversationAutoReplyService(conversationMapper, messageMapper, jobMapper,
                companyMemberMapper, realtime, 20);
        when(conversationMapper.selectById("conv-seed-003")).thenReturn(conversation());
        when(messageMapper.nextNewSeq()).thenReturn(7L);
        when(jobMapper.selectById("job-1")).thenReturn(job());
        CompanyMemberEntity member = new CompanyMemberEntity();
        member.setUserId("u-hr-1");
        member.setCompanyId("co-acgiw7");
        when(companyMemberMapper.selectList(any())).thenReturn(List.of(member));
    }

    @AfterEach
    void tearDown() {
        service.shutdown();
    }

    @Test
    void 求职者发薪资_HR自动回复含岗位薪资并增加求职者未读() {
        service.deliver("conv-seed-003", "USER", "请问薪资待遇如何？");

        ArgumentCaptor<MessageEntity> captor = ArgumentCaptor.forClass(MessageEntity.class);
        verify(messageMapper).insert(captor.capture());
        MessageEntity reply = captor.getValue();
        assertEquals("msg-new-7", reply.getId());
        assertEquals("HR", reply.getFromType());
        assertEquals("hr-x2270n", reply.getFromHrId());
        assertEquals("TEXT", reply.getType());
        assertTrue(reply.getContent().contains("20-35K"));
        assertTrue(reply.getContent().contains("另有年终奖和长期激励"));
        assertFalse(reply.getReadByUser());
        assertTrue(reply.getReadByHr());
        verify(realtime).pushMessage(eq("u-seeker-1"), any());
    }

    @Test
    void HR发面试_候选人自动回复并增加HR未读() {
        service.deliver("conv-seed-003", "HR", "方便约个面试吗？");

        ArgumentCaptor<MessageEntity> captor = ArgumentCaptor.forClass(MessageEntity.class);
        verify(messageMapper).insert(captor.capture());
        MessageEntity reply = captor.getValue();
        assertEquals("USER", reply.getFromType());
        assertEquals("u-seeker-1", reply.getFromUserId());
        assertTrue(reply.getContent().contains("周三或周四"));
        assertTrue(reply.getReadByUser());
        assertFalse(reply.getReadByHr());
        verify(realtime).pushMessage(eq("u-hr-1"), any());
    }

    @Test
    void 自动回复文案_关键词与默认回退() {
        assertEquals("收到您的简历了，我先和用人部门同步一下，有结果第一时间通知您。",
                ConversationAutoReplyService.hrReply("我发简历给你", job()));
        assertTrue(ConversationAutoReplyService.hrReply("约面试", job()).contains("周三或周四"));
        assertEquals("您好，请问您现在的工作状态是？大概什么时候可以到岗呢？",
                ConversationAutoReplyService.hrReply("你好", job()));

        assertTrue(ConversationAutoReplyService.candidateReply("薪资多少").contains("25-35K"));
        assertTrue(ConversationAutoReplyService.candidateReply("发我简历").contains("最新简历"));
        assertTrue(ConversationAutoReplyService.candidateReply("什么时候到岗").contains("随时"));
        assertEquals("好的，谢谢您！我这边随时可以进一步沟通。",
                ConversationAutoReplyService.candidateReply("你好"));
    }

    private static ConversationEntity conversation() {
        ConversationEntity c = new ConversationEntity();
        c.setId("conv-seed-003");
        c.setJobId("job-1");
        c.setUserId("u-seeker-1");
        c.setHrId("hr-x2270n");
        c.setCompanyId("co-acgiw7");
        c.setLastMessageAt(LocalDateTime.now());
        c.setUserUnreadCount(0);
        c.setHrUnreadCount(0);
        c.setClosedByHr(false);
        c.setCreatedAt(LocalDateTime.now());
        return c;
    }

    private static JobEntity job() {
        JobEntity job = new JobEntity();
        job.setId("job-1");
        job.setSalaryMin(20);
        job.setSalaryMax(35);
        return job;
    }
}
