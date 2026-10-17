package com.pinliao.backend.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.pinliao.backend.common.web.TraceId;

import java.util.List;

/**
 * 统一响应包络（契约 §4.1）：{@code {code, message, data, traceId}}；失败时可选 {@code errors}（§4.3）。
 *
 * <p>成功：{@link #ok(Object)}；失败：{@link #fail(int, String)} / {@link #fail(int, String, List)}。
 * traceId 由 {@code TraceIdFilter} 写入 MDC，此处读取装配。</p>
 */
public record ApiResponse<T>(
        int code,
        String message,
        T data,
        String traceId,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<FieldErrorItem> errors) {

    /** 字段级错误项（契约 §4.3 的 errors 数组元素）。 */
    public record FieldErrorItem(String field, String message) {
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(ApiCodes.OK, ApiCodes.MSG_OK, data, TraceId.current(), null);
    }

    public static ApiResponse<Void> ok() {
        return ok(null);
    }

    public static <T> ApiResponse<T> fail(int code, String message) {
        return new ApiResponse<>(code, message, null, TraceId.current(), null);
    }

    public static <T> ApiResponse<T> fail(int code, String message, List<FieldErrorItem> errors) {
        return new ApiResponse<>(code, message, null, TraceId.current(), errors);
    }
}
