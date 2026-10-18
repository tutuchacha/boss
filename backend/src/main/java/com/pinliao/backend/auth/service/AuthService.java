package com.pinliao.backend.auth.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pinliao.backend.auth.context.UserContext;
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
import com.pinliao.backend.auth.token.AuthTokenStore;
import com.pinliao.backend.auth.view.AuthViews;
import com.pinliao.backend.common.api.ApiCodes;
import com.pinliao.backend.common.exception.BusinessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 鉴权核心服务：注册、密码 / 验证码登录、刷新、退出、当前用户（契约 §1.2~§1.7）。
 *
 * <p>语义对齐前端 mock（src/api/mock/auth.ts 与 src/api/auth.ts）：</p>
 * <ul>
 *   <li>登录响应 = AuthResult + company / member（HR 工作台需要公司上下文）；</li>
 *   <li>错误码：40910 手机号已注册、40110 账号或密码 / 验证码错误、40111 账号已禁用、40103 refreshToken 无效；</li>
 *   <li>refreshToken 一次性轮换（用过即废），退出登录吊销 accessToken（jti 名单）与 refreshToken。</li>
 * </ul>
 */
@Service
public class AuthService {

    private final UserMapper userMapper;
    private final UserProfileMapper userProfileMapper;
    private final HrProfileMapper hrProfileMapper;
    private final CompanyMapper companyMapper;
    private final CompanyMemberMapper companyMemberMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthTokenStore tokenStore;
    private final SmsCodeService smsCodeService;

    public AuthService(UserMapper userMapper,
                       UserProfileMapper userProfileMapper,
                       HrProfileMapper hrProfileMapper,
                       CompanyMapper companyMapper,
                       CompanyMemberMapper companyMemberMapper,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AuthTokenStore tokenStore,
                       SmsCodeService smsCodeService) {
        this.userMapper = userMapper;
        this.userProfileMapper = userProfileMapper;
        this.hrProfileMapper = hrProfileMapper;
        this.companyMapper = companyMapper;
        this.companyMemberMapper = companyMemberMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.tokenStore = tokenStore;
        this.smsCodeService = smsCodeService;
    }

    /** §1.4 密码登录（account 可为手机号或邮箱）。 */
    public AuthResponses.AuthResult loginPassword(AuthRequests.LoginPasswordRequest req) {
        UserEntity user = findUserByAccount(req.account().trim());
        if (user == null || user.getPasswordHash() == null
                || !passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new BusinessException(ApiCodes.LOGIN_FAILED, "账号或密码错误");
        }
        ensureActive(user);
        return issueSession(user);
    }

    /** §1.3 手机号验证码登录（验证码一次性，先校验后查账号，与 mock 顺序一致）。 */
    public AuthResponses.AuthResult loginSms(AuthRequests.LoginSmsRequest req) {
        String phone = req.phone().trim();
        smsCodeService.verify(phone, "LOGIN", req.code());
        UserEntity user = findUserByPhone(phone);
        if (user == null) {
            throw new BusinessException(ApiCodes.LOGIN_FAILED, "该手机号尚未注册，请先注册");
        }
        ensureActive(user);
        return issueSession(user);
    }

