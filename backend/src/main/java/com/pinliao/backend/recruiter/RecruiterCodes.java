package com.pinliao.backend.recruiter;

/**
 * 招聘者模块专属业务码（契约 §5；与 mock {@code src/api/mock/*} 口径一致）。
 *
 * <p>40410 用于「资源不存在或无权查看」（不泄露存在性）；42210 用于 §4.5「筛选条件为空」。
 * 字段 / 业务参数校验统一复用 {@code ApiCodes.FIELD_VALIDATION}（42201）。</p>
 */
public final class RecruiterCodes {

    /** 40410 招聘者档案不存在或无权查看 */
    public static final int NOT_FOUND = 40410;

    /** 42210 §4.5 筛选条件为空（query 与 tags 同时为空） */
    public static final int EMPTY_CRITERIA = 42210;

    private RecruiterCodes() {
    }
}
