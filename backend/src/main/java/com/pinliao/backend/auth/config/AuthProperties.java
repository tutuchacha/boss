package com.pinliao.backend.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 鉴权配置（application.yml 的 pinliao.auth.*）。
 *
 * @param secret     HS256 签名密钥（≥32 字节；生产用环境变量 PINLIAO_AUTH_SECRET 覆盖）
 * @param accessTtl  accessToken 有效期（契约 §2.1：2h）
 * @param refreshTtl refreshToken 有效期（契约 §2.1：30d）
 * @param sms        短信验证码（演示语义：固定 123456、5 分钟有效、60s 限发一次）
 */
@ConfigurationProperties(prefix = "pinliao.auth")
public record AuthProperties(
        String secret,
        Duration accessTtl,
        Duration refreshTtl,
        Sms sms) {

    /** 短信验证码配置。 */
    public record Sms(String fixedCode, Duration ttl, Duration cooldown) {
    }
}
