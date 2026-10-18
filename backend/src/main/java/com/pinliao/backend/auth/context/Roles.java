package com.pinliao.backend.auth.context;

import com.pinliao.backend.common.api.ApiCodes;
import com.pinliao.backend.common.exception.BusinessException;

/**
 * 角色守卫（T4 投递 / 面试模块使用）。
 *
 * <p>管理员口径与 T3b 职位写一致：仅 HR / COMPANY_ADMIN 且 token 带 companyId；
 * 求职者接口仅 JOB_SEEKER。越权统一 40301。</p>
 */
public final class Roles {

    public static final String SEEKER = "JOB_SEEKER";
    public static final String HR = "HR";
    public static final String COMPANY_ADMIN = "COMPANY_ADMIN";

    private Roles() {
    }

    public static UserContext.CurrentUser requireSeeker() {
        UserContext.CurrentUser user = UserContext.require();
        if (!SEEKER.equals(user.role())) {
            throw new BusinessException(ApiCodes.FORBIDDEN, "仅求职者可执行该操作");
        }
        return user;
    }

    public static UserContext.CurrentUser requireManager() {
        UserContext.CurrentUser user = UserContext.require();
        if (!isManager(user) || user.companyId() == null || user.companyId().isBlank()) {
            throw new BusinessException(ApiCodes.FORBIDDEN, "无权执行该操作");
        }
        return user;
    }

    public static boolean isSeeker(UserContext.CurrentUser user) {
        return user != null && SEEKER.equals(user.role());
    }

    public static boolean isManager(UserContext.CurrentUser user) {
        return user != null && (HR.equals(user.role()) || COMPANY_ADMIN.equals(user.role()));
    }
}
