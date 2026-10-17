package com.pinliao.backend.auth.token;

import com.pinliao.backend.auth.config.AuthProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * Redis 令牌存储（契约 §2.1：refreshToken 存 Redis，可服务端吊销）。
 *
 * <p>键：{@code pinliao:auth:refresh:{token}} → userId（白名单）；{@code pinliao:auth:at-revoked:{jti}} → 1（accessToken 吊销名单）。</p>
 */
@Component
public class AuthTokenStore {

    private static final String REFRESH_KEY = "pinliao:auth:refresh:%s";
    private static final String REVOKED_JTI_KEY = "pinliao:auth:at-revoked:%s";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;
    private final AuthProperties props;

    public AuthTokenStore(StringRedisTemplate redis, AuthProperties props) {
        this.redis = redis;
        this.props = props;
    }

    /** 签发 refreshToken（不透明随机串，TTL = pinliao.auth.refresh-ttl）。 */
    public String issueRefresh(String userId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        redis.opsForValue().set(REFRESH_KEY.formatted(token), userId, props.refreshTtl());
        return token;
    }

    /** 查询 refreshToken 归属的 userId；不存在 / 已吊销返回 null。 */
    public String resolveRefresh(String token) {
        return redis.opsForValue().get(REFRESH_KEY.formatted(token));
    }

    /** 吊销 refreshToken（退出登录 / 刷新轮换）。 */
    public void revokeRefresh(String token) {
        redis.delete(REFRESH_KEY.formatted(token));
    }

    /** 吊销 accessToken：退出后至其自然过期前不可再用（按剩余有效期设置 TTL）。 */
    public void revokeAccess(String jti, Instant expiresAt) {
        long ttlSeconds = Duration.between(Instant.now(), expiresAt).toSeconds();
        if (ttlSeconds > 0) {
            redis.opsForValue().set(REVOKED_JTI_KEY.formatted(jti), "1", Duration.ofSeconds(ttlSeconds));
        }
    }

    /** accessToken 是否已被吊销。 */
    public boolean isAccessRevoked(String jti) {
        return Boolean.TRUE.equals(redis.hasKey(REVOKED_JTI_KEY.formatted(jti)));
    }
}
