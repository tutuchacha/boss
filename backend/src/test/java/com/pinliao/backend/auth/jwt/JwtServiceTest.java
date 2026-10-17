package com.pinliao.backend.auth.jwt;

import com.pinliao.backend.auth.config.AuthProperties;
import com.pinliao.backend.common.api.ApiCodes;
import com.pinliao.backend.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** JwtService 单元测试（不依赖 Spring 容器 / 数据库 / Redis）。 */
class JwtServiceTest {

    private static final String SECRET_A = "unit-test-secret-0123456789abcdef-0123456789abcdef";
    private static final String SECRET_B = "another-secret-0123456789abcdef-0123456789abcdef";

    private static JwtService jwt(String secret, Duration accessTtl) {
        return new JwtService(new AuthProperties(secret, accessTtl, Duration.ofDays(30),
                new AuthProperties.Sms("123456", Duration.ofMinutes(5), Duration.ofSeconds(60))));
    }

    @Test
    void 签发与解析_声明完整() {
        JwtService service = jwt(SECRET_A, Duration.ofHours(2));
        String token = service.createAccessToken("u-hr-1", "HR", "co-acgiw7");

        JwtService.ParsedAccessToken parsed = service.parse(token);

        assertEquals("u-hr-1", parsed.userId());
        assertEquals("HR", parsed.role());
        assertEquals("co-acgiw7", parsed.companyId());
        assertNotNull(parsed.jti());
        assertTrue(parsed.expiresAt().isAfter(Instant.now()));
    }

    @Test
    void 求职者token_companyId为空() {
        JwtService service = jwt(SECRET_A, Duration.ofHours(2));
        JwtService.ParsedAccessToken parsed = service.parse(service.createAccessToken("u-seeker-1", "JOB_SEEKER", null));
        assertNull(parsed.companyId());
    }

    @Test
    void 过期token_抛出40102() {
        JwtService service = jwt(SECRET_A, Duration.ofSeconds(-60));
        String token = service.createAccessToken("u-1", "JOB_SEEKER", null);

        BusinessException e = assertThrows(BusinessException.class, () -> service.parse(token));
        assertEquals(ApiCodes.TOKEN_EXPIRED, e.getCode());
    }

    @Test
    void 换密钥后签名不符_抛出40101() {
        String token = jwt(SECRET_A, Duration.ofHours(2)).createAccessToken("u-1", "HR", null);

        BusinessException e = assertThrows(BusinessException.class, () -> jwt(SECRET_B, Duration.ofHours(2)).parse(token));
        assertEquals(ApiCodes.UNAUTHORIZED, e.getCode());
    }
}
