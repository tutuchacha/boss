package com.pinliao.backend.auth;

import com.pinliao.backend.auth.dto.AuthRequests;
import com.pinliao.backend.auth.dto.AuthResponses;
import com.pinliao.backend.auth.entity.CompanyEntity;
import com.pinliao.backend.auth.entity.CompanyMemberEntity;
import com.pinliao.backend.auth.entity.HrProfileEntity;
import com.pinliao.backend.auth.entity.UserEntity;
import com.pinliao.backend.auth.entity.UserProfileEntity;
import com.pinliao.backend.auth.jwt.JwtService;
import com.pinliao.backend.auth.mapper.CompanyMapper;
import com.pinliao.backend.auth.mapper.CompanyMemberMapper;
import com.pinliao.backend.auth.mapper.HrProfileMapper;
import com.pinliao.backend.auth.mapper.UserMapper;
import com.pinliao.backend.auth.mapper.UserProfileMapper;
import com.pinliao.backend.auth.service.AuthService;
import com.pinliao.backend.auth.service.SmsCodeService;
import com.pinliao.backend.auth.token.AuthTokenStore;
import com.pinliao.backend.auth.view.AuthViews;
import com.pinliao.backend.common.api.ApiCodes;
import com.pinliao.backend.common.exception.BusinessException;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * §1.2 注册（T2b）单元测试：校验顺序 / 自动建公司 / 画像与公司落库字段。
 *
 * <p>用 Mockito 假体依赖、不连数据库；端到端验证交给 {@code backend/scripts/smoke-auth.mjs}。</p>
 */
class AuthServiceRegisterTest {

