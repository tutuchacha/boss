package com.pinliao.backend.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.pinliao.backend.auth.view.AuthViews;

import java.time.Instant;

/** 鉴权模块响应体（与 src/types/api.ts、mock 数据形状对齐；null 字段不序列化）。 */
public final class AuthResponses {

    private AuthResponses() {
    }

    /** §1.1 发送验证码出参。 */
    public record SmsCodeResponse(Instant sentAt, long nextRetryIn) {
    }

    /**
     * 登录 / 注册出参（契约 AuthResult + 前端 LoginResult 的公司上下文扩展）。
     *
     * <p>{@code profile} 为 {@link AuthViews.UserProfile}（求职者）或 {@link AuthViews.HrProfile}（招聘者）。</p>
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AuthResult(
            String accessToken,
            String refreshToken,
            AuthViews.User user,
            Object profile,
            AuthViews.Company company,
            AuthViews.CompanyMember member) {
    }

    /** §1.5 刷新增发出参。 */
    public record RefreshResult(String accessToken, String refreshToken) {
    }

    /** §1.7 当前用户信息（在契约 {user, profile} 之上补 company / member，与登录响应一致）。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MeResponse(
            AuthViews.User user,
            Object profile,
            AuthViews.Company company,
            AuthViews.CompanyMember member) {
    }
}
