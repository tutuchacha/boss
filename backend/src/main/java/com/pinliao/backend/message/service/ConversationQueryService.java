package com.pinliao.backend.message.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pinliao.backend.application.entity.ConversationEntity;
import com.pinliao.backend.application.entity.MessageEntity;
import com.pinliao.backend.application.entity.ResumeEntity;
import com.pinliao.backend.application.mapper.ConversationMapper;
import com.pinliao.backend.application.mapper.MessageMapper;
import com.pinliao.backend.application.mapper.ResumeMapper;
import com.pinliao.backend.auth.context.Roles;
import com.pinliao.backend.auth.context.UserContext;
import com.pinliao.backend.auth.entity.CompanyEntity;
import com.pinliao.backend.auth.entity.HrProfileEntity;
import com.pinliao.backend.auth.entity.UserProfileEntity;
import com.pinliao.backend.auth.mapper.CompanyMapper;
import com.pinliao.backend.auth.mapper.HrProfileMapper;
import com.pinliao.backend.auth.mapper.UserProfileMapper;
import com.pinliao.backend.common.api.ApiCodes;
import com.pinliao.backend.common.api.PageViews;
import com.pinliao.backend.common.exception.BusinessException;
import com.pinliao.backend.job.entity.JobEntity;
import com.pinliao.backend.job.mapper.JobMapper;
import com.pinliao.backend.message.MessageCodes;
import com.pinliao.backend.message.view.MessageViews;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * 会话 / 消息读服务（契约 §8.1、§8.2，以及 HR 聊天页使用的扩展 GET /conversations/{id}）。
 *
 * <p><b>视角</b>：求职者只看 {@code user_id=本人}；HR / COMPANY_ADMIN 看
 * {@code company_id=本公司}（租户拦截器还会再注入一次兜底）。列表项同时携带
 * 求职者需要的 job / hr / company 与 HR 需要的 candidate，前端按角色取用。</p>
 *
 * <p><b>游标</b>：base64(JSON{k,id})，排序键 = lastMessageAt / createdAt DESC, id ASC；
 * 演示规模下全量取回后内存切片（与 T3/T4 同一策略）。</p>
 */
@Service
public class ConversationQueryService {

    static final int DEFAULT_LIMIT = 20;
    static final int MAX_LIMIT = 100;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final JobMapper jobMapper;
    private final CompanyMapper companyMapper;
    private final HrProfileMapper hrProfileMapper;
    private final UserProfileMapper userProfileMapper;
    private final ResumeMapper resumeMapper;

