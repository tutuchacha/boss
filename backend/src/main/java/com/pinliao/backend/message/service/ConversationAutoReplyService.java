package com.pinliao.backend.message.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pinliao.backend.application.entity.ConversationEntity;
import com.pinliao.backend.application.entity.MessageEntity;
import com.pinliao.backend.application.mapper.ConversationMapper;
import com.pinliao.backend.application.mapper.MessageMapper;
import com.pinliao.backend.auth.entity.CompanyMemberEntity;
import com.pinliao.backend.auth.mapper.CompanyMemberMapper;
import com.pinliao.backend.job.entity.JobEntity;
import com.pinliao.backend.job.mapper.JobMapper;
import com.pinliao.backend.message.realtime.MessageRealtime;
import com.pinliao.backend.message.view.MessageViews;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 聊天自动回复演示语义（step3-plan §3）。
 *
 * <p>对齐 mock：</p>
 * <ul>
 *   <li>求职者发 TEXT → 约 1.2s 后以 HR 身份自动回复（{@code mockHrReply} 关键词规则）；</li>
 *   <li>HR 发 TEXT → 约 1.2s 后以候选人身份自动回复（{@code mockCandidateReply} 规则）；</li>
 *   <li>回复前先推 typing=true，回复后 typing=false；新消息同时走 STOMP 用户队列。</li>
 * </ul>
 *
 * <p>调度线程为 daemon，延迟由 {@code pinliao.message.auto-reply-delay-ms} 配置
 * （默认 1200ms；单测可缩短）。</p>
 */
@Service
public class ConversationAutoReplyService {

    private static final Logger log = LoggerFactory.getLogger(ConversationAutoReplyService.class);
    private static final String FROM_USER = "USER";
    private static final String FROM_HR = "HR";

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final JobMapper jobMapper;
    private final CompanyMemberMapper companyMemberMapper;
    private final MessageRealtime realtime;
    private final long delayMs;
    private final ScheduledExecutorService scheduler;

