package com.pinliao.backend.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pinliao.backend.auth.context.Roles;
import com.pinliao.backend.auth.context.UserContext;
import com.pinliao.backend.auth.entity.UserEntity;
import com.pinliao.backend.auth.entity.UserProfileEntity;
import com.pinliao.backend.auth.mapper.UserMapper;
import com.pinliao.backend.auth.mapper.UserProfileMapper;
import com.pinliao.backend.auth.service.SmsCodeService;
import com.pinliao.backend.auth.token.AuthTokenStore;
import com.pinliao.backend.auth.view.AuthViews;
import com.pinliao.backend.common.api.ApiCodes;
import com.pinliao.backend.common.exception.BusinessException;
import com.pinliao.backend.user.UserCodes;
import com.pinliao.backend.user.dto.UserRequests;
import com.pinliao.backend.user.view.UserViews;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Set;

/**
 * 用户 / 求职者服务（契约 §2.1~§2.8）。
 *
 * <p><b>校验顺序</b>：修改密码 / 手机号均先校验业务冲突，再校验验证码，最后落库，
 * 避免错误请求消耗一次性验证码，也保证 RootGate 不会在失败前收到登录态变化。</p>
 *
 * <p><b>外观偏好</b>：契约允许前端 localStorage 存储，接口为多端同步预留；
 * 演示环境用 Redis 按 userId 保存 JSON，不新增数据库表。</p>
 */
@Service
public class UserService {

    private static final String PREF_KEY = "pinliao:user:prefs:%s";
    private static final String DEFAULT_THEME = "auto";
    private static final Set<String> GENDERS = Set.of("male", "female", "other");
    private static final Set<String> SEEKING_STATUSES = Set.of("ACTIVE", "PASSIVE", "NOT_LOOKING");
    private static final Set<String> THEMES = Set.of("auto", "light", "dark");
    private static final ObjectMapper JSON = new ObjectMapper();

    private final UserMapper userMapper;
    private final UserProfileMapper userProfileMapper;
    private final PasswordEncoder passwordEncoder;
    private final SmsCodeService smsCodeService;
    private final AuthTokenStore tokenStore;
    private final StringRedisTemplate redis;

    public UserService(UserMapper userMapper,
                       UserProfileMapper userProfileMapper,
                       PasswordEncoder passwordEncoder,
                       SmsCodeService smsCodeService,
                       AuthTokenStore tokenStore,
                       StringRedisTemplate redis) {
        this.userMapper = userMapper;
        this.userProfileMapper = userProfileMapper;
        this.passwordEncoder = passwordEncoder;
        this.smsCodeService = smsCodeService;
        this.tokenStore = tokenStore;
        this.redis = redis;
    }

    // ------------------------------------------------------------------
    // §2.1 / §2.2 求职者画像
    // ------------------------------------------------------------------

    /** §2.1 获取求职者画像（仅 JOB_SEEKER）。 */
    public AuthViews.UserProfile profile() {
        UserContext.CurrentUser user = Roles.requireSeeker();
        return AuthViews.UserProfile.from(requireProfile(user.userId()));
    }

    /** §2.2 部分更新求职者画像；null 字段保持原值。 */
    @Transactional
    public AuthViews.UserProfile updateProfile(UserRequests.ProfilePatch patch) {
        UserContext.CurrentUser user = Roles.requireSeeker();
        UserProfileEntity profile = requireProfile(user.userId());

        if (patch.name() != null) {
            profile.setName(patch.name().trim());
        }
        if (patch.avatarUrl() != null) {
            profile.setAvatarUrl(blankToNull(patch.avatarUrl()));
        }
        if (patch.gender() != null) {
            profile.setGender(patch.gender());
        }
        if (patch.birthDate() != null) {
            if (patch.birthDate().isAfter(LocalDate.now(ZoneOffset.UTC))) {
                throw invalid("出生日期不能晚于今天");
            }
            profile.setBirthDate(patch.birthDate());
        }
        if (patch.workYears() != null) {
            profile.setWorkYears(patch.workYears());
        }
        if (patch.edu() != null) {
            profile.setEdu(blankToNull(patch.edu()));
        }
        if (patch.expectCat() != null) {
            profile.setExpectCat(blankToNull(patch.expectCat()));
        }
        if (patch.expectSub() != null) {
            profile.setExpectSub(blankToNull(patch.expectSub()));
        }
        if (patch.expectCity() != null) {
            profile.setExpectCity(blankToNull(patch.expectCity()));
        }
        if (patch.expectSalaryMin() != null) {
            profile.setExpectSalaryMin(patch.expectSalaryMin());
        }
        if (patch.expectSalaryMax() != null) {
            profile.setExpectSalaryMax(patch.expectSalaryMax());
        }
        if (patch.seekingStatus() != null) {
            if (!SEEKING_STATUSES.contains(patch.seekingStatus())) {
                throw invalid("求职状态不合法");
            }
            profile.setSeekingStatus(patch.seekingStatus());
        }

        Integer min = profile.getExpectSalaryMin();
        Integer max = profile.getExpectSalaryMax();
        if (min != null && max != null && min > max) {
            throw invalid("期望薪资下限不能大于上限");
        }
        if (profile.getGender() != null && !GENDERS.contains(profile.getGender())) {
            throw invalid("性别不合法");
        }

        profile.setUpdatedAt(now());
        userProfileMapper.updateById(profile);
        return AuthViews.UserProfile.from(profile);
    }

