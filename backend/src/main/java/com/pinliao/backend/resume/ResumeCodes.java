package com.pinliao.backend.resume;

/** 简历模块专属业务码（契约 §5）。 */
public final class ResumeCodes {

    /** 40410 简历不存在或无权查看（不泄露存在性） */
    public static final int NOT_FOUND = 40410;

    /** 42201 简历字段 / 业务校验失败 */
    public static final int INVALID = 42201;

    private ResumeCodes() {
    }
}
