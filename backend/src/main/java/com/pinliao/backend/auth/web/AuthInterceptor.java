package com.pinliao.backend.auth.web;

import com.pinliao.backend.auth.context.UserContext;
import com.pinliao.backend.auth.jwt.JwtService;
import com.pinliao.backend.auth.token.AuthTokenStore;
import com.pinliao.backend.common.api.ApiCodes;
import com.pinliao.backend.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录态拦截器：解析 {@code Authorization: Bearer <accessToken>} 并写入 {@link UserContext}。
 *
 * <p>错误码：缺少 / 非法 token → 40101；已过期 → 40102；已退出（jti 吊销名单）→ 40102。</p>
 *
 * <p><b>公开接口</b>（契约 §6.1~§6.4、§6.9、§7）：未携带 token 时放行（UserContext 保持空）；
 * 携带 token 时按正常流程解析——求职者浏览职位时 recommend 排序要用期望岗位加权。
 * 白名单按「HTTP 方法 + 路径」精确到接口，例如 POST {@code /api/v1/jobs}（发布职位）仍需鉴权。</p>
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final JwtService jwtService;
    private final AuthTokenStore tokenStore;

    public AuthInterceptor(JwtService jwtService, AuthTokenStore tokenStore) {
        this.jwtService = jwtService;
        this.tokenStore = tokenStore;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String header = request.getHeader("Authorization");
        String token = header != null && header.startsWith(BEARER_PREFIX)
                ? header.substring(BEARER_PREFIX.length()).trim()
                : "";
        if (token.isEmpty()) {
            if (isPublicEndpoint(request.getMethod(), request.getRequestURI())) {
                return true;
            }
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

    /** 公开接口判定（契约 §6.1~§6.4、§6.9、§7.1~§7.3；包级可见便于单测）。 */
    static boolean isPublicEndpoint(String method, String path) {
        if (method == null || path == null) {
            return false;
        }
        return switch (method) {
            case "GET" -> matchesAny(path,
                    "/api/v1/jobs",
                    "/api/v1/jobs/*",
                    "/api/v1/jobs/*/similar",
                    "/api/v1/search/jobs",
                    "/api/v1/categories",
                    "/api/v1/cities",
                    "/api/v1/dict");
            case "POST" -> matchesAny(path, "/api/v1/jobs/*/view");
            default -> false;
        };
    }

    private static boolean matchesAny(String path, String... patterns) {
        for (String pattern : patterns) {
            if (PATH_MATCHER.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }
}
