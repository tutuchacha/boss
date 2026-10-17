package com.pinliao.backend.auth.service;

import com.pinliao.backend.auth.config.AuthProperties;
import com.pinliao.backend.auth.dto.AuthResponses;
import com.pinliao.backend.common.api.ApiCodes;
import com.pinliao.backend.common.exception.BusinessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * 短信验证码（契约 §1.1）。
 *
 * <p>演示语义（与前端 mock 一致）：验证码固定 {@code 123456}、5 分钟有效、一次性使用；
 * 同手机号 60 秒内只能发送一次（Redis 限流）。</p>
 */
@Service
public class SmsCodeService {

    /** 验证码：scene:phone → code */
    private static final String CODE_KEY = "pinliao:auth:sms:%s:%s";
    /** 限流冷却：phone → 1（TTL = 60s） */
    private static final String COOLDOWN_KEY = "pinliao:auth:sms:cd:%s";

    private final StringRedisTemplate redis;
    private final AuthProperties props;

    public SmsCodeService(StringRedisTemplate redis, AuthProperties props) {
        this.redis = redis;
        this.props = props;
    }

    /** 发送验证码（演示环境不外发短信，直接返回固定码的有效期信息）。 */
    public AuthResponses.SmsCodeResponse send(String phone, String scene) {
        String cooldownKey = COOLDOWN_KEY.formatted(phone);
        Long remain = redis.getExpire(cooldownKey, TimeUnit.SECONDS);
        if (remain != null && remain > 0) {
            throw new BusinessException(ApiCodes.RATE_LIMITED, "发送过于频繁，请 " + remain + " 秒后再试");
        }
        redis.opsForValue().set(CODE_KEY.formatted(scene, phone), props.sms().fixedCode(), props.sms().ttl());
        redis.opsForValue().set(cooldownKey, "1", props.sms().cooldown());
        return new AuthResponses.SmsCodeResponse(Instant.now(), props.sms().cooldown().toSeconds());
    }

    /**
     * 校验并消费验证码（一次性）。
     *
     * @throws BusinessException 40110 验证码错误或已过期（含未发送 / 已使用 / 场景不符）
     */
    public void verify(String phone, String scene, String code) {
        String key = CODE_KEY.formatted(scene, phone);
        String stored = redis.opsForValue().get(key);
        if (stored == null || !stored.equals(code.trim())) {
            throw new BusinessException(ApiCodes.LOGIN_FAILED, "验证码错误或已过期");
        }
        redis.delete(key);
    }
}