    // ------------------------------------------------------------------
    // §2.3 / §2.4 密码
    // ------------------------------------------------------------------

    /** §2.3 修改密码（登录态；原密码必须正确）。 */
    @Transactional
    public void changePassword(UserRequests.ChangePasswordRequest req) {
        UserContext.CurrentUser current = UserContext.require();
        UserEntity user = requireUser(current.userId());
        if (user.getPasswordHash() == null || !passwordEncoder.matches(req.oldPassword(), user.getPasswordHash())) {
            throw invalid("原密码不正确");
        }
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        user.setUpdatedAt(now());
        userMapper.updateById(user);
    }

    /** §2.4 重置密码（未登录；验证码场景 RESET）。 */
    @Transactional
    public void resetPassword(UserRequests.ResetPasswordRequest req) {
        String phone = req.phone().trim();
        UserEntity user = findActiveByPhone(phone);
        if (user == null) {
            throw new BusinessException(ApiCodes.LOGIN_FAILED, "手机号或验证码错误");
        }
        smsCodeService.verify(phone, "RESET", req.code());
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        user.setUpdatedAt(now());
        userMapper.updateById(user);
    }

    // ------------------------------------------------------------------
    // §2.5 手机号
    // ------------------------------------------------------------------

    /** §2.5 修改手机号（验证新号；验证码场景 LOGIN）。 */
    @Transactional
    public void changePhone(UserRequests.ChangePhoneRequest req) {
        UserContext.CurrentUser current = UserContext.require();
        UserEntity user = requireUser(current.userId());
        String newPhone = req.newPhone().trim();
        UserEntity owner = findActiveByPhone(newPhone);
        if (owner != null && !owner.getId().equals(user.getId())) {
            throw new BusinessException(UserCodes.PHONE_TAKEN, "该手机号已被使用");
        }
        smsCodeService.verify(newPhone, "LOGIN", req.code());
        user.setPhone(newPhone);
        user.setUpdatedAt(now());
        userMapper.updateById(user);
    }

    // ------------------------------------------------------------------
    // §2.6 / §2.7 外观偏好
    // ------------------------------------------------------------------

    /** §2.6 获取外观偏好（无记录时返回默认 auto）。 */
    public UserViews.Preferences preferences() {
        UserContext.CurrentUser current = UserContext.require();
        String raw = redis.opsForValue().get(prefKey(current.userId()));
        if (raw == null || raw.isBlank()) {
            return new UserViews.Preferences(DEFAULT_THEME);
        }
        try {
            UserViews.Preferences stored = JSON.readValue(raw, UserViews.Preferences.class);
            String theme = stored.theme();
            return new UserViews.Preferences(theme != null && THEMES.contains(theme) ? theme : DEFAULT_THEME);
        } catch (Exception ignored) {
            // 演示环境容错：Redis 中脏数据按默认值处理，避免整个客户端无法启动
            return new UserViews.Preferences(DEFAULT_THEME);
        }
    }

    /** §2.7 部分更新外观偏好（当前仅 theme）。 */
    public UserViews.Preferences updatePreferences(UserRequests.PreferencesPatch patch) {
        UserContext.CurrentUser current = UserContext.require();
        UserViews.Preferences currentValue = preferences();
        String theme = patch.theme() == null ? currentValue.theme() : patch.theme();
        if (!THEMES.contains(theme)) {
            throw invalid("theme 不合法");
        }
        try {
            redis.opsForValue().set(prefKey(current.userId()), JSON.writeValueAsString(new UserViews.Preferences(theme)));
        } catch (Exception e) {
            throw new IllegalStateException("外观偏好保存失败", e);
        }
        return new UserViews.Preferences(theme);
    }

    // ------------------------------------------------------------------
    // §2.8 注销
    // ------------------------------------------------------------------

    /** §2.8 注销账号：软删账号并吊销当前 accessToken（30 天内可由运维恢复）。 */
    @Transactional
    public void deactivate(UserContext.CurrentUser current, UserRequests.DeactivateRequest req) {
        UserEntity user = requireUser(current.userId());
        user.setStatus("DELETED");
        user.setDeletedAt(now());
        user.setUpdatedAt(now());
        userMapper.updateById(user);
        tokenStore.revokeAccess(current.jti(), current.expiresAt());
    }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    private UserProfileEntity requireProfile(String userId) {
        UserProfileEntity profile = userProfileMapper.selectOne(Wrappers.<UserProfileEntity>lambdaQuery()
                .eq(UserProfileEntity::getUserId, userId)
                .last("LIMIT 1"));
        if (profile == null) {
            throw new BusinessException(UserCodes.NOT_FOUND, "求职者画像不存在");
        }
        return profile;
    }

    private UserEntity requireUser(String userId) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null || user.getDeletedAt() != null) {
            throw new BusinessException(ApiCodes.UNAUTHORIZED, "账号不存在或已被注销");
        }
        return user;
    }

    private UserEntity findActiveByPhone(String phone) {
        return userMapper.selectList(Wrappers.<UserEntity>lambdaQuery()
                        .eq(UserEntity::getPhone, phone)
                        .isNull(UserEntity::getDeletedAt)
                        .last("LIMIT 1"))
                .stream().findFirst().orElse(null);
    }

    private static String prefKey(String userId) {
        return PREF_KEY.formatted(userId);
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(UserCodes.INVALID, message);
    }

    private static String blankToNull(String value) {
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