    public ConversationQueryService(ConversationMapper conversationMapper, MessageMapper messageMapper,
                                    JobMapper jobMapper, CompanyMapper companyMapper,
                                    HrProfileMapper hrProfileMapper, UserProfileMapper userProfileMapper,
                                    ResumeMapper resumeMapper) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.jobMapper = jobMapper;
        this.companyMapper = companyMapper;
        this.hrProfileMapper = hrProfileMapper;
        this.userProfileMapper = userProfileMapper;
        this.resumeMapper = resumeMapper;
    }

    /** §8.1 会话列表（求职者本人 / 本公司 HR）。 */
    public PageViews.Page<MessageViews.ListItem> list(String cursor, Integer limit) {
        UserContext.CurrentUser user = requireConversationUser();
        List<ConversationEntity> rows;
        if (Roles.isSeeker(user)) {
            rows = conversationMapper.selectList(new LambdaQueryWrapper<ConversationEntity>()
                    .eq(ConversationEntity::getUserId, user.userId()));
        } else {
            rows = conversationMapper.selectList(new LambdaQueryWrapper<ConversationEntity>()
                    .eq(ConversationEntity::getCompanyId, user.companyId()));
        }
        rows = new ArrayList<>(rows);
        rows.sort(Comparator.comparing((ConversationEntity c) -> epochMillis(c.getLastMessageAt())).reversed()
                .thenComparing(ConversationEntity::getId));

        Slice<ConversationEntity> slice = slice(rows, cursor, limit,
                ConversationEntity::getId, c -> epochMillis(c.getLastMessageAt()));
        List<MessageViews.ListItem> views = slice.items().stream().map(this::toItem).toList();
        return new PageViews.Page<>(views,
                new PageViews.Pagination(slice.nextCursor(), slice.hasMore(), rows.size()));
    }

    /** 会话详情（HR 聊天页顶栏；契约外的扩展读取，不改变 §8 的八条计账）。 */
    public MessageViews.ListItem detail(String conversationId) {
        return toItem(requireAccessible(conversationId, UserContext.require()));
    }

    /** §8.2 会话消息列表（参与方 / 本公司 HR；createdAt DESC, id ASC）。 */
    public PageViews.Page<MessageViews.Message> messages(String conversationId, String cursor, Integer limit) {
        ConversationEntity conversation = requireAccessible(conversationId, UserContext.require());
        List<MessageEntity> rows = messageMapper.selectList(new LambdaQueryWrapper<MessageEntity>()
                .eq(MessageEntity::getConversationId, conversation.getId()));
        rows = new ArrayList<>(rows);
        rows.sort(Comparator.comparing((MessageEntity m) -> epochMillis(m.getCreatedAt())).reversed()
                .thenComparing(MessageEntity::getId));

        Slice<MessageEntity> slice = slice(rows, cursor, limit,
                MessageEntity::getId, m -> epochMillis(m.getCreatedAt()));
        List<MessageViews.Message> views = slice.items().stream().map(MessageViews.Message::from).toList();
        return new PageViews.Page<>(views,
                new PageViews.Pagination(slice.nextCursor(), slice.hasMore(), rows.size()));
    }

    // ------------------------------------------------------------------
    // 归属 / 组装
    // ------------------------------------------------------------------

    /** 读取并对会话做归属校验；不存在 / 非参与方 / 跨公司统一 40410。 */
    ConversationEntity requireAccessible(String conversationId, UserContext.CurrentUser user) {
        ConversationEntity conversation = conversationMapper.selectById(conversationId);
        if (conversation == null || !accessible(conversation, user)) {
            throw new BusinessException(MessageCodes.CONVERSATION_NOT_FOUND, "会话不存在");
        }
        return conversation;
    }

    static boolean accessible(ConversationEntity conversation, UserContext.CurrentUser user) {
        if (Roles.isSeeker(user)) {
            return user.userId().equals(conversation.getUserId());
        }
        if (Roles.isManager(user)) {
            return user.companyId() != null && user.companyId().equals(conversation.getCompanyId());
        }
        return false;
    }

    private UserContext.CurrentUser requireConversationUser() {
        UserContext.CurrentUser user = UserContext.require();
        if (!Roles.isSeeker(user) && !Roles.isManager(user)) {
            throw new BusinessException(ApiCodes.FORBIDDEN, "无权查看会话");
        }
        if (Roles.isManager(user) && (user.companyId() == null || user.companyId().isBlank())) {
            throw new BusinessException(ApiCodes.FORBIDDEN, "缺少企业上下文");
        }
        return user;
    }

    private MessageViews.ListItem toItem(ConversationEntity conversation) {
        JobEntity job = jobMapper.selectById(conversation.getJobId());
        CompanyEntity company = companyMapper.selectById(conversation.getCompanyId());
        HrProfileEntity hr = hrProfileMapper.selectById(conversation.getHrId());
        UserProfileEntity profile = userProfileMapper.selectOne(new LambdaQueryWrapper<UserProfileEntity>()
                .eq(UserProfileEntity::getUserId, conversation.getUserId())
                .last("LIMIT 1"));
        MessageEntity last = messageMapper.selectOne(new LambdaQueryWrapper<MessageEntity>()
                .eq(MessageEntity::getConversationId, conversation.getId())
                .orderByDesc(MessageEntity::getCreatedAt)
                .orderByDesc(MessageEntity::getId)
                .last("LIMIT 1"));

        MessageViews.JobBrief jobBrief = job == null
                ? new MessageViews.JobBrief(conversation.getJobId(), "职位已下线", "", "", "")
                : new MessageViews.JobBrief(job.getId(), job.getTitle(), job.getCity(), job.getArea(),
                salaryText(job));
        String hrName = job != null && job.getHrName() != null ? job.getHrName()
                : hr != null ? hr.getName() : "HR";
        String hrTitle = job != null && job.getHrTitle() != null ? job.getHrTitle()
                : hr != null ? hr.getTitle() : "";
        MessageViews.HrBrief hrBrief = new MessageViews.HrBrief(conversation.getHrId(), hrName, hrTitle,
                hr == null ? null : hr.getAvatarUrl());
        MessageViews.CompanyBrief companyBrief = new MessageViews.CompanyBrief(
                conversation.getCompanyId(), company == null ? "" : company.getName());
        MessageViews.Candidate candidate = candidateOf(conversation.getUserId(), profile);

        return new MessageViews.ListItem(MessageViews.Conversation.from(conversation), jobBrief, hrBrief,
                companyBrief, candidate, last == null ? null : MessageViews.Message.from(last));
    }

    private MessageViews.Candidate candidateOf(String userId, UserProfileEntity profile) {
        if (profile == null) {
            return new MessageViews.Candidate(userId, "候选人", "—", "—", 0, "—", 0);
        }
        return new MessageViews.Candidate(userId,
                profile.getName() == null ? "候选人" : profile.getName(),
                profile.getExpectSub() == null ? "—" : profile.getExpectSub(),
                profile.getExpectCity() == null ? "—" : profile.getExpectCity(),
                profile.getWorkYears() == null ? 0 : profile.getWorkYears(),
                profile.getEdu() == null ? "—" : profile.getEdu(),
                resumeCompleteness(userId, profile.getDefaultResumeId()));
    }

    /**
     * 简历完善度：当前数据模型没有该列，演示阶段以「存在可用简历」给 85 分，
     * 缺简历给 0（与 mock 求职者 85 的展示基线一致；T8 候选人才筛选可再细化算法）。
     */
    private int resumeCompleteness(String userId, String defaultResumeId) {
        if (trimToNull(defaultResumeId) != null) {
            ResumeEntity resume = resumeMapper.selectById(defaultResumeId);
            if (resume != null && resume.getDeletedAt() == null) {
                return 85;
            }
        }
        Long count = resumeMapper.selectCount(new LambdaQueryWrapper<ResumeEntity>()
                .eq(ResumeEntity::getUserId, userId)
                .isNull(ResumeEntity::getDeletedAt));
        return count != null && count > 0 ? 85 : 0;
    }

    private static String salaryText(JobEntity job) {
        int min = job.getSalaryMin() == null ? 0 : job.getSalaryMin();
        int max = job.getSalaryMax() == null ? 0 : job.getSalaryMax();
        return min + "-" + max + "K";
    }

    // ------------------------------------------------------------------
    // 游标编解码（包级可见便于单测）
    // ------------------------------------------------------------------

    record Cursor(long k, String id) {
    }

    record Slice<T>(List<T> items, String nextCursor, boolean hasMore) {
    }

    static <T> Slice<T> slice(List<T> rows, String rawCursor, Integer limit,
                             Function<T, String> idOf, Function<T, Long> keyOf) {
        int size = clampLimit(limit);
        int start = 0;
        Cursor cursor = trimToNull(rawCursor) == null ? null : decodeCursor(rawCursor);
        if (cursor != null) {
            start = firstIndexAfter(rows, cursor, idOf, keyOf);
        }
        int end = Math.min(start + size, rows.size());
        List<T> items = new ArrayList<>(rows.subList(start, end));
        boolean hasMore = end < rows.size();
        String nextCursor = null;
        if (hasMore && !items.isEmpty()) {
            T last = items.get(items.size() - 1);
            nextCursor = encodeCursor(keyOf.apply(last), idOf.apply(last));
        }
        return new Slice<>(items, nextCursor, hasMore);
    }

    private static <T> int firstIndexAfter(List<T> rows, Cursor cursor,
                                           Function<T, String> idOf, Function<T, Long> keyOf) {
        for (int i = 0; i < rows.size(); i++) {
            T row = rows.get(i);
            long k = keyOf.apply(row);
            if (k < cursor.k() || (k == cursor.k() && idOf.apply(row).compareTo(cursor.id()) > 0)) {
                return i;
            }
        }
        return rows.size();
    }

    static String encodeCursor(long keyMillis, String id) {
        try {
            ObjectNode node = JSON.createObjectNode();
            node.put("k", keyMillis);
            node.put("id", id);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(JSON.writeValueAsBytes(node));
        } catch (Exception e) {
            throw new IllegalStateException("游标编码失败", e);
        }
    }

    static Cursor decodeCursor(String raw) {
        byte[] bytes;
        try {
            bytes = Base64.getUrlDecoder().decode(raw);
        } catch (IllegalArgumentException urlSafeFailed) {
            try {
                bytes = Base64.getDecoder().decode(raw);
            } catch (IllegalArgumentException standardFailed) {
                throw invalidCursor();
            }
        }
        try {
            JsonNode node = JSON.readTree(bytes);
            if (node == null || !node.path("k").isNumber() || !node.path("id").isTextual()) {
                throw invalidCursor();
            }
            return new Cursor(node.path("k").asLong(), node.path("id").asText());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw invalidCursor();
        }
    }

    private static BusinessException invalidCursor() {
        return new BusinessException(ApiCodes.PARAM_INVALID, "分页游标无效");
    }

    static int clampLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.max(1, Math.min(limit, MAX_LIMIT));
    }

    private static long epochMillis(LocalDateTime time) {
        return time == null ? 0 : time.toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
