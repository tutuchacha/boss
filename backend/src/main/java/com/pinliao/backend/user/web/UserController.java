package com.pinliao.backend.user.web;

import com.pinliao.backend.auth.context.UserContext;
import com.pinliao.backend.auth.view.AuthViews;
import com.pinliao.backend.user.dto.UserRequests;
import com.pinliao.backend.user.service.UserService;
import com.pinliao.backend.user.view.UserViews;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 用户 / 求职者接口（契约 §2.1~§2.8）。
 *
 * <p>§2.4 重置密码由 {@code WebAuthConfig} 排除在登录态拦截器之外；
 * 其余接口需要登录，角色 / 归属在 service 层守卫。</p>
 */
@RestController
@RequestMapping("/api/v1/user")
@Tag(name = "2. 用户/求职者", description = "画像、密码、手机号、外观偏好、注销（契约 §2）")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /** §2.1 获取求职者画像（JOB_SEEKER）。 */
    @GetMapping("/profile")
    public AuthViews.UserProfile profile() {
        return userService.profile();
    }

    /** §2.2 更新求职者画像（JOB_SEEKER；部分字段）。 */
    @PatchMapping("/profile")
    public AuthViews.UserProfile updateProfile(@Valid @RequestBody UserRequests.ProfilePatch body) {
        return userService.updateProfile(body);
    }

    /** §2.3 修改密码（登录态）。 */
    @PostMapping("/password/change")
    public Map<String, Object> changePassword(@Valid @RequestBody UserRequests.ChangePasswordRequest body) {
        userService.changePassword(body);
        return Map.of();
    }

    /** §2.4 重置密码（未登录；验证码场景 RESET）。 */
    @PostMapping("/password/reset")
    public Map<String, Object> resetPassword(@Valid @RequestBody UserRequests.ResetPasswordRequest body) {
        userService.resetPassword(body);
        return Map.of();
    }

    /** §2.5 修改手机号（登录态；验证码场景 LOGIN）。 */
    @PostMapping("/phone/change")
    public Map<String, Object> changePhone(@Valid @RequestBody UserRequests.ChangePhoneRequest body) {
        userService.changePhone(body);
        return Map.of();
    }

    /** §2.6 获取外观偏好。 */
    @GetMapping("/preferences")
    public UserViews.Preferences preferences() {
        return userService.preferences();
    }

    /** §2.7 更新外观偏好。 */
    @PatchMapping("/preferences")
    public UserViews.Preferences updatePreferences(@Valid @RequestBody UserRequests.PreferencesPatch body) {
        return userService.updatePreferences(body);
    }

    /** §2.8 注销账号（软删；当前 accessToken 立即失效）。 */
    @PostMapping("/deactivate")
    public Map<String, Object> deactivate(@RequestBody(required = false) UserRequests.DeactivateRequest body) {
        userService.deactivate(UserContext.require(),
                body == null ? new UserRequests.DeactivateRequest(null) : body);
        return Map.of();
    }
}
