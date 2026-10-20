package com.pinliao.backend.ai.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AI 基础设施配置绑定（对齐 {@code auth/config/AuthConfig} 的写法）。
 *
 * <p>Step 4 T-AI0：只做配置绑定与连通性自检；索引 / 检索 / 问答在 T-AI1~T-AI3 增量追加。</p>
 */
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfig {

    /**
     * SSE 推流线程池（T-AI3）。
     *
     * <p>为什么单独一个池：流式回答会长时间占用线程（等待上游 token），不能和 Tomcat 请求线程
     * 或 Spring 默认 {@code applicationTaskExecutor} 混用，否则慢问答会拖垮其它接口。
     * 单 HR 的并发已被配额与前端「一次只发一问」约束，固定 4 线程足够演示；
     * 上量后改成带界的队列 + 拒绝策略，或迁移到 reactive。</p>
     */
    @Bean(name = "aiStreamExecutor", destroyMethod = "shutdown")
    public ExecutorService aiStreamExecutor() {
        AtomicInteger seq = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "ai-sse-" + seq.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return Executors.newFixedThreadPool(4, factory);
    }
}
