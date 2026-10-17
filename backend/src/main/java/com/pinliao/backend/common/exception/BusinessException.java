package com.pinliao.backend.common.exception;

/** 业务异常：携带业务码（契约 §5），由 {@link GlobalExceptionHandler} 映射为对应 HTTP 状态码与包络。 */
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
