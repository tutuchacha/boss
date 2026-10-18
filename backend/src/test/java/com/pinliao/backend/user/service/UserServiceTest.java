package com.pinliao.backend.user.service;

import com.pinliao.backend.auth.config.AuthProperties;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** T7：用户 / 求职者模块（§2.1~§2.8）单元测试。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private UserProfileMapper userProfileMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private SmsCodeService smsCodeService;
    @Mock
    private AuthTokenStore tokenStore;
    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOps;

    private UserService service;
    private UserEntity user;
    private UserProfileEntity profile;

    @BeforeEach
    void setUp() {
        service = new UserService(userMapper, userProfileMapper, passwordEncoder, smsCodeService, tokenStore, redis);
        when(redis.opsForValue()).thenReturn(valueOps);
        user = new UserEntity();
        user.setId("u-seeker-1");
        user.setPhone("13800000001");
        user.setPasswordHash("stored-hash");
        user.setRole("JOB_SEEKER");
        user.setStatus("ACTIVE");
        user.setCreatedAt(java.time.LocalDateTime.now());
        user.setUpdatedAt(java.time.LocalDateTime.now());

        profile = new UserProfileEntity();
        profile.setId("up-seeker-1");
        profile.setUserId("u-seeker-1");
        profile.setName("李小明");
        profile.setSeekingStatus("ACTIVE");
        profile.setExpectSalaryMin(20);
        profile.setExpectSalaryMax(40);
        profile.setUpdatedAt(java.time.LocalDateTime.now());

        when(userMapper.selectById("u-seeker-1")).thenReturn(user);
        when(userProfileMapper.selectOne(any())).thenReturn(profile);
        UserContext.set(new UserContext.CurrentUser("u-seeker-1", "JOB_SEEKER", null,
                "jti-1", Instant.now().plus(Duration.ofHours(2))));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void profileReturnsSeekerProfile() {
        AuthViews.UserProfile view = service.profile();
        assertEquals("李小明", view.name());
        assertEquals("u-seeker-1", view.userId());
    }

    @Test
    void profileMissingReturns40410() {
        when(userProfileMapper.selectOne(any())).thenReturn(null);
        BusinessException e = assertThrows(BusinessException.class, () -> service.profile());
        assertEquals(UserCodes.NOT_FOUND, e.getCode());
    }

    @Test
    void updateProfileMergesPartialFieldsAndChecksSalaryRange() {
        UserRequests.ProfilePatch patch = new UserRequests.ProfilePatch(
                "新名字", null, null, null, null, null, null, null, null, null, 45, null);
        AuthViews.UserProfile view = service.updateProfile(patch);
        assertEquals("新名字", view.name());
        assertEquals(45, view.expectSalaryMax());
        verify(userProfileMapper).updateById(profile);
    }

    @Test
    void updateProfileRejectsSalaryMinGreaterThanMax() {
        UserRequests.ProfilePatch patch = new UserRequests.ProfilePatch(
                null, null, null, null, null, null, null, null, null, null, 10, null);
        BusinessException e = assertThrows(BusinessException.class, () -> service.updateProfile(patch));
        assertEquals(UserCodes.INVALID, e.getCode());
        verify(userProfileMapper, never()).updateById(any(UserProfileEntity.class));
    }

    @Test
    void updateProfileRejectsFutureBirthDate() {
        UserRequests.ProfilePatch patch = new UserRequests.ProfilePatch(
                null, null, null, LocalDate.now().plusDays(1), null, null, null, null, null, null, null, null);
        BusinessException e = assertThrows(BusinessException.class, () -> service.updateProfile(patch));
        assertEquals(UserCodes.INVALID, e.getCode());
    }

    @Test
    void changePasswordRejectsWrongOldPassword() {
        when(passwordEncoder.matches("bad", "stored-hash")).thenReturn(false);
        BusinessException e = assertThrows(BusinessException.class, () -> service.changePassword(
                new UserRequests.ChangePasswordRequest("bad", "newpass")));
        assertEquals(UserCodes.INVALID, e.getCode());
        verify(userMapper, never()).updateById(any(UserEntity.class));
    }

    @Test
    void changePasswordUpdatesHash() {
        when(passwordEncoder.matches("oldpass", "stored-hash")).thenReturn(true);
        when(passwordEncoder.encode("newpass")).thenReturn("new-hash");
        service.changePassword(new UserRequests.ChangePasswordRequest("oldpass", "newpass"));
        assertEquals("new-hash", user.getPasswordHash());
        verify(userMapper).updateById(user);
    }

    @Test
    void resetPasswordVerifiesResetScene() {
        when(userMapper.selectList(any())).thenReturn(List.of(user));
        when(passwordEncoder.encode("newpass")).thenReturn("reset-hash");
        service.resetPassword(new UserRequests.ResetPasswordRequest("13800000001", "123456", "newpass"));
        verify(smsCodeService).verify("13800000001", "RESET", "123456");
        assertEquals("reset-hash", user.getPasswordHash());
    }

    @Test
    void resetPasswordUnknownPhoneReturns40110WithoutConsumingCode() {
        when(userMapper.selectList(any())).thenReturn(List.of());
        BusinessException e = assertThrows(BusinessException.class, () -> service.resetPassword(
                new UserRequests.ResetPasswordRequest("13900000000", "123456", "newpass")));
        assertEquals(ApiCodes.LOGIN_FAILED, e.getCode());
        verify(smsCodeService, never()).verify(anyString(), anyString(), anyString());
    }

    @Test
    void changePhoneRejectsPhoneUsedByAnotherUser() {
        UserEntity other = new UserEntity();
        other.setId("u-other");
        other.setPhone("13900000000");
        when(userMapper.selectList(any())).thenReturn(List.of(other));
        BusinessException e = assertThrows(BusinessException.class, () -> service.changePhone(
                new UserRequests.ChangePhoneRequest("13900000000", "123456")));
        assertEquals(UserCodes.PHONE_TAKEN, e.getCode());
        verify(smsCodeService, never()).verify(anyString(), anyString(), anyString());
    }

    @Test
    void changePhoneVerifiesLoginSceneAndUpdatesPhone() {
        when(userMapper.selectList(any())).thenReturn(List.of());
        service.changePhone(new UserRequests.ChangePhoneRequest("13900000000", "123456"));
        verify(smsCodeService).verify("13900000000", "LOGIN", "123456");
        assertEquals("13900000000", user.getPhone());
        verify(userMapper).updateById(user);
    }

    @Test
    void preferencesDefaultAndUpdate() {
        when(valueOps.get("pinliao:user:prefs:u-seeker-1")).thenReturn(null);
        UserViews.Preferences value = service.preferences();
        assertEquals("auto", value.theme());

        UserViews.Preferences updated = service.updatePreferences(new UserRequests.PreferencesPatch("dark"));
        assertEquals("dark", updated.theme());
        verify(valueOps).set(eq("pinliao:user:prefs:u-seeker-1"), anyString());
    }

    @Test
    void deactivateSoftDeletesAndRevokesCurrentAccess() {
        service.deactivate(UserContext.require(), new UserRequests.DeactivateRequest("不想用了"));
        assertEquals("DELETED", user.getStatus());
        verify(userMapper).updateById(user);
        verify(tokenStore).revokeAccess("jti-1", UserContext.require().expiresAt());
    }
}
