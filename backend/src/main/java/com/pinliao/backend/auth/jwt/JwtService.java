package com.pinliao.backend.auth.jwt;

import com.pinliao.backend.auth.config.AuthProperties;
import com.pinliao.backend.common.api.ApiCodes;
import com.pinliao.backend.common.exception.BusinessException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 签发与校验（HS256，契约 §2.1）。
 *
 * <p>accessToken 声明：{@code sub}=userId、{@code role}、{@code companyId}（可空）、{@code jti}（退出登录吊销用）。</p>
 */
@Component
public class JwtService {

    private final SecretKey key;
    private final AuthProperties props;

    public JwtService(AuthProperties props) {
        this.props = props;
        this.key = Keys.hmacShaKeyFor(props.secret().getBytes(StandardCharsets.UTF_8));
    }

    /** 签发 accessToken（TTL = pinliao.auth.access-ttl）。 */
    public String createAccessToken(String userId, String role, String companyId) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId)
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(props.accessTtl())))
                .signWith(key);
        if (companyId != null) {
            builder.claim("companyId", companyId);
        }
        return builder.compact();
    }

    /**
     * 校验并解析 accessToken。
     *
     * @throws BusinessException 40102 已过期；40101 非法（签名 / 格式错误）
     */
    public ParsedAccessToken parse(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
            return new ParsedAccessToken(
                    claims.getSubject(),
                    claims.get("role", String.class),
                    claims.get("companyId", String.class),
                    claims.getId(),
                    claims.getExpiration().toInstant());
        } catch (ExpiredJwtException e) {
            throw new BusinessException(ApiCodes.TOKEN_EXPIRED, "登录已过期，请重新登录");
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ApiCodes.UNAUTHORIZED, "登录凭证无效，请重新登录");
        }
    }

    /** 解析结果（由拦截器写入 {@code UserContext}）。 */
    public record ParsedAccessToken(String userId, String role, String companyId, String jti, Instant expiresAt) {
    }
}
