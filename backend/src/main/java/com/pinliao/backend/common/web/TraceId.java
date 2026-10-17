package com.pinliao.backend.common.web;

import org.slf4j.MDC;

/** traceId 读写（MDC key 见 {@link #MDC_KEY}，由 {@link TraceIdFilter} 写入）。 */
public final class TraceId {

    public static final String MDC_KEY = "traceId";
    public static final String HEADER = "X-Trace-Id";

    private TraceId() {
    }

    /** 当前请求上下文的 traceId；不在请求线程内时为 null。 */
    public static String current() {
        return MDC.get(MDC_KEY);
    }
}
