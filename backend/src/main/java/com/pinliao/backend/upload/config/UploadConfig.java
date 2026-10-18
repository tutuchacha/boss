package com.pinliao.backend.upload.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 上传目录静态映射：{@code /uploads/**} → 本地对象目录。
 *
 * <p>静态 URL 不在 {@code /api/v1/**} 下，因此不经过登录态拦截器，可用于头像 / Logo 等
 * 需要直接展示的地址；上传写入本身仍使用带签名与有效期的
 * {@code PUT /api/v1/uploads/local/{objectKey}}。</p>
 */
@Configuration
@EnableConfigurationProperties(UploadProperties.class)
public class UploadConfig implements WebMvcConfigurer {

    private final UploadProperties properties;

    public UploadConfig(UploadProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path root = Paths.get(properties.dir()).toAbsolutePath().normalize();
        String location = root.toUri().toString();
        if (!location.endsWith("/")) {
            location += "/";
        }
        registry.addResourceHandler("/uploads/**").addResourceLocations(location);
    }
}