    /** §1.2 手机号注册（注册成功即登录；HR 自动创建「未认证」公司，语义对齐 mock registerMockAccount）。 */
    @Transactional
    public AuthResponses.AuthResult register(AuthRequests.RegisterRequest req) {
        String phone = req.phone().trim();
        // 校验顺序对齐 mock（src/api/auth.ts §1.2）：已注册（40910）先于验证码（40110，失败不消耗验证码）
        if (findUserByPhone(phone) != null) {
            throw new BusinessException(ApiCodes.PHONE_REGISTERED, "该手机号已注册，请直接登录");
        }
        smsCodeService.verify(phone, "REGISTER", req.code());

        String suffix = phone.substring(phone.length() - 4);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        // id 派生自手机号：mock 用进程内递增计数（u-reg-1…，刷新即复位）；落库需跨重启稳定且唯一
        UserEntity user = new UserEntity();
        user.setId("u-reg-" + phone);
        user.setPhone(phone);
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setRole(req.role());
        user.setStatus("ACTIVE");
        user.setLastLoginAt(now);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userMapper.insert(user);

        if ("JOB_SEEKER".equals(req.role())) {
            UserProfileEntity profile = new UserProfileEntity();
            profile.setId("up-reg-" + phone);
            profile.setUserId(user.getId());
            profile.setName("求职者" + suffix);
            profile.setSeekingStatus("ACTIVE");
            profile.setCreatedAt(now);
            profile.setUpdatedAt(now);
            userProfileMapper.insert(profile);
        } else {
            CompanyEntity company = new CompanyEntity();
            company.setId("co-reg-" + phone);
            company.setName("新注册企业-" + suffix);
            company.setStage("未融资");
            company.setSize("1-19人");
            company.setIndustry("企业服务");
            company.setVerified(false);
            company.setStatus("ACTIVE");
            company.setCreatedAt(now);
            company.setUpdatedAt(now);
            companyMapper.insert(company);

            HrProfileEntity hrProfile = new HrProfileEntity();
            hrProfile.setId("hr-reg-" + phone);
            hrProfile.setUserId(user.getId());
            hrProfile.setCompanyId(company.getId());
            hrProfile.setName("招聘官" + suffix);
            hrProfile.setTitle("招聘负责人");
            hrProfile.setCreatedAt(now);
            hrProfile.setUpdatedAt(now);
            hrProfileMapper.insert(hrProfile);

            CompanyMemberEntity member = new CompanyMemberEntity();
            member.setId("cm-reg-" + phone);
            member.setUserId(user.getId());
            member.setCompanyId(company.getId());
            member.setRole("ADMIN");
            member.setJoinedAt(now);
            companyMemberMapper.insert(member);
        }

        return issueSession(user);
    }

    /** §1.5 刷新 token（一次性轮换：旧 refreshToken 立即失效）。 */
    public AuthResponses.RefreshResult refresh(AuthRequests.RefreshRequest req) {
        String oldToken = req.refreshToken().trim();
        String userId = tokenStore.resolveRefresh(oldToken);
        if (userId == null) {
            throw new BusinessException(ApiCodes.REFRESH_TOKEN_INVALID, "登录已失效，请重新登录");
        }
        UserEntity user = userMapper.selectById(userId);
        if (user == null || !"ACTIVE".equals(user.getStatus())) {
            tokenStore.revokeRefresh(oldToken);
            throw new BusinessException(ApiCodes.REFRESH_TOKEN_INVALID, "登录已失效，请重新登录");
        }
        tokenStore.revokeRefresh(oldToken);
        SessionData session = buildSession(user);
        String access = jwtService.createAccessToken(user.getId(), user.getRole(), companyIdOf(session));
        return new AuthResponses.RefreshResult(access, tokenStore.issueRefresh(user.getId()));
    }

    /** §1.6 退出登录：吊销当前 accessToken（jti 名单）+ 请求携带的 refreshToken。 */
    public void logout(UserContext.CurrentUser current, AuthRequests.LogoutRequest req) {
        tokenStore.revokeAccess(current.jti(), current.expiresAt());
        if (req != null && req.refreshToken() != null && !req.refreshToken().isBlank()) {
            tokenStore.revokeRefresh(req.refreshToken().trim());
        }
    }

