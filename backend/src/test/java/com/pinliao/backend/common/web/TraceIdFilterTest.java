package com.pinliao.backend.common.web;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Scope;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T-OBS2：traceId 解析优先级（OTel Span → 透传请求头 → UUID 兜底）。
 *
 * <p>不依赖 Agent、不联网：用一个手工构造的「远程父 Span 上下文」模拟挂了 OTel Agent 的场景。</p>
 */
class TraceIdFilterTest {

    /** 一个合法的 W3C traceId（32 位小写 hex），等价于请求头 traceparent 里携带的 trace 标识 */
    private static final String OTEL_TRACE_ID = "0af7651916cd43dd8448eb211c80319c";
    private static final String OTEL_SPAN_ID = "b7ad6b7169203331";
    private static final String HEX32 = "[0-9a-f]{32}";

    /** 模拟「挂了 OTel Agent」：把远程父上下文设为当前上下文 */
    private static <T> T withActiveOtelSpan(java.util.function.Supplier<T> action) {
        SpanContext context = SpanContext.create(OTEL_TRACE_ID, OTEL_SPAN_ID,
                TraceFlags.getSampled(), TraceState.getDefault());
        try (Scope ignored = Span.wrap(context).makeCurrent()) {
            return action.get();
        }
    }

    @Test
    void 有活跃Span时用Span的traceId_压过请求头() {
        String resolved = withActiveOtelSpan(() -> TraceIdFilter.resolveTraceId("deadbeefdeadbeefdeadbeefdeadbeef"));

        assertEquals(OTEL_TRACE_ID, resolved);
    }

    @Test
    void 无Span时透传请求头_保持既有行为() {
        assertEquals("abc123", TraceIdFilter.resolveTraceId("abc123"));
    }

    @Test
    void 无Span且无请求头时_生成32位小写hex() {
        String resolved = TraceIdFilter.resolveTraceId(null);

        assertTrue(resolved.matches(HEX32), "应为 32 位小写 hex，实际：" + resolved);
        assertEquals(32, resolved.length());
    }

    @Test
    void 请求头超长视为非法_重新生成() {
        String tooLong = "t".repeat(65);

        String resolved = TraceIdFilter.resolveTraceId(tooLong);

        assertTrue(resolved.matches(HEX32), "超长请求头应被丢弃并重新生成，实际：" + resolved);
    }

    @Test
    void 请求头为空白时_重新生成() {
        assertTrue(TraceIdFilter.resolveTraceId("   ").matches(HEX32));
    }

    @Test
    void 过滤器把traceId同时写入MDC与响应头_并在结束后清理MDC() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/cities");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> inChain = new AtomicReference<>();

        new TraceIdFilter().doFilter(request, response, (req, res) -> inChain.set(TraceId.current()));

        assertEquals(inChain.get(), response.getHeader(TraceId.HEADER));
        assertTrue(inChain.get().matches(HEX32));
        assertNull(TraceId.current(), "请求结束后必须清理 MDC，避免线程复用串号");
    }
}
