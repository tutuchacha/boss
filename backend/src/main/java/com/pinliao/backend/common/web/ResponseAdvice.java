package com.pinliao.backend.common.web;

import com.pinliao.backend.common.api.ApiResponse;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 统一包络装配：控制器直接返回业务对象时，自动包装为 {@link ApiResponse}。
 *
 * <p>跳过：已包装的 ApiResponse、非 JSON 响应，以及 /actuator、/v3/api-docs、/swagger-ui 等系统端点。</p>
 */
@RestControllerAdvice
public class ResponseAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return !ApiResponse.class.isAssignableFrom(returnType.getParameterType());
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        // 异常处理器直接返回的包络（或其它已在 advice 中包装的响应）不再二次包装
        if (body instanceof ApiResponse<?>) {
            return body;
        }
        String path = request.getURI().getPath();
        if (path.startsWith("/actuator") || path.startsWith("/v3/api-docs") || path.startsWith("/swagger-ui")) {
            return body;
        }
        if (selectedContentType == null || !MediaType.APPLICATION_JSON.isCompatibleWith(selectedContentType)) {
            return body;
        }
        return ApiResponse.ok(body);
    }
}
