package com.pinliao.backend.company;

/**
 * 公司模块专属业务码（契约 §5：5 位数字，前 3 位对应 HTTP 状态码）。
 *
 * <p>与前端 mock 对齐：{@code src/api/company.ts} 使用 40410（公司不存在）/
 * 42210（资料校验失败）/ 40916（已认证）。</p>
 */
public final class CompanyCodes {

    /** 40410 公司不存在（公开详情 / 公司职位 / 当前登录者所属公司） */
    public static final int COMPANY_NOT_FOUND = 40410;

    /** 40916 公司已完成企业认证，无需重复提交（契约 §5.6） */
    public static final int ALREADY_VERIFIED = 40916;

    /** 42210 公司资料 / 企业认证资料校验失败（对齐 mock 的前置条件码） */
    public static final int PROFILE_INVALID = 42210;

    private CompanyCodes() {
    }
}