    public ConversationAutoReplyService(ConversationMapper conversationMapper, MessageMapper messageMapper,
                                        JobMapper jobMapper, CompanyMemberMapper companyMemberMapper,
                                        MessageRealtime realtime,
                                        @Value("${pinliao.message.auto-reply-delay-ms:1200}") long delayMs) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.jobMapper = jobMapper;
        this.companyMemberMapper = companyMemberMapper;
        this.realtime = realtime;
        this.delayMs = Math.max(1, delayMs);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "message-auto-reply");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** 安排一次自动回复；仅调用方确认是 TEXT 后调用。 */
    public void schedule(String conversationId, String fromType, String triggerText) {
        long typingDelay = Math.max(1, Math.min(200, delayMs / 2));
        scheduler.schedule(() -> safely(() -> pushTyping(conversationId, fromType)),
                typingDelay, TimeUnit.MILLISECONDS);
        scheduler.schedule(() -> safely(() -> deliver(conversationId, fromType, triggerText)),
                delayMs, TimeUnit.MILLISECONDS);
    }

    private void safely(Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            log.warn("聊天自动回复失败：{}", e.getMessage());
        }
    }

    private void pushTyping(String conversationId, String triggerFromType) {
        ConversationEntity conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            return;
        }
        String replierType = FROM_USER.equals(triggerFromType) ? FROM_HR : FROM_USER;
        MessageViews.Typing event = new MessageViews.Typing(conversationId, replierType,
                FROM_USER.equals(replierType) ? conversation.getUserId() : null,
                FROM_HR.equals(replierType) ? conversation.getHrId() : null,
                true);
        realtime.pushTyping(typingTargets(conversation, triggerFromType), event);
    }

    /** 生成自动回复（包级可见，单测可直接调用，避免异步断言）。 */
    void deliver(String conversationId, String triggerFromType, String triggerText) {
        ConversationEntity conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            return;
        }
        boolean replyAsHr = FROM_USER.equals(triggerFromType);
        LocalDateTime now = nowMillis();
        MessageEntity reply = new MessageEntity();
        reply.setId("msg-new-" + messageMapper.nextNewSeq());
        reply.setConversationId(conversation.getId());
        reply.setCompanyId(conversation.getCompanyId());
        reply.setType("TEXT");
        reply.setCreatedAt(now);
        if (replyAsHr) {
            reply.setFromType(FROM_HR);
            reply.setFromHrId(conversation.getHrId());
            reply.setContent(hrReply(triggerText, jobMapper.selectById(conversation.getJobId())));
            reply.setReadByUser(false);
            reply.setReadByHr(true);
        } else {
            reply.setFromType(FROM_USER);
            reply.setFromUserId(conversation.getUserId());
            reply.setContent(candidateReply(triggerText));
            reply.setReadByUser(true);
            reply.setReadByHr(false);
        }
        messageMapper.insert(reply);

        conversation.setLastMessageAt(now);
        if (replyAsHr) {
            conversation.setUserUnreadCount(intOf(conversation.getUserUnreadCount()) + 1);
        } else {
            conversation.setHrUnreadCount(intOf(conversation.getHrUnreadCount()) + 1);
        }
        conversationMapper.updateById(conversation);

        MessageViews.Message view = MessageViews.Message.from(reply);
        if (replyAsHr) {
            realtime.pushMessage(conversation.getUserId(), view);
        } else {
            for (String userId : hrUserIds(conversation.getCompanyId())) {
                realtime.pushMessage(userId, view);
            }
        }
        MessageViews.Typing done = new MessageViews.Typing(conversationId,
                replyAsHr ? FROM_HR : FROM_USER,
                replyAsHr ? null : conversation.getUserId(),
                replyAsHr ? conversation.getHrId() : null,
                false);
        realtime.pushTyping(typingTargets(conversation, triggerFromType), done);
    }

    private Set<String> typingTargets(ConversationEntity conversation, String triggerFromType) {
        Set<String> targets = new LinkedHashSet<>();
        if (FROM_USER.equals(triggerFromType)) {
            targets.add(conversation.getUserId());
        } else {
            targets.addAll(hrUserIds(conversation.getCompanyId()));
        }
        return targets;
    }

    private List<String> hrUserIds(String companyId) {
        List<CompanyMemberEntity> members = companyMemberMapper.selectList(
                new LambdaQueryWrapper<CompanyMemberEntity>()
                        .eq(CompanyMemberEntity::getCompanyId, companyId));
        List<String> ids = new ArrayList<>();
        for (CompanyMemberEntity member : members) {
            if (member.getUserId() != null && !ids.contains(member.getUserId())) {
                ids.add(member.getUserId());
            }
        }
        return ids;
    }

    /** 对齐 mock {@code mockHrReply}：简历 / 薪资 / 面试关键词 + 默认到岗追问。 */
    static String hrReply(String userText, JobEntity job) {
        String text = userText == null ? "" : userText;
        if (text.contains("简历")) {
            return "收到您的简历了，我先和用人部门同步一下，有结果第一时间通知您。";
        }
        if (text.matches("(?s).*(薪|工资|待遇).*")) {
            if (job == null) {
                return "薪资范围会在沟通中详细说明，具体根据面试表现确定。";
            }
            int max = job.getSalaryMax() == null ? 0 : job.getSalaryMax();
            String extra = max >= 30 ? "另有年终奖和长期激励" : "含绩效奖金";
            return "这个岗位的薪资范围是 " + salaryText(job) + "，" + extra + "，具体会根据面试表现确定。";
        }
        if (text.matches("(?s).*(面试|约).*")) {
            return "可以的，您本周三或周四下午方便吗？确认后我给您发面试邀请。";
        }
        return "您好，请问您现在的工作状态是？大概什么时候可以到岗呢？";
    }

    /** 对齐 mock {@code mockCandidateReply}。 */
    static String candidateReply(String hrText) {
        String text = hrText == null ? "" : hrText;
        if (text.matches("(?s).*(面试|约|电话).*")) {
            return "可以的，我这周三或周四下午都方便，看您时间。";
        }
        if (text.matches("(?s).*(薪|工资|待遇|薪资).*")) {
            return "我目前的期望是 25-35K，具体可以再沟通。";
        }
        if (text.matches("(?s).*(简历|作品).*")) {
            return "好的，我这就把最新简历发您，稍等。";
        }
        if (text.matches("(?s).*(到岗|入职|什么时候).*")) {
            return "我现在离职状态，随时可以到岗。";
        }
        return "好的，谢谢您！我这边随时可以进一步沟通。";
    }

    private static String salaryText(JobEntity job) {
        int min = job.getSalaryMin() == null ? 0 : job.getSalaryMin();
        int max = job.getSalaryMax() == null ? 0 : job.getSalaryMax();
        return min + "-" + max + "K";
    }

    private static int intOf(Integer value) {
        return value == null ? 0 : value;
    }

    private static LocalDateTime nowMillis() {
        return LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS);
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }
}
