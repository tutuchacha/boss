package com.pinliao.backend.application.view;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.pinliao.backend.application.entity.ApplicationEntity;
import com.pinliao.backend.application.entity.InterviewEntity;
import com.pinliao.backend.application.entity.MessageEntity;
import com.pinliao.backend.application.entity.ResumeEntity;
import com.pinliao.backend.job.view.JobViews;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 投递 / 面试接口视图（契约 §9 / §10 / §8.7；字段与 {@code src/types/api.ts} 对齐）。
 *
 * <p>时间统一输出 ISO-8601 UTC；null 字段不序列化（对齐 mock 的 undefined 缺席形状）。
 * 分页响应使用 {@code PageViews}，其中 nextCursor 显式输出 null。</p>
 */
public final class ApplicationViews {

    private ApplicationViews() {
    }

    /** Application：§9.1 / §9.4 / §9.5 的出参。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Application(
            String id,
            String userId,
            String jobId,
            String companyId,
            String resumeId,
            String status,
            String coverLetter,
            Instant createdAt,
            Instant updatedAt) {

        public static Application from(ApplicationEntity a) {
            return new Application(a.getId(), a.getUserId(), a.getJobId(), a.getCompanyId(), a.getResumeId(),
                    a.getStatus(), a.getCoverLetter(), instant(a.getCreatedAt()), instant(a.getUpdatedAt()));
        }
    }

    /** ApplicationWithJob：§9.2 我的投递列表项（application 字段平铺 + 职位摘要）。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record WithJob(
            String id,
            String userId,
            String jobId,
            String companyId,
            String resumeId,
            String status,
            String coverLetter,
            Instant createdAt,
            Instant updatedAt,
            JobViews.Summary job) {

        public static WithJob from(ApplicationEntity a, JobViews.Summary job) {
            return new WithJob(a.getId(), a.getUserId(), a.getJobId(), a.getCompanyId(), a.getResumeId(),
                    a.getStatus(), a.getCoverLetter(), instant(a.getCreatedAt()), instant(a.getUpdatedAt()), job);
        }
    }

    /**
     * ApplicationDetail：§9.3 投递详情。
     *
     * <p>{@code conversationId} 为 T4 增量字段（契约未列，但双端发消息 / 约面试需要会话上下文；
     * 由投递时占位创建，T6 会话模块可复用）。</p>
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Detail(
            String id,
            String userId,
            String jobId,
            String companyId,
            String resumeId,
            String status,
            String coverLetter,
            Instant createdAt,
            Instant updatedAt,
            String conversationId,
            JobViews.Detail job,
            Resume resume,
            List<Interview> interviews) {

        public static Detail of(ApplicationEntity a, String conversationId, JobViews.Detail job,
                                Resume resume, List<Interview> interviews) {
            return new Detail(a.getId(), a.getUserId(), a.getJobId(), a.getCompanyId(), a.getResumeId(),
                    a.getStatus(), a.getCoverLetter(), instant(a.getCreatedAt()), instant(a.getUpdatedAt()),
                    conversationId, job, resume, interviews);
        }
    }

    /** Resume：投递详情中的简历快照（content 原样透传 JSON）。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Resume(
            String id,
            String userId,
            String name,
            JsonNode content,
            String attachmentUrl,
            Boolean isDefault,
            Instant createdAt,
            Instant updatedAt) {

        public static Resume from(ResumeEntity r, JsonNode content) {
            return new Resume(r.getId(), r.getUserId(), r.getName(), content, r.getAttachmentUrl(),
                    r.getIsDefault(), instant(r.getCreatedAt()), instant(r.getUpdatedAt()));
        }
    }

    /** Interview：§10.3 / §10.4 / §10.5 出参。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Interview(
            String id,
            String applicationId,
            String companyId,
            Instant scheduledAt,
            String format,
            String location,
            String onlineUrl,
            String note,
            String status,
            Instant createdAt,
            Instant updatedAt) {

        public static Interview from(InterviewEntity i) {
            return new Interview(i.getId(), i.getApplicationId(), i.getCompanyId(), instant(i.getScheduledAt()),
                    i.getFormat(), i.getLocation(), i.getOnlineUrl(), i.getNote(), i.getStatus(),
                    instant(i.getCreatedAt()), instant(i.getUpdatedAt()));
        }
    }

    /** InterviewWithJob：§10.1 求职者视角列表项。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record InterviewWithJob(
            String id,
            String applicationId,
            String companyId,
            Instant scheduledAt,
            String format,
            String location,
            String onlineUrl,
            String note,
            String status,
            Instant createdAt,
            Instant updatedAt,
            JobViews.Summary job) {

        public static InterviewWithJob from(InterviewEntity i, JobViews.Summary job) {
            return new InterviewWithJob(i.getId(), i.getApplicationId(), i.getCompanyId(), instant(i.getScheduledAt()),
                    i.getFormat(), i.getLocation(), i.getOnlineUrl(), i.getNote(), i.getStatus(),
                    instant(i.getCreatedAt()), instant(i.getUpdatedAt()), job);
        }
    }

    /** InterviewWithCandidate：§10.2 招聘者视角列表项。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CandidateUser(String id, String phone) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CandidateProfile(String name, String avatarUrl) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record InterviewWithCandidate(
            String id,
            String applicationId,
            String companyId,
            Instant scheduledAt,
            String format,
            String location,
            String onlineUrl,
            String note,
            String status,
            Instant createdAt,
            Instant updatedAt,
            CandidateUser user,
            CandidateProfile profile) {

        public static InterviewWithCandidate from(InterviewEntity i, CandidateUser user, CandidateProfile profile) {
            return new InterviewWithCandidate(i.getId(), i.getApplicationId(), i.getCompanyId(),
                    instant(i.getScheduledAt()), i.getFormat(), i.getLocation(), i.getOnlineUrl(), i.getNote(),
                    i.getStatus(), instant(i.getCreatedAt()), instant(i.getUpdatedAt()), user, profile);
        }
    }

    /** Message：§8.7 约面试出参（契约规定返回邀请消息）。 */
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
            return new Message(m.getId(), m.getConversationId(), m.getFromType(), m.getFromUserId(), m.getFromHrId(),
                    m.getType(), m.getContent(), instant(m.getCreatedAt()),
                    Boolean.TRUE.equals(m.getReadByUser()), Boolean.TRUE.equals(m.getReadByHr()));
        }
    }

    private static Instant instant(LocalDateTime time) {
        return time == null ? null : time.toInstant(ZoneOffset.UTC);
    }
}
