package com.pinliao.backend.auth.config;

import com.pinliao.backend.auth.web.AuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 登录态拦截器注册（契约 §2.2）。
 *
 * <p>默认保护全部 {@code /api/v1/**}；白名单 = 登录、验证码、刷新、注册、骨架样例接口。
 * 公开只读接口（公司详情/公司职位 §5.1~§5.2、职位浏览 §6.1~§6.4、§6.9 埋点、分类字典 §7）不在此排除——由
 * {@link AuthInterceptor} 内部按「方法 + 路径」携带 token 可选放行（匿名也走同一拦截器）。</p>
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
                        "/api/v1/user/password/reset",
                        "/api/v1/uploads/local/**",
                        "/api/v1/_demo/**");
    }
}
