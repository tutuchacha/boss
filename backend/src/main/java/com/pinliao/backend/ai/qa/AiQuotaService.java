package com.pinliao.backend.ai.qa;

import com.pinliao.backend.ai.AiCodes;
import com.pinliao.backend.ai.config.AiProperties;
import com.pinliao.backend.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * AI 问答配额（Step 4 T-AI2）。
 *
 * <p>按「HR / 自然日（UTC）」在 Redis 计数，超过 {@code pinliao.ai.qa-daily-limit} 返回 42902。
 * 问答与语义人才检索共用同一份额度（两者都会调用上游）。
 * 计数在调用上游**之前**自增，失败请求也会计入（防止被失败请求绕过配额）。
 * Redis 不可用时降级放行并打 WARN：AI 是可选能力，不能因缓存故障阻断业务。</p>
 */
@Service
public class AiQuotaService {

    private static final Logger log = LoggerFactory.getLogger(AiQuotaService.class);
    private static final String KEY = "pinliao:ai:qa:%s:%s";
    private static final Duration TTL = Duration.ofDays(2);

    private final StringRedisTemplate redis;
    private final AiProperties properties;

    public AiQuotaService(StringRedisTemplate redis, AiProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    /** 消耗一次配额；超限抛 42902。 */
    public void consume(String userId) {
        int limit = properties.qaDailyLimit();
        if (limit <= 0) {
            return;
        }
        String key = KEY.formatted(userId, LocalDate.now(ZoneOffset.UTC));
        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redis.expire(key, TTL);
            }
            if (count != null && count > limit) {
                throw new BusinessException(AiCodes.QUOTA_EXCEEDED,
                        "今日 AI 调用次数已达上限（" + limit + " 次），请明天再试");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("AI 配额计数失败（已降级放行）：{}", e.toString());
        }
    }
}
