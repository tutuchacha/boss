package com.pinliao.backend.message.view;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.pinliao.backend.application.entity.ConversationEntity;
import com.pinliao.backend.application.entity.MessageEntity;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 消息接口视图（契约 §8.1~§8.8；字段与 {@code src/types/api.ts} 的
 * Conversation / Message / ConversationListItem 及 HR 端 HrConversationView 对齐）。
 *
 * <p>列表项采用「超集」形状：求职者需要的 job / hr / company，与 HR 需要的 candidate
 * 同时返回，便于同一端点按角色复用；新增字段不影响旧客户端。时间统一 ISO-8601 UTC。</p>
 */
public final class MessageViews {

    private MessageViews() {
    }

    /** Message：§8.2 / §8.4 / §8.7 出参。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Message(
            String id,
            String conversationId,
            String fromType,
            String fromUserId,
            String fromHrId,
            String type,
            String content,
            Instant createdAt,
            boolean readByUser,
            boolean readByHr) {

        public static Message from(MessageEntity m) {
            return new Message(m.getId(), m.getConversationId(), m.getFromType(), m.getFromUserId(),
                    m.getFromHrId(), m.getType(), m.getContent(), instant(m.getCreatedAt()),
                    Boolean.TRUE.equals(m.getReadByUser()), Boolean.TRUE.equals(m.getReadByHr()));
        }
    }

    /** Conversation（§8.3 / §8.8 出参，也是列表项内的 conversation 字段）。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Conversation(
            String id,
            String jobId,
            String userId,
            String hrId,
            String companyId,
            Instant lastMessageAt,
            int userUnreadCount,
            int hrUnreadCount,
            boolean closedByHr,
            Instant createdAt) {

        public static Conversation from(ConversationEntity c) {
            return new Conversation(c.getId(), c.getJobId(), c.getUserId(), c.getHrId(), c.getCompanyId(),
                    instant(c.getLastMessageAt()), intOf(c.getUserUnreadCount()), intOf(c.getHrUnreadCount()),
                    Boolean.TRUE.equals(c.getClosedByHr()), instant(c.getCreatedAt()));
        }
    }

    /** 列表项中的职位摘要（求职者需要 city / area / salary；HR 只用 id / title / salary）。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record JobBrief(String id, String title, String city, String area, String salary) {
    }

    /** 列表项中的 HR 摘要（Pick&lt;HrProfile,'id'|'name'|'title'|'avatarUrl'&gt;）。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record HrBrief(String id, String name, String title, String avatarUrl) {
    }

    /** 列表项中的公司摘要（Pick&lt;Company,'id'|'name'&gt;）。 */
    public record CompanyBrief(String id, String name) {
    }

    /** HR 侧列表项的候选人摘要（对齐 mock HrConversationView.candidate）。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Candidate(
            String userId,
            String name,
            String expect,
            String city,
            Integer workYears,
            String edu,
            Integer resumeCompleteness) {
    }

    /** §8.1 会话列表项 / §8.2 会话详情（扩展的 GET /conversations/{id}）。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ListItem(
            Conversation conversation,
            JobBrief job,
            HrBrief hr,
            CompanyBrief company,
            Candidate candidate,
            Message lastMessage) {
    }

    /** §8.5 标记已读出参：{@code {unreadCount:0}}。 */
    public record ReadResult(int unreadCount) {
    }

    /** §8.6 / WebSocket typing 事件体。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Typing(
            String conversationId,
            String fromType,
            String userId,
            String hrId,
            boolean isTyping) {
    }

    private static Instant instant(LocalDateTime time) {
        return time == null ? null : time.toInstant(ZoneOffset.UTC);
    }

    private static int intOf(Integer value) {
        return value == null ? 0 : value;
    }
}