    static {
        // 纯单测环境没有 MyBatis 上下文，手工初始化实体表信息供 LambdaWrapper 解析列名
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, UserEntity.class);
        TableInfoHelper.initTableInfo(assistant, UserProfileEntity.class);
        TableInfoHelper.initTableInfo(assistant, HrProfileEntity.class);
        TableInfoHelper.initTableInfo(assistant, CompanyMemberEntity.class);
    }

    private UserMapper userMapper;
    private UserProfileMapper userProfileMapper;
    private HrProfileMapper hrProfileMapper;
    private CompanyMapper companyMapper;
    private CompanyMemberMapper companyMemberMapper;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private AuthTokenStore tokenStore;
    private SmsCodeService smsCodeService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        userProfileMapper = mock(UserProfileMapper.class);
        hrProfileMapper = mock(HrProfileMapper.class);
        companyMapper = mock(CompanyMapper.class);
        companyMemberMapper = mock(CompanyMemberMapper.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtService = mock(JwtService.class);
        tokenStore = mock(AuthTokenStore.class);
        smsCodeService = mock(SmsCodeService.class);
        authService = new AuthService(userMapper, userProfileMapper, hrProfileMapper, companyMapper,
                companyMemberMapper, passwordEncoder, jwtService, tokenStore, smsCodeService);
    }

    @Test
    void 已注册手机号抛40910且不消耗验证码() {
        UserEntity existing = new UserEntity();
        existing.setId("u-seeker-1");
        existing.setPhone("13800009999");
        when(userMapper.selectList(any())).thenReturn(List.of(existing));

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.register(
                new AuthRequests.RegisterRequest("13800009999", "123456", "123456", "JOB_SEEKER")));

        assertEquals(ApiCodes.PHONE_REGISTERED, ex.getCode());
        verify(smsCodeService, never()).verify(anyString(), anyString(), anyString());
        verify(userMapper, never()).insert(any(UserEntity.class));
    }

    @Test
    void 验证码错误抛40110且不落库() {
        when(userMapper.selectList(any())).thenReturn(List.of());
        doThrow(new BusinessException(ApiCodes.LOGIN_FAILED, "验证码错误或已过期"))
                .when(smsCodeService).verify("13800006666", "REGISTER", "000000");

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.register(
                new AuthRequests.RegisterRequest("13800006666", "000000", "123456", "HR")));

        assertEquals(ApiCodes.LOGIN_FAILED, ex.getCode());
        verify(userMapper, never()).insert(any(UserEntity.class));
        verify(companyMapper, never()).insert(any(CompanyEntity.class));
    }

    @Test
    void 求职者注册建画像不建公司并签发双token() {
        when(userMapper.selectList(any())).thenReturn(List.of());
        when(passwordEncoder.encode("123456")).thenReturn("hash-1");
        when(jwtService.createAccessToken(anyString(), anyString(), any())).thenReturn("at-token");
        when(tokenStore.issueRefresh(anyString())).thenReturn("rt-token");

        ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class);
        ArgumentCaptor<UserProfileEntity> profileCaptor = ArgumentCaptor.forClass(UserProfileEntity.class);
        when(userMapper.insert(userCaptor.capture())).thenReturn(1);
        when(userProfileMapper.insert(profileCaptor.capture())).thenReturn(1);
        when(userProfileMapper.selectOne(any())).thenAnswer(inv -> profileCaptor.getValue());

        AuthResponses.AuthResult result = authService.register(
                new AuthRequests.RegisterRequest("13800009999", "123456", "123456", "JOB_SEEKER"));

        UserEntity user = userCaptor.getValue();
        assertEquals("u-reg-13800009999", user.getId());
        assertEquals("13800009999", user.getPhone());
        assertEquals("JOB_SEEKER", user.getRole());
        assertEquals("ACTIVE", user.getStatus());
        assertEquals("hash-1", user.getPasswordHash());
        assertNotNull(user.getLastLoginAt());
        assertNotNull(user.getCreatedAt());
        assertNotNull(user.getUpdatedAt());

        UserProfileEntity profile = profileCaptor.getValue();
        assertEquals("up-reg-13800009999", profile.getId());
        assertEquals(user.getId(), profile.getUserId());
        assertEquals("求职者9999", profile.getName());
        assertEquals("ACTIVE", profile.getSeekingStatus());

        assertEquals("at-token", result.accessToken());
        assertEquals("rt-token", result.refreshToken());
        assertInstanceOf(AuthViews.UserProfile.class, result.profile());
        assertEquals("求职者9999", ((AuthViews.UserProfile) result.profile()).name());
        assertNull(result.company());
        assertNull(result.member());
        verify(companyMapper, never()).insert(any(CompanyEntity.class));
        verify(hrProfileMapper, never()).insert(any(HrProfileEntity.class));
    }

    @Test
    void HR注册自动建未认证公司与ADMIN成员() {
        when(userMapper.selectList(any())).thenReturn(List.of());
        when(passwordEncoder.encode("123456")).thenReturn("hash-2");
        when(jwtService.createAccessToken(anyString(), anyString(), any())).thenReturn("at-token");
        when(tokenStore.issueRefresh(anyString())).thenReturn("rt-token");

        ArgumentCaptor<CompanyEntity> companyCaptor = ArgumentCaptor.forClass(CompanyEntity.class);
        ArgumentCaptor<HrProfileEntity> hrCaptor = ArgumentCaptor.forClass(HrProfileEntity.class);
        ArgumentCaptor<CompanyMemberEntity> memberCaptor = ArgumentCaptor.forClass(CompanyMemberEntity.class);
        when(userMapper.insert(any(UserEntity.class))).thenReturn(1);
        when(companyMapper.insert(companyCaptor.capture())).thenReturn(1);
        when(hrProfileMapper.insert(hrCaptor.capture())).thenReturn(1);
        when(companyMemberMapper.insert(memberCaptor.capture())).thenReturn(1);
        when(hrProfileMapper.selectOne(any())).thenAnswer(inv -> hrCaptor.getValue());
        when(companyMemberMapper.selectOne(any())).thenAnswer(inv -> memberCaptor.getValue());
        when(companyMapper.selectById(anyString())).thenAnswer(inv -> companyCaptor.getValue());

        AuthResponses.AuthResult result = authService.register(
                new AuthRequests.RegisterRequest("13800007777", "123456", "123456", "HR"));

        CompanyEntity company = companyCaptor.getValue();
        assertEquals("co-reg-13800007777", company.getId());
        assertEquals("新注册企业-7777", company.getName());
        assertEquals(Boolean.FALSE, company.getVerified());
        assertEquals("ACTIVE", company.getStatus());
        assertEquals("未融资", company.getStage());
        assertEquals("1-19人", company.getSize());
        assertEquals("企业服务", company.getIndustry());

        HrProfileEntity hr = hrCaptor.getValue();
        assertEquals("hr-reg-13800007777", hr.getId());
        assertEquals("招聘官7777", hr.getName());
        assertEquals("招聘负责人", hr.getTitle());
        assertEquals(company.getId(), hr.getCompanyId());

        CompanyMemberEntity member = memberCaptor.getValue();
        assertEquals("cm-reg-13800007777", member.getId());
        assertEquals("ADMIN", member.getRole());
        assertEquals(company.getId(), member.getCompanyId());
        assertNotNull(member.getJoinedAt());

        assertNotNull(result.company());
        assertEquals("新注册企业-7777", result.company().name());
        assertEquals(Boolean.FALSE, result.company().verified());
        assertInstanceOf(AuthViews.HrProfile.class, result.profile());
        assertEquals("招聘官7777", ((AuthViews.HrProfile) result.profile()).name());
        assertNotNull(result.member());
        assertEquals("ADMIN", result.member().role());
        verify(jwtService).createAccessToken("u-reg-13800007777", "HR", "co-reg-13800007777");
    }
}
