package com.pinliao.backend.common.web;

import io.opentelemetry.api.trace.Span;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 为每个请求准备 traceId：写入 MDC（日志占位）与响应头 {@code X-Trace-Id}；包络中的 traceId 取自 MDC。
 *
 * <p>取值优先级（T-OBS2 可观测性增量）：</p>
 * <ol>
 *   <li><b>当前活跃的 OpenTelemetry Span 的 traceId</b> —— 挂了 OTel / ARMS Agent（或 Micrometer Tracing）
 *       时，让响应包络里的 traceId 与 ARMS 链路详情中的 TraceId <b>完全一致</b>，可直接复制粘贴反查链路。</li>
 *   <li>请求头 {@code X-Trace-Id} —— 未挂 Agent 时的既有透传行为，便于跨系统串联（超长视为非法）。</li>
 *   <li>兜底：随机生成 32 位小写 hex（与改造前完全一致）。</li>
 * </ol>
 *
 * <p>未挂 Agent 时 {@link Span#current()} 返回 invalid span，<b>不会抛异常</b>，故本类对既有行为零影响。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    /** 允许的最大透传长度，超长视为非法直接重生 */
    private static final int MAX_TRACE_ID_LENGTH = 64;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String traceId = resolveTraceId(request.getHeader(TraceId.HEADER));
        MDC.put(TraceId.MDC_KEY, traceId);
        response.setHeader(TraceId.HEADER, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(TraceId.MDC_KEY);
        }
    }

    /**
     * 解析本次请求的 traceId（优先级见类注释）。
     *
     * <p>抽成静态方法，便于单测直接覆盖三个分支而不必启动容器。</p>
     *
     * @param header 请求头 {@code X-Trace-Id} 的原值，可为 null
     */
    static String resolveTraceId(String header) {
        String spanTraceId = currentSpanTraceId();
        if (spanTraceId != null) {
            return spanTraceId;
        }
        if (header != null && !header.isBlank() && header.length() <= MAX_TRACE_ID_LENGTH) {
            return header;
        }
        return UUID.randomUUID().toString().replace("-", "");
    }

    /** 活跃 OTel Span 的 traceId；没有（未挂 Agent / 不在 Span 上下文内）时返回 null。 */
    private static String currentSpanTraceId() {
        Span span = Span.current();
        return span.getSpanContext().isValid() ? span.getSpanContext().getTraceId() : null;
    }
}
