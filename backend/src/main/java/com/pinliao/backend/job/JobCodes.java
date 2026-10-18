package com.pinliao.backend.job;

/**
 * 职位模块专属业务码（契约 §5：5 位数字，前 3 位对应 HTTP 状态码）。
 *
 * <p>与前端 mock 对齐：{@code src/api/mock/hrJobs.ts} 使用 40410（职位不存在）/ 40310（无权操作该职位）。</p>
 */
public final class JobCodes {

    /** 40410 职位不存在（对求职者不可见：不存在 / 已关闭 / 已删除 / 非公开） */
    public static final int JOB_NOT_FOUND = 40410;

    /** 40310 无权操作该职位（HR 写接口：不存在 / 非本公司 / 已软删；对齐 mock hrJobs.ts） */
    public static final int JOB_FORBIDDEN = 40310;

    /** 42211 企业配额已满，无法发布职位（契约 §6.5；tenant_configs.max_jobs，缺省 50） */
    public static final int COMPANY_QUOTA_EXCEEDED = 42211;

    private JobCodes() {
    }
}
