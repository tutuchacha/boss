package com.pinliao.backend.auth.context;

import com.pinliao.backend.common.api.ApiCodes;
import com.pinliao.backend.common.exception.BusinessException;

import java.time.Instant;

/**
 * 当前登录用户上下文：由 {@code AuthInterceptor} 从 JWT 解析后写入，请求结束清理。
 */
public final class UserContext {

    /** 解析后的登录态（后续租户拦截器等从这里取 companyId）。 */
    public record CurrentUser(String userId, String role, String companyId, String jti, Instant expiresAt) {
    }

    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(CurrentUser user) {
        HOLDER.set(user);
    }

    /** 未登录时返回 null（受保护接口由拦截器保证非空）。 */
    public static CurrentUser get() {
        return HOLDER.get();
    }

    /** 取当前登录用户；未登录抛 40101（正常路径不会发生，防止漏挂拦截器的接口静默通过）。 */
    public static CurrentUser require() {
        CurrentUser user = HOLDER.get();
        if (user == null) {
            throw new BusinessException(ApiCodes.UNAUTHORIZED, "请先登录");
        }
        return user;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
