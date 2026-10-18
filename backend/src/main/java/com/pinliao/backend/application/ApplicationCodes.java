package com.pinliao.backend.application;

/**
 * 投递 / 面试模块专属业务码（契约 §5：5 位数字，前 3 位对应 HTTP 状态码）。
 *
 * <p>与前端 mock 对齐：{@code src/api/mock/applications.ts} /
 * {@code src/api/hrApplications.ts} 使用 40911 / 40913 / 40914 / 40915 / 42210。</p>
 */
public final class ApplicationCodes {

    /** 40410 投递记录 / 面试 / 会话不存在（含跨用户 / 跨公司，不泄露存在性） */
    public static final int APPLICATION_NOT_FOUND = 40410;

    /** 40911 已投递该职位（幂等键 userId + jobId；撤回后可重新投递） */
    public static final int ALREADY_APPLIED = 40911;

    /** 40913 当前状态不可撤回 / 不可变更（已撤回、Offer、不合适等终态） */
    public static final int STATUS_CONFLICT = 40913;

    /** 40914 当前投递状态不可发起面试（REJECTED / WITHDRAWN） */
    public static final int INVITE_NOT_ALLOWED = 40914;

    /** 40915 已有待确认的面试邀请（同一投递同时只允许一个 PENDING） */
    public static final int PENDING_INVITE_EXISTS = 40915;

    /** 42210 简历不完整 / 简历不可用（投递前置校验） */
    public static final int RESUME_INCOMPLETE = 42210;

    /** 42210 面试时间非法 / 早于当前时间（复用 mock 的 42210 前置条件码） */
    public static final int INVALID_SCHEDULE = 42210;

    private ApplicationCodes() {
    }
}
