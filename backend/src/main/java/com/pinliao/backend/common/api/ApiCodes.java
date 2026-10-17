package com.pinliao.backend.common.api;

/**
 * 业务错误码表（契约 §5：5 位数字，前 3 位对应 HTTP 状态码）。
 *
 * <p>通用码在此集中定义；各业务模块专属码（如 40911 重复投递、40913 不可撤回）在对应模块内补充。</p>
 */
public final class ApiCodes {

    /** 成功 */
    public static final int OK = 0;
    public static final String MSG_OK = "ok";

    /** 40001 通用请求参数错误 */
    public static final int PARAM_INVALID = 40001;
    /** 40101 未登录（缺少 Authorization） */
    public static final int UNAUTHORIZED = 40101;
    /** 40102 token 已过期 */
    public static final int TOKEN_EXPIRED = 40102;
    /** 40103 refresh token 无效 */
    public static final int REFRESH_TOKEN_INVALID = 40103;
    /** 40110 账号/密码错误或验证码错误（契约 §1.2~§1.4） */
    public static final int LOGIN_FAILED = 40110;
    /** 40111 账号已禁用（契约 §1.2~§1.4） */
    public static final int ACCOUNT_DISABLED = 40111;
    /** 40301 无权限访问该资源（跨租户 / 角色不符） */
    public static final int FORBIDDEN = 40301;
    /** 40401 资源不存在 */
    public static final int NOT_FOUND = 40401;
    /** 40901 资源已存在 */
    public static final int CONFLICT = 40901;
    /** 42201 字段校验失败（配合 errors 数组） */
    public static final int FIELD_VALIDATION = 42201;
    /** 42901 请求过于频繁（限流） */
    public static final int RATE_LIMITED = 42901;
    /** 50001 服务器内部错误 */
    public static final int INTERNAL_ERROR = 50001;

    private ApiCodes() {
    }

    /** 由业务码推导 HTTP 状态码（契约 §5：前 3 位）；未知码段一律按 500 处理。 */
    public static int httpStatus(int code) {
        if (code == OK) {
            return 200;
        }
        int prefix = code / 100;
        return switch (prefix) {
            case 400, 401, 403, 404, 409, 422, 429, 500 -> prefix;
            default -> 500;
        };
    }
}
