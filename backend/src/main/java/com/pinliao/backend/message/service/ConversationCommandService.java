package com.pinliao.backend.message.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pinliao.backend.application.ApplicationCodes;
import com.pinliao.backend.application.entity.ConversationEntity;
import com.pinliao.backend.application.entity.MessageEntity;
import com.pinliao.backend.application.entity.ResumeEntity;
import com.pinliao.backend.application.mapper.ConversationMapper;
import com.pinliao.backend.application.mapper.MessageMapper;
import com.pinliao.backend.application.mapper.ResumeMapper;
import com.pinliao.backend.auth.context.Roles;
import com.pinliao.backend.auth.context.UserContext;
import com.pinliao.backend.auth.entity.CompanyMemberEntity;
import com.pinliao.backend.auth.entity.HrProfileEntity;
import com.pinliao.backend.auth.entity.UserProfileEntity;
import com.pinliao.backend.auth.mapper.CompanyMemberMapper;
import com.pinliao.backend.auth.mapper.HrProfileMapper;
import com.pinliao.backend.auth.mapper.UserProfileMapper;
import com.pinliao.backend.common.api.ApiCodes;
import com.pinliao.backend.common.exception.BusinessException;
import com.pinliao.backend.job.JobCodes;
import com.pinliao.backend.job.entity.JobEntity;
import com.pinliao.backend.job.mapper.JobMapper;
import com.pinliao.backend.message.MessageCodes;
import com.pinliao.backend.message.dto.MessageRequests;
import com.pinliao.backend.message.realtime.MessageRealtime;
import com.pinliao.backend.message.view.MessageViews;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 会话 / 消息写服务（契约 §8.3~§8.8）。
 *
 * <p><b>权限</b>：求职者只能操作自己的会话；HR / COMPANY_ADMIN 只能操作本公司会话；
 * 不存在 / 非参与方 / 跨公司统一 40410，不泄露存在性。</p>
 *
 * <p><b>未读</b>：conversations 的 user_unread_count / hr_unread_count 为持久化事实源；
 * 发送方消息只增加对方计数，标记已读只清空自己的计数并同步 message.read_by_*。
 * Redis 仅用于发送限流（30 次/分钟/用户）与 idempotencyKey 24h 去重。</p>
 *
 * <p><b>演示语义</b>：TEXT 消息落库后由 {@link ConversationAutoReplyService} 延迟生成
 * 对方自动回复（1.2s，可配置），并通过 STOMP 推送；详见 step3-plan §3。</p>
 */
@Service
public class ConversationCommandService {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> CLIENT_TYPES = Set.of("TEXT", "RESUME", "INTERVIEW_INVITE", "JOB_CARD");
    private static final int TEXT_MAX_LENGTH = 2000;
    private static final int STRUCTURED_MAX_LENGTH = 10000;
    private static final int SEND_LIMIT_PER_MINUTE = 30;
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    private static final String SEND_RATE_KEY = "pinliao:message:send-rate:%s:%d";
    private static final String IDEM_KEY = "pinliao:message:idem:%s:%s:%s";

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final ResumeMapper resumeMapper;
    private final JobMapper jobMapper;
    private final UserProfileMapper userProfileMapper;
    private final HrProfileMapper hrProfileMapper;
    private final CompanyMemberMapper companyMemberMapper;
    private final StringRedisTemplate redis;
    private final MessageRealtime realtime;
    private final ConversationAutoReplyService autoReplyService;

