package com.pinliao.backend.common.exception;

import com.pinliao.backend.common.api.ApiCodes;
import com.pinliao.backend.common.api.ApiResponse;
import com.pinliao.backend.common.api.ApiResponse.FieldErrorItem;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/** 全局异常 → 统一包络（契约 §4.3 / §5）。HTTP 状态码由业务码前 3 位推导。 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务异常：按业务码映射 HTTP 状态码。 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        log.warn("业务异常 code={} message={}", e.getCode(), e.getMessage());
        return build(e.getCode(), e.getMessage(), null);
    }

    /** @RequestBody 字段校验失败（42201 + errors 数组）。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleBodyValidation(MethodArgumentNotValidException e) {
        List<FieldErrorItem> errors = e.getBindingResult().getFieldErrors().stream()
                .map(f -> new FieldErrorItem(f.getField(), f.getDefaultMessage()))
                .toList();
        return build(ApiCodes.FIELD_VALIDATION, "字段校验失败", errors);
    }

    /** 控制器方法参数（query / path）校验失败（Spring 6.1+ 内建校验）。 */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodValidation(HandlerMethodValidationException e) {
        List<FieldErrorItem> errors = e.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(err -> new FieldErrorItem(result.getMethodParameter().getParameterName(), err.getDefaultMessage())))
                .toList();
        return build(ApiCodes.FIELD_VALIDATION, "字段校验失败", errors);
    }

    /** 服务/其它层 @Validated 约束校验失败。 */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException e) {
        List<FieldErrorItem> errors = e.getConstraintViolations().stream()
                .map(v -> new FieldErrorItem(lastPathNode(v.getPropertyPath().toString()), v.getMessage()))
                .toList();
        return build(ApiCodes.FIELD_VALIDATION, "字段校验失败", errors);
    }

    /** 请求体不可读（JSON 语法错误等）。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException e) {
        return build(ApiCodes.PARAM_INVALID, "请求体格式错误", null);
    }

    /** 缺少必填 query 参数 / 参数类型不匹配。 */
    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResponse<Void>> handleParamMiss(Exception e) {
        return build(ApiCodes.PARAM_INVALID, "请求参数错误", null);
    }

    /** 路径不存在的静态资源 / 未匹配路由。 */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException e) {
        return build(ApiCodes.NOT_FOUND, "资源不存在", null);
    }

    /** 兜底：未预期异常（记录堆栈，返回 50001）。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        log.error("未处理异常", e);
        return build(ApiCodes.INTERNAL_ERROR, "服务器内部错误", null);
    }

    private static ResponseEntity<ApiResponse<Void>> build(int code, String message, List<FieldErrorItem> errors) {
        return ResponseEntity.status(ApiCodes.httpStatus(code)).body(ApiResponse.fail(code, message, errors));
    }

    /** 取属性路径最后一段（如 "demoController.validate.phone" → "phone"）。 */
    private static String lastPathNode(String path) {
        int idx = path.lastIndexOf('.');
        return idx >= 0 ? path.substring(idx + 1) : path;
    }
}
