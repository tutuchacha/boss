package com.pinliao.backend.auth.web;

import com.pinliao.backend.auth.context.UserContext;
import com.pinliao.backend.auth.jwt.JwtService;
import com.pinliao.backend.auth.token.AuthTokenStore;
import com.pinliao.backend.common.api.ApiCodes;
import com.pinliao.backend.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录态拦截器：解析 {@code Authorization: Bearer <accessToken>} 并写入 {@link UserContext}。
 *
 * <p>错误码：缺少 / 非法 token → 40101；已过期 → 40102；已退出（jti 吊销名单）→ 40102。</p>
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final AuthTokenStore tokenStore;

    public AuthInterceptor(JwtService jwtService, AuthTokenStore tokenStore) {
        this.jwtService = jwtService;
        this.tokenStore = tokenStore;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            throw new BusinessException(ApiCodes.UNAUTHORIZED, "请先登录");
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            throw new BusinessException(ApiCodes.UNAUTHORIZED, "请先登录");
        }
        JwtService.ParsedAccessToken parsed = jwtService.parse(token);
        if (tokenStore.isAccessRevoked(parsed.jti())) {
            throw new BusinessException(ApiCodes.TOKEN_EXPIRED, "登录已失效，请重新登录");
        }
        UserContext.set(new UserContext.CurrentUser(
                parsed.userId(), parsed.role(), parsed.companyId(), parsed.jti(), parsed.expiresAt()));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }
}