    public ConversationCommandService(ConversationMapper conversationMapper, MessageMapper messageMapper,
                                      ResumeMapper resumeMapper, JobMapper jobMapper,
                                      UserProfileMapper userProfileMapper, HrProfileMapper hrProfileMapper,
                                      CompanyMemberMapper companyMemberMapper, StringRedisTemplate redis,
                                      MessageRealtime realtime, ConversationAutoReplyService autoReplyService) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.resumeMapper = resumeMapper;
        this.jobMapper = jobMapper;
        this.userProfileMapper = userProfileMapper;
        this.hrProfileMapper = hrProfileMapper;
        this.companyMemberMapper = companyMemberMapper;
        this.redis = redis;
        this.realtime = realtime;
        this.autoReplyService = autoReplyService;
    }

    // ------------------------------------------------------------------
    // §8.3 发起会话
    // ------------------------------------------------------------------

    /** §8.3 发起会话（JOB_SEEKER；已存在 40912；firstMessage 可选）。 */
    @Transactional
    public MessageViews.Conversation start(MessageRequests.Start input) {
        UserContext.CurrentUser user = Roles.requireSeeker();
        if (input == null) {
            throw invalid("请求体不能为空");
        }
        String jobId = trimToNull(input.jobId());
        if (jobId == null) {
            throw invalid("请选择职位");
        }
        JobEntity job = jobMapper.selectVisibleById(jobId);
        if (job == null) {
            throw new BusinessException(JobCodes.JOB_NOT_FOUND, "职位不存在或已下线");
        }
        String firstMessage = trimToNull(input.firstMessage());
        if (firstMessage != null && firstMessage.length() > TEXT_MAX_LENGTH) {
            throw invalid("打招呼语不能超过 2000 字");
        }
        ConversationEntity existing = conversationMapper.selectOne(new LambdaQueryWrapper<ConversationEntity>()
                .eq(ConversationEntity::getJobId, jobId)
                .eq(ConversationEntity::getUserId, user.userId())
                .last("LIMIT 1"));
        if (existing != null) {
            throw new BusinessException(MessageCodes.CONVERSATION_EXISTS, "会话已存在");
        }

        LocalDateTime now = nowMillis();
        ConversationEntity conversation = new ConversationEntity();
        conversation.setId("conv-new-" + conversationMapper.nextNewSeq());
        conversation.setJobId(jobId);
        conversation.setUserId(user.userId());
        conversation.setHrId(job.getHrId());
        conversation.setCompanyId(job.getCompanyId());
        conversation.setLastMessageAt(now);
        conversation.setUserUnreadCount(0);
        conversation.setHrUnreadCount(firstMessage == null ? 0 : 1);
        conversation.setClosedByHr(false);
        conversation.setCreatedAt(now);
        try {
            conversationMapper.insert(conversation);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(MessageCodes.CONVERSATION_EXISTS, "会话已存在");
        }

        if (firstMessage != null) {
            MessageEntity system = new MessageEntity();
            system.setId("msg-new-" + messageMapper.nextNewSeq());
            system.setConversationId(conversation.getId());
            system.setCompanyId(conversation.getCompanyId());
            system.setFromType("SYSTEM");
            system.setType("SYSTEM");
            system.setContent("你已向 " + job.getHrName() + " 发起沟通");
            system.setCreatedAt(now);
            system.setReadByUser(true);
            system.setReadByHr(true);
            messageMapper.insert(system);

            MessageEntity message = baseMessage(conversation, user, "TEXT", firstMessage, now);
            messageMapper.insert(message);
            pushToManagers(conversation, MessageViews.Message.from(message));
            autoReplyService.schedule(conversation.getId(), "USER", firstMessage);
        }
        return MessageViews.Conversation.from(conversation);
    }

    // ------------------------------------------------------------------
    // §8.4 发送消息
    // ------------------------------------------------------------------

    /** §8.4 REST 发送（参与方）。 */
    @Transactional
    public MessageViews.Message send(String conversationId, MessageRequests.Send input) {
        return sendAs(UserContext.require(), conversationId, input);
    }

    /** §8.4 发送核心（REST 与 STOMP 共用；WS 线程没有 UserContext，显式传入 actor）。 */
    @Transactional
    public MessageViews.Message sendAs(UserContext.CurrentUser actor, String conversationId,
                                       MessageRequests.Send input) {
        requireActor(actor);
        ConversationEntity conversation = requireAccessible(conversationId, actor);
        String type = normalizeType(input == null ? null : input.type());
        String content = normalizeContent(type, input == null ? null : input.content());
        String idempotencyKey = idempotencyKey(input == null ? null : input.idempotencyKey());

        if (idempotencyKey != null) {
            MessageViews.Message cached = cachedMessage(actor, conversationId, idempotencyKey);
            if (cached != null) {
                return cached;
            }
        }
        limitSendRate(actor.userId());

        LocalDateTime now = nowMillis();
        MessageEntity message = baseMessage(conversation, actor, type, content, now);
        messageMapper.insert(message);
        conversation.setLastMessageAt(now);
        if (Roles.isSeeker(actor)) {
            conversation.setHrUnreadCount(intOf(conversation.getHrUnreadCount()) + 1);
            pushToManagers(conversation, MessageViews.Message.from(message));
        } else {
            conversation.setUserUnreadCount(intOf(conversation.getUserUnreadCount()) + 1);
            realtime.pushMessage(conversation.getUserId(), MessageViews.Message.from(message));
        }
        conversationMapper.updateById(conversation);

        if (idempotencyKey != null) {
            redis.opsForValue().set(idemKey(actor.userId(), conversationId, idempotencyKey),
                    message.getId(), IDEMPOTENCY_TTL);
        }
        if ("TEXT".equals(type)) {
            autoReplyService.schedule(conversation.getId(), Roles.isSeeker(actor) ? "USER" : "HR", content);
        }
        return MessageViews.Message.from(message);
    }

    // ------------------------------------------------------------------
    // §8.5 / §8.6 / §8.8
    // ------------------------------------------------------------------

    /** §8.5 标记会话已读（参与方；返回 unreadCount=0）。 */
    @Transactional
    public MessageViews.ReadResult markRead(String conversationId) {
        return markReadAs(UserContext.require(), conversationId);
    }

    /** §8.5 标记已读核心（REST / WS 共用）。 */
    @Transactional
    public MessageViews.ReadResult markReadAs(UserContext.CurrentUser actor, String conversationId) {
        requireActor(actor);
        ConversationEntity conversation = requireAccessible(conversationId, actor);
        boolean seeker = Roles.isSeeker(actor);
        List<MessageEntity> messages = messageMapper.selectList(new LambdaQueryWrapper<MessageEntity>()
                .eq(MessageEntity::getConversationId, conversation.getId()));
        for (MessageEntity message : messages) {
            if (seeker) {
                if (!"USER".equals(message.getFromType()) && !Boolean.TRUE.equals(message.getReadByUser())) {
                    message.setReadByUser(true);
                    messageMapper.updateById(message);
                }
            } else if (!"HR".equals(message.getFromType()) && !Boolean.TRUE.equals(message.getReadByHr())) {
                message.setReadByHr(true);
                messageMapper.updateById(message);
            }
        }
        if (seeker) {
            conversation.setUserUnreadCount(0);
        } else {
            conversation.setHrUnreadCount(0);
        }
        conversationMapper.updateById(conversation);
        return new MessageViews.ReadResult(0);
    }

    /** §8.6 通知对方正在输入（REST；参与方）。 */
    public void typing(String conversationId, MessageRequests.Typing input) {
        typingAs(UserContext.require(), conversationId, input);
    }

    /** §8.6 正在输入核心（REST / WS 共用）。 */
    public void typingAs(UserContext.CurrentUser actor, String conversationId, MessageRequests.Typing input) {
        requireActor(actor);
        if (input == null || input.isTyping() == null) {
            throw invalid("isTyping 不能为空");
        }
        ConversationEntity conversation = requireAccessible(conversationId, actor);
        boolean seeker = Roles.isSeeker(actor);
        MessageViews.Typing event = new MessageViews.Typing(conversationId,
                seeker ? "USER" : "HR",
                seeker ? actor.userId() : null,
                seeker ? null : hrIdOf(actor, conversation),
                input.isTyping());
        realtime.pushTyping(seeker ? hrUserIds(conversation.getCompanyId())
                : List.of(conversation.getUserId()), event);
    }

    /** §8.8 关闭会话（HR / COMPANY_ADMIN 本公司）。 */
    @Transactional
    public MessageViews.Conversation close(String conversationId) {
        UserContext.CurrentUser actor = Roles.requireManager();
        ConversationEntity conversation = conversationMapper.selectById(conversationId);
        if (conversation == null || !actor.companyId().equals(conversation.getCompanyId())) {
            throw conversationNotFound();
        }
        conversation.setClosedByHr(true);
        conversationMapper.updateById(conversation);
        return MessageViews.Conversation.from(conversation);
    }

    // ------------------------------------------------------------------
    // §8.7 发送简历
    // ------------------------------------------------------------------

    /** §8.7 发送简历快捷操作（JOB_SEEKER 本人；默认简历兜底；无可用简历 42210）。 */
    @Transactional
    public MessageViews.Message sendResume(String conversationId, MessageRequests.SendResume input) {
        UserContext.CurrentUser actor = Roles.requireSeeker();
        ConversationEntity conversation = requireAccessible(conversationId, actor);
        ResumeEntity resume = resolveResume(actor.userId(), input == null ? null : input.resumeId());
        LocalDateTime now = nowMillis();
        MessageEntity message = baseMessage(conversation, actor, "RESUME", resumeContent(resume), now);
        messageMapper.insert(message);
        conversation.setLastMessageAt(now);
        conversation.setHrUnreadCount(intOf(conversation.getHrUnreadCount()) + 1);
        conversationMapper.updateById(conversation);
        pushToManagers(conversation, MessageViews.Message.from(message));
        return MessageViews.Message.from(message);
    }

    // ------------------------------------------------------------------
    // 内部：归属 / 消息 / 简历 / 校验 / 工具
    // ------------------------------------------------------------------

    private ConversationEntity requireAccessible(String conversationId, UserContext.CurrentUser actor) {
        ConversationEntity conversation = conversationMapper.selectById(conversationId);
        if (conversation == null || !accessible(conversation, actor)) {
            throw conversationNotFound();
        }
        return conversation;
    }

    private static boolean accessible(ConversationEntity conversation, UserContext.CurrentUser actor) {
        if (Roles.isSeeker(actor)) {
            return actor.userId().equals(conversation.getUserId());
        }
        if (Roles.isManager(actor)) {
            return actor.companyId() != null && actor.companyId().equals(conversation.getCompanyId());
        }
        return false;
    }

    private static void requireActor(UserContext.CurrentUser actor) {
        if (actor == null || (!Roles.isSeeker(actor) && !Roles.isManager(actor))) {
            throw new BusinessException(ApiCodes.FORBIDDEN, "无权操作会话");
        }
        if (Roles.isManager(actor) && (actor.companyId() == null || actor.companyId().isBlank())) {
            throw new BusinessException(ApiCodes.FORBIDDEN, "缺少企业上下文");
        }
    }

    /** 生成一条客户端消息（fromType / fromId / read flags 由角色决定）。 */
    private MessageEntity baseMessage(ConversationEntity conversation, UserContext.CurrentUser actor,
                                      String type, String content, LocalDateTime now) {
        MessageEntity message = new MessageEntity();
        message.setId("msg-new-" + messageMapper.nextNewSeq());
        message.setConversationId(conversation.getId());
        message.setCompanyId(conversation.getCompanyId());
        message.setType(type);
        message.setContent(content);
        message.setCreatedAt(now);
        if (Roles.isSeeker(actor)) {
            message.setFromType("USER");
            message.setFromUserId(actor.userId());
            message.setReadByUser(true);
            message.setReadByHr(false);
        } else {
            message.setFromType("HR");
            message.setFromHrId(hrIdOf(actor, conversation));
            message.setReadByUser(false);
            message.setReadByHr(true);
        }
        return message;
    }

    private String hrIdOf(UserContext.CurrentUser actor, ConversationEntity conversation) {
        HrProfileEntity profile = hrProfileMapper.selectOne(new LambdaQueryWrapper<HrProfileEntity>()
                .eq(HrProfileEntity::getUserId, actor.userId())
                .last("LIMIT 1"));
        return profile != null && profile.getId() != null ? profile.getId() : conversation.getHrId();
    }

    private void pushToManagers(ConversationEntity conversation, MessageViews.Message message) {
        for (String userId : hrUserIds(conversation.getCompanyId())) {
            realtime.pushMessage(userId, message);
        }
    }

    /** 本公司 HR 账号列表；演示种子中多数公司无 HR 账号，返回空列表即不推 WS（REST 仍可读）。 */
    private List<String> hrUserIds(String companyId) {
        List<CompanyMemberEntity> members = companyMemberMapper.selectList(
                new LambdaQueryWrapper<CompanyMemberEntity>()
                        .eq(CompanyMemberEntity::getCompanyId, companyId));
        Set<String> ids = new LinkedHashSet<>();
        for (CompanyMemberEntity member : members) {
            if (member.getUserId() != null) {
                ids.add(member.getUserId());
            }
        }
        return new ArrayList<>(ids);
    }

    private ResumeEntity resolveResume(String userId, String requestedResumeId) {
        String requested = trimToNull(requestedResumeId);
        if (requested != null) {
            ResumeEntity resume = resumeMapper.selectById(requested);
            if (resume == null || !userId.equals(resume.getUserId()) || resume.getDeletedAt() != null) {
                throw new BusinessException(ApplicationCodes.RESUME_INCOMPLETE, "简历不存在或不属于当前用户");
            }
            return resume;
        }
        UserProfileEntity profile = userProfileMapper.selectOne(new LambdaQueryWrapper<UserProfileEntity>()
                .eq(UserProfileEntity::getUserId, userId)
                .last("LIMIT 1"));
        if (profile != null && trimToNull(profile.getDefaultResumeId()) != null) {
            ResumeEntity resume = resumeMapper.selectById(profile.getDefaultResumeId());
            if (resume != null && userId.equals(resume.getUserId()) && resume.getDeletedAt() == null) {
                return resume;
            }
        }
        List<ResumeEntity> resumes = resumeMapper.selectList(new LambdaQueryWrapper<ResumeEntity>()
                .eq(ResumeEntity::getUserId, userId)
                .isNull(ResumeEntity::getDeletedAt)
                .orderByDesc(ResumeEntity::getIsDefault)
                .orderByAsc(ResumeEntity::getCreatedAt)
                .last("LIMIT 1"));
        if (resumes.isEmpty()) {
            throw new BusinessException(ApplicationCodes.RESUME_INCOMPLETE, "请先完善简历");
        }
        return resumes.get(0);
    }

    private static String resumeContent(ResumeEntity resume) {
        ObjectNode payload = JSON.createObjectNode();
        payload.put("resumeId", resume.getId());
        payload.put("name", resume.getName());
        if (resume.getAttachmentUrl() != null) {
            payload.put("attachmentUrl", resume.getAttachmentUrl());
        }
        try {
            return JSON.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("简历消息序列化失败", e);
        }
    }

    private MessageViews.Message cachedMessage(UserContext.CurrentUser actor, String conversationId,
                                               String idempotencyKey) {
        String cachedId = redis.opsForValue().get(idemKey(actor.userId(), conversationId, idempotencyKey));
        if (cachedId == null) {
            return null;
        }
        MessageEntity message = messageMapper.selectById(cachedId);
        return message == null || !conversationId.equals(message.getConversationId())
                ? null : MessageViews.Message.from(message);
    }

    private void limitSendRate(String userId) {
        long minute = Instant.now().getEpochSecond() / 60;
        String key = SEND_RATE_KEY.formatted(userId, minute);
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, Duration.ofSeconds(60));
        }
        if (count != null && count > SEND_LIMIT_PER_MINUTE) {
            throw new BusinessException(ApiCodes.RATE_LIMITED, "发送过于频繁，请稍后再试");
        }
    }

    private static String idemKey(String userId, String conversationId, String key) {
        return IDEM_KEY.formatted(userId, conversationId, key);
    }

    private static String normalizeType(String raw) {
        String type = trimToNull(raw);
        if (type == null || !CLIENT_TYPES.contains(type)) {
            throw invalid("消息类型无效");
        }
        return type;
    }

    private static String normalizeContent(String type, JsonNode node) {
        if (node == null || node.isNull()) {
            throw invalid("消息内容不能为空");
        }
        if ("TEXT".equals(type)) {
            if (!node.isTextual()) {
                throw invalid("文本消息内容必须是字符串");
            }
            String text = node.asText().trim();
            if (text.isEmpty()) {
                throw invalid("消息内容不能为空");
            }
            if (text.length() > TEXT_MAX_LENGTH) {
                throw invalid("消息内容不能超过 2000 字");
            }
            return text;
        }
        if (node.isTextual()) {
            String text = node.asText().trim();
            if (text.isEmpty()) {
                throw invalid("消息内容不能为空");
            }
            if (text.length() > STRUCTURED_MAX_LENGTH) {
                throw invalid("消息内容过长");
            }
            return text;
        }
        String serialized = node.toString();
        if (serialized.length() > STRUCTURED_MAX_LENGTH) {
            throw invalid("消息内容过长");
        }
        return serialized;
    }

    private static String idempotencyKey(String raw) {
        String key = trimToNull(raw);
        if (key != null && key.length() > 128) {
            throw invalid("idempotencyKey 不能超过 128 字符");
        }
        return key;
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(MessageCodes.INVALID_MESSAGE, message);
    }

    private static BusinessException conversationNotFound() {
        return new BusinessException(MessageCodes.CONVERSATION_NOT_FOUND, "会话不存在");
    }

    private static int intOf(Integer value) {
        return value == null ? 0 : value;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static LocalDateTime nowMillis() {
        return LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS);
    }
}
