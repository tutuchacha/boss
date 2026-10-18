package com.pinliao.backend.application.dto;

/**
 * 投递 / 面试模块请求体（契约 §9 / §10 / §8.7）。
 *
 * <p>字段校验在 service 层完成，以便按契约返回 42201 / 42210 / 40914 等业务码。</p>
 */
public final class ApplicationRequests {

    private ApplicationRequests() {
    }

    /** §9.1 投递（resumeId 可省略：回退默认简历；coverLetter ≤ 500）。 */
    public record Apply(String jobId, String resumeId, String coverLetter) {
    }

    /** §9.5 HR 更新投递状态（VIEWED / INTERVIEW / OFFER / REJECTED）。 */
    public record StatusChange(String status, String note) {
    }

    /** §10.5 拒绝面试（reason 暂不落库，保留契约形状）。 */
    public record Decline(String reason) {
    }

    /** §8.7 约面试。scheduledAt 为 ISO-8601（前端 Date.toISOString()）。 */
    public record Invite(String scheduledAt, String format, String location,
                         String onlineUrl, String note) {
    }
}
