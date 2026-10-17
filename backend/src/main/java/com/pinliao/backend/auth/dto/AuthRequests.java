package com.pinliao.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 鉴权模块请求体（契约 §1；字段校验失败返回 42201 + errors 数组）。 */
public final class AuthRequests {

    private AuthRequests() {
    }

    /** §1.1 发送验证码。 */
    public record SmsCodeRequest(
            @NotBlank(message = "手机号不能为空")
            @Pattern(regexp = "^1\\d{10}$", message = "请输入正确的手机号")
            String phone,
            @NotBlank(message = "scene 不能为空")
            @Pattern(regexp = "LOGIN|REGISTER|RESET", message = "scene 只能是 LOGIN / REGISTER / RESET")
            String scene) {
    }

    /** §1.4 密码登录（account 可为手机号或邮箱）。 */
    public record LoginPasswordRequest(
            @NotBlank(message = "账号不能为空")
            String account,
            @NotBlank(message = "密码不能为空")
            String password) {
    }

    /** §1.3 手机号验证码登录。 */
    public record LoginSmsRequest(
            @NotBlank(message = "手机号不能为空")
            @Pattern(regexp = "^1\\d{10}$", message = "请输入正确的手机号")
            String phone,
            @NotBlank(message = "验证码不能为空")
            String code) {
    }

    /** §1.5 刷新 token。 */
    public record RefreshRequest(
            @NotBlank(message = "refreshToken 不能为空")
            String refreshToken) {
    }

    /** §1.6 退出登录（refreshToken 可选：前端可能只带 accessToken 调退出）。 */
    public record LogoutRequest(String refreshToken) {
    }
}