    /** §1.7 当前用户信息（user + profile，HR 额外带 company / member）。 */
    public AuthResponses.MeResponse me(String userId) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null || !"ACTIVE".equals(user.getStatus())) {
            throw new BusinessException(ApiCodes.UNAUTHORIZED, "账号不存在或已被禁用");
        }
        SessionData session = buildSession(user);
        return new AuthResponses.MeResponse(session.user(), session.profile(), session.company(), session.member());
    }

    // ------------------------------------------------------------------
    // 内部：登录会话装配
    // ------------------------------------------------------------------

    /** 签发双 token + 更新最近登录时间，返回完整登录结果。 */
    private AuthResponses.AuthResult issueSession(UserEntity user) {
        SessionData session = buildSession(user);
        touchLastLogin(user.getId());
        String access = jwtService.createAccessToken(user.getId(), user.getRole(), companyIdOf(session));
        String refresh = tokenStore.issueRefresh(user.getId());
        return new AuthResponses.AuthResult(access, refresh, session.user(), session.profile(),
                session.company(), session.member());
    }

    /** 会话上下文：user 视图 + 画像（求职者 UserProfile / 招聘者 HrProfile）+ 公司与成员（仅 HR）。 */
    private record SessionData(AuthViews.User user, Object profile,
                               AuthViews.Company company, AuthViews.CompanyMember member) {
    }

    private SessionData buildSession(UserEntity user) {
        AuthViews.User userView = AuthViews.User.from(user);
        if ("JOB_SEEKER".equals(user.getRole())) {
            UserProfileEntity up = userProfileMapper.selectOne(Wrappers.<UserProfileEntity>lambdaQuery()
                    .eq(UserProfileEntity::getUserId, user.getId()));
            return new SessionData(userView, up == null ? null : AuthViews.UserProfile.from(up), null, null);
        }
        HrProfileEntity hp = hrProfileMapper.selectOne(Wrappers.<HrProfileEntity>lambdaQuery()
                .eq(HrProfileEntity::getUserId, user.getId()));
        AuthViews.HrProfile hrView = hp == null ? null : AuthViews.HrProfile.from(hp);
        CompanyMemberEntity cm = companyMemberMapper.selectOne(Wrappers.<CompanyMemberEntity>lambdaQuery()
                .eq(CompanyMemberEntity::getUserId, user.getId())
                .orderByAsc(CompanyMemberEntity::getJoinedAt)
                .last("LIMIT 1"));
        CompanyEntity company = null;
        if (cm != null) {
            company = companyMapper.selectById(cm.getCompanyId());
        } else if (hp != null) {
            company = companyMapper.selectById(hp.getCompanyId());
        }
        return new SessionData(userView, hrView,
                company == null ? null : AuthViews.Company.from(company),
                cm == null ? null : AuthViews.CompanyMember.from(cm, userView, hrView));
    }

    private static String companyIdOf(SessionData session) {
        return session.company() == null ? null : session.company().id();
    }

    // ------------------------------------------------------------------
    // 内部：查询与校验
    // ------------------------------------------------------------------

    private UserEntity findUserByAccount(String account) {
        return userMapper.selectList(Wrappers.<UserEntity>lambdaQuery()
                        .and(w -> w.eq(UserEntity::getPhone, account).or().eq(UserEntity::getEmail, account))
                        .isNull(UserEntity::getDeletedAt)
                        .orderByAsc(UserEntity::getCreatedAt)
                        .last("LIMIT 1"))
                .stream().findFirst().orElse(null);
    }

    private UserEntity findUserByPhone(String phone) {
        return userMapper.selectList(Wrappers.<UserEntity>lambdaQuery()
                        .eq(UserEntity::getPhone, phone)
                        .isNull(UserEntity::getDeletedAt)
                        .last("LIMIT 1"))
                .stream().findFirst().orElse(null);
    }

    private static void ensureActive(UserEntity user) {
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new BusinessException(ApiCodes.ACCOUNT_DISABLED, "账号已被禁用");
        }
    }

    /** 更新最近登录时间（只更新该列，updated_at 由 DB 的 ON UPDATE 自动推进）。 */
    private void touchLastLogin(String userId) {
        userMapper.update(null, Wrappers.<UserEntity>lambdaUpdate()
                .eq(UserEntity::getId, userId)
                .set(UserEntity::getLastLoginAt, LocalDateTime.now(ZoneOffset.UTC)));
    }
}
