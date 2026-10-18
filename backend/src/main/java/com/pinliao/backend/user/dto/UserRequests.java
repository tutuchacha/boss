package com.pinliao.backend.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** 用户 / 求职者模块请求体（契约 §2.1~§2.8）。 */
public final class UserRequests {

    private UserRequests() {
    }

    /**
     * §2.2 更新求职者画像（部分字段；null = 不修改）。
     *
     * <p>服务端会把 birthDate、薪资区间等联动字段与当前画像合并后再校验；
     * 字符串字段统一做长度与枚举校验，错误返回 42201。</p>
     */
    public record ProfilePatch(
            @Pattern(regexp = ".*\\S.*", message = "姓名不能为空")
            @Size(max = 64, message = "姓名不能超过 64 个字符")
            String name,
            @Size(max = 512, message = "头像地址不能超过 512 个字符")
            String avatarUrl,
            @Pattern(regexp = "male|female|other", message = "gender 只能是 male / female / other")
            String gender,
            LocalDate birthDate,
            @Min(value = 0, message = "工作年限不能小于 0")
            @Max(value = 60, message = "工作年限不能超过 60")
            Integer workYears,
            @Size(max = 32, message = "学历不能超过 32 个字符")
            String edu,
            @Size(max = 64, message = "期望一级分类不能超过 64 个字符")
            String expectCat,
            @Size(max = 64, message = "期望二级分类不能超过 64 个字符")
            String expectSub,
            @Size(max = 64, message = "期望城市不能超过 64 个字符")
            String expectCity,
            @Min(value = 1, message = "期望薪资下限必须大于 0")
            @Max(value = 1000, message = "期望薪资上限不能超过 1000K")
            Integer expectSalaryMin,
            @Min(value = 1, message = "期望薪资上限必须大于 0")
            @Max(value = 1000, message = "期望薪资上限不能超过 1000K")
            Integer expectSalaryMax,
            @Pattern(regexp = "ACTIVE|PASSIVE|NOT_LOOKING",
                    message = "seekingStatus 只能是 ACTIVE / PASSIVE / NOT_LOOKING")
            String seekingStatus) {
    }

    /** §2.3 修改密码。 */
    public record ChangePasswordRequest(
            @NotBlank(message = "原密码不能为空")
            String oldPassword,
            @NotBlank(message = "新密码不能为空")
            @Size(min = 6, max = 64, message = "密码长度需为 6~64 位")
            String newPassword) {
    }

    /** §2.4 重置密码（未登录；验证码场景 RESET）。 */
    public record ResetPasswordRequest(
            @NotBlank(message = "手机号不能为空")
            @Pattern(regexp = "^1\\d{10}$", message = "请输入正确的手机号")
            String phone,
            @NotBlank(message = "验证码不能为空")
            String code,
            @NotBlank(message = "新密码不能为空")
            @Size(min = 6, max = 64, message = "密码长度需为 6~64 位")
            String newPassword) {
    }

    /**
     * §2.5 修改手机号（验证新号）。
     *
     * <p>验证码场景使用 {@code LOGIN}：新号码可能尚未注册，无法使用 REGISTER / RESET 场景。</p>
     */
    public record ChangePhoneRequest(
            @NotBlank(message = "新手机号不能为空")
            @Pattern(regexp = "^1\\d{10}$", message = "请输入正确的手机号")
            String newPhone,
            @NotBlank(message = "验证码不能为空")
            String code) {
    }

    /** §2.7 更新外观偏好（当前仅 theme；null = 不修改）。 */
    public record PreferencesPatch(
            @Pattern(regexp = "auto|light|dark", message = "theme 只能是 auto / light / dark")
            String theme) {
    }

    /** §2.8 注销账号（reason 仅保留契约字段，演示环境不单独落表）。 */
    public record DeactivateRequest(
            @Size(max = 200, message = "注销原因不能超过 200 个字符")
            String reason) {
    }
}
