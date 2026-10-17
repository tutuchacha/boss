package com.pinliao.backend.demo;

import com.pinliao.backend.common.api.ApiCodes;
import com.pinliao.backend.common.exception.BusinessException;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 骨架自检样例接口（T0 验收用，不属契约正文；T9 全量联调通过后可移除）。
 *
 * <ul>
 *   <li>GET /api/v1/_demo/ping     → 成功包络</li>
 *   <li>GET /api/v1/_demo/error    → 业务异常（40401，演示 code→HTTP 映射）</li>
 *   <li>GET /api/v1/_demo/validate → 参数校验失败（42201 + errors 数组）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/_demo")
public class DemoController {

    @GetMapping("/ping")
    public Map<String, Object> ping() {
        return Map.of("pong", true, "service", "pinliao-backend");
    }

    @GetMapping("/error")
    public Map<String, Object> error() {
        throw new BusinessException(ApiCodes.NOT_FOUND, "演示业务异常：资源不存在（40401）");
    }

    @GetMapping("/validate")
    public Map<String, Object> validate(
            @RequestParam @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不正确") String phone) {
        return Map.of("phone", phone);
    }
}
