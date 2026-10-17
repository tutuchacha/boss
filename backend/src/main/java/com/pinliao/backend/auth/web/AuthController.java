package com.pinliao.backend.auth.web;

import com.pinliao.backend.auth.context.UserContext;
import com.pinliao.backend.auth.dto.AuthRequests;
import com.pinliao.backend.auth.dto.AuthResponses;
import com.pinliao.backend.auth.service.AuthService;
import com.pinliao.backend.auth.service.SmsCodeService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 鉴权模块接口（契约 docs/api-endpoints.md §1）。
 *
 * <p>响应统一由 ResponseAdvice 包装为 {@code {code, message, data, traceId}}。</p>
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "1. 鉴权模块", description = "验证码 / 登录 / 刷新 / 退出 / 当前用户（契约 §1）")
public class AuthController {

    private final AuthService authService;
    private final SmsCodeService smsCodeService;

    public AuthController(AuthService authService, SmsCodeService smsCodeService) {
        this.authService = authService;
        this.smsCodeService = smsCodeService;
    }

    /** §1.1 发送验证码（无鉴权；60s 限流 42901）。 */
    @PostMapping("/sms/code")
    public AuthResponses.SmsCodeResponse smsCode(@Valid @RequestBody AuthRequests.SmsCodeRequest req) {
        return smsCodeService.send(req.phone().trim(), req.scene());
    }

    /** §1.4 密码登录（无鉴权）。 */
    @PostMapping("/login/password")
    public AuthResponses.AuthResult loginPassword(@Valid @RequestBody AuthRequests.LoginPasswordRequest req) {
        return authService.loginPassword(req);
    }

    /** §1.3 手机号验证码登录（无鉴权）。 */
    @PostMapping("/login/sms")
    public AuthResponses.AuthResult loginSms(@Valid @RequestBody AuthRequests.LoginSmsRequest req) {
        return authService.loginSms(req);
    }

    /** §1.5 刷新 token（无鉴权，用 refreshToken；一次性轮换）。 */
    @PostMapping("/refresh")
    public AuthResponses.RefreshResult refresh(@Valid @RequestBody AuthRequests.RefreshRequest req) {
        return authService.refresh(req);
    }

    /** §1.6 退出登录（需鉴权；吊销 accessToken 与可选 refreshToken）。 */
    @PostMapping("/logout")
    public Map<String, Object> logout(@RequestBody(required = false) AuthRequests.LogoutRequest req) {
        authService.logout(UserContext.require(), req);
        return Map.of();
    }

    /** §1.7 当前用户信息（需鉴权）。 */
    @GetMapping("/me")
    public AuthResponses.MeResponse me() {
        return authService.me(UserContext.require().userId());
    }
}
