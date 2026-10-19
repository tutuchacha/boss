package com.pinliao.backend.ai.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * AI 基础设施配置绑定（对齐 {@code auth/config/AuthConfig} 的写法）。
 *
 * <p>Step 4 T-AI0：只做配置绑定与连通性自检；索引 / 检索 / 问答在 T-AI1~T-AI2 增量追加。</p>
 */
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfig {
}
