package com.pinliao.backend.auth;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 种子密码 hash 回归测试。
 *
 * <p>{@link #SEED_HASH} 必须与 {@code db/migration/V2__seed_auth_demo.sql} 中的 hash 一致；
 * 改动种子密码时两处同步修改。</p>
 * <p>如需重新生成：临时加 {@code System.out.println(new BCryptPasswordEncoder().encode("123456"))} 跑一次本测试即可。</p>
 */
class PasswordHashTest {

    /** 演示账号密码 123456 的 BCrypt 哈希。 */
    private static final String SEED_HASH = "$2a$10$.az91kSHYTOcUZerCHPwoe0jN3eUX8oQ.25Hps9C2jX0bZ1/8tr4m";

    @Test
    void 种子密码hash_可匹配演示密码() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        assertTrue(encoder.matches("123456", SEED_HASH), "V2 种子 hash 必须能匹配演示密码 123456");
        assertFalse(encoder.matches("000000", SEED_HASH));
    }
}
