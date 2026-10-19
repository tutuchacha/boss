package com.pinliao.backend.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 总开关（前缀 {@code pinliao.ai}）。
 *
 * <p>provider 侧参数（地址 / 密钥 / 模型 / 向量维度）统一走 {@code spring.ai.openai.*}，
 * 由环境变量注入，详见 {@code application.yml} 与 {@code backend/README.md}「AI 配置」。</p>
 *
 * @param enabled 总开关：false 时 AI 接口返回 42220 且不做任何上游调用（默认 false）
 */
@ConfigurationProperties(prefix = "pinliao.ai")
public record AiProperties(boolean enabled) {
}
