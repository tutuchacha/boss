package com.pinliao.backend.ai.qa;

import com.pinliao.backend.ai.AiCodes;
import com.pinliao.backend.ai.config.AiProperties;
import com.pinliao.backend.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T-AI2：AI 问答配额（Redis 计数 / 42902 / 故障降级）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiQuotaServiceTest {

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOps;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void 首次计数设置过期_超限返回42902() {
        when(valueOps.increment(anyString())).thenReturn(1L, 101L);

        AiQuotaService service = service(100);
        assertDoesNotThrow(() -> service.consume("u-hr-1"));
        verify(redis).expire(anyString(), org.mockito.ArgumentMatchers.any());

        BusinessException error = assertThrows(BusinessException.class, () -> service.consume("u-hr-1"));
        assertEquals(AiCodes.QUOTA_EXCEEDED, error.getCode());
    }

    @Test
    void 关闭配额时不访问Redis() {
        service(0).consume("u-hr-1");

        org.mockito.Mockito.verifyNoInteractions(redis);
    }

    @Test
    void Redis故障时降级放行() {
        when(valueOps.increment(anyString())).thenThrow(new IllegalStateException("redis down"));

        assertDoesNotThrow(() -> service(100).consume("u-hr-1"));
    }

    private AiQuotaService service(int dailyLimit) {
        return new AiQuotaService(redis,
                new AiProperties(true, AiProperties.PROVIDER_FAKE, 800, 10, 6, 0.2, 700, dailyLimit, 8, 0.15));
    }
}
