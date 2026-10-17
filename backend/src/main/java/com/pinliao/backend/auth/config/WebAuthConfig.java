package com.pinliao.backend.auth.config;

import com.pinliao.backend.auth.web.AuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 登录态拦截器注册（契约 §2.2）。
 *
 * <p>默认保护全部 {@code /api/v1/**}；白名单 = 登录、验证码、刷新、注册、骨架样例接口。
 * 后续模块新增接口无需再挂心鉴权，未登录会统一返回 40101。</p>
 */
@Configuration
public class WebAuthConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    public WebAuthConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/v1/**")
                .excludePathPatterns(
                        "/api/v1/auth/sms/**",
                        "/api/v1/auth/login/**",
                        "/api/v1/auth/refresh",
                        "/api/v1/auth/register",
                        "/api/v1/_demo/**");
    }
}
