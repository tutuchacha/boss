package com.pinliao.backend.company.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.pinliao.backend.auth.context.UserContext;
import com.pinliao.backend.auth.entity.CompanyEntity;
import com.pinliao.backend.auth.entity.CompanyMemberEntity;
import com.pinliao.backend.auth.entity.HrProfileEntity;
import com.pinliao.backend.auth.entity.UserEntity;
import com.pinliao.backend.auth.mapper.CompanyMapper;
import com.pinliao.backend.auth.mapper.CompanyMemberMapper;
import com.pinliao.backend.auth.mapper.HrProfileMapper;
import com.pinliao.backend.auth.mapper.UserMapper;
import com.pinliao.backend.auth.view.AuthViews;
import com.pinliao.backend.common.exception.BusinessException;
import com.pinliao.backend.company.CompanyCodes;
import com.pinliao.backend.company.dto.CompanyRequests;
import com.pinliao.backend.job.service.JobQueryService;
import com.pinliao.backend.job.view.JobViews;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T5：公司模块（§5.1~§5.6）单元测试。
 *
 * <p>语义基线 = mock {@code src/api/company.ts} / {@code mock/companyOps.ts}：
 * 资料校验 42210、已认证 40916、提交后 PENDING 并在约 1.2s 后自动通过；
 * 管理员以 company_members.role=ADMIN 为准（演示 HR 账号）。</p>
 */
class CompanyServiceTest {

    static {
        // 纯单测环境没有 MyBatis 上下文，手工初始化实体表信息供 LambdaWrapper 解析列名
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, CompanyMemberEntity.class);
        TableInfoHelper.initTableInfo(assistant, HrProfileEntity.class);
    }

    /** 自动通过延迟缩短到 30ms，异步等待上限 2s。 */
    private static final long TEST_VERIFY_DELAY_MS = 30;

    private CompanyMapper companyMapper;
    private CompanyMemberMapper companyMemberMapper;
    private UserMapper userMapper;
    private HrProfileMapper hrProfileMapper;
    private JobQueryService jobQueryService;
    private CompanyService service;

    @BeforeEach
    void setUp() {
        companyMapper = mock(CompanyMapper.class);
        companyMemberMapper = mock(CompanyMemberMapper.class);
        userMapper = mock(UserMapper.class);
        hrProfileMapper = mock(HrProfileMapper.class);
        jobQueryService = mock(JobQueryService.class);
        service = new CompanyService(companyMapper, companyMemberMapper, userMapper, hrProfileMapper,
                jobQueryService, TEST_VERIFY_DELAY_MS);
        loginHr();
    }

    @AfterEach
    void tearDown() {
        service.shutdown();
        UserContext.clear();
    }

    // ------------------------------------------------------------------
    // §5.1 / §5.2 公开读
    // ------------------------------------------------------------------

    @Test
    void 公司详情_公开读取_不存在40410() {
        CompanyEntity company = company(true, "ACTIVE");
        when(companyMapper.selectById("co-acgiw7")).thenReturn(company);

        AuthViews.Company view = service.detail("co-acgiw7");

        assertEquals("co-acgiw7", view.id());
        assertEquals("云杉科技", view.name());
        assertEquals("企业服务", view.industry());
        assertTrue(view.verified());
        assertEquals("ACTIVE", view.status());

        when(companyMapper.selectById("missing")).thenReturn(null);
        BusinessException e = assertThrows(BusinessException.class, () -> service.detail("missing"));
        assertEquals(CompanyCodes.COMPANY_NOT_FOUND, e.getCode());
    }

    @Test
    void 公司职位_公司不存在40410_存在则委托分页查询() {
        when(companyMapper.selectById("co-1")).thenReturn(company(true, "ACTIVE"));
        JobViews.Page<JobViews.Summary> page = new JobViews.Page<>(List.of(),
                new JobViews.Pagination(null, false, 0));
        when(jobQueryService.listByCompany("co-1", "cursor-1", 5)).thenReturn(page);

        assertSame(page, service.jobs("co-1", "cursor-1", 5));

        when(companyMapper.selectById("missing")).thenReturn(null);
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.jobs("missing", null, null));
        assertEquals(CompanyCodes.COMPANY_NOT_FOUND, e.getCode());
        verify(jobQueryService, never()).listByCompany("missing", null, null);
    }

    // ------------------------------------------------------------------
    // §5.3 我的公司
    // ------------------------------------------------------------------

    @Test
    void 我的公司_HR读取本公司_求职者40301() {
        when(companyMapper.selectById("co-acgiw7")).thenReturn(company(true, "ACTIVE"));

        AuthViews.Company view = service.myCompany();
        assertEquals("co-acgiw7", view.id());

        UserContext.set(new UserContext.CurrentUser("u-seeker-1", "JOB_SEEKER", null, "jti",
                Instant.now().plusSeconds(3600)));
        BusinessException e = assertThrows(BusinessException.class, () -> service.myCompany());
        assertEquals(40301, e.getCode());

        UserContext.set(new UserContext.CurrentUser("u-hr-1", "HR", null, "jti",
                Instant.now().plusSeconds(3600)));
        BusinessException noCompany = assertThrows(BusinessException.class, () -> service.myCompany());
        assertEquals(40301, noCompany.getCode());
    }

    // ------------------------------------------------------------------
    // §5.4 更新公司信息
    // ------------------------------------------------------------------

    @Test
    void 更新公司_成功_名称trim且字段落库() {
        stubAdmin();
        CompanyEntity company = company(true, "ACTIVE");
        when(companyMapper.selectById("co-acgiw7")).thenReturn(company);
        when(companyMapper.updateById(any(CompanyEntity.class))).thenReturn(1);

        AuthViews.Company view = service.update(new CompanyRequests.Patch(
                "  云杉科技（新）  ", null, "专注企业服务。", "企业服务", "C轮", "500-999人", "https://yunshan.dev"));

        assertEquals("云杉科技（新）", view.name());
        assertEquals("专注企业服务。", view.intro());
        assertEquals("https://yunshan.dev", view.website());

        ArgumentCaptor<CompanyEntity> captor = ArgumentCaptor.forClass(CompanyEntity.class);
        verify(companyMapper).updateById(captor.capture());
        assertEquals("云杉科技（新）", captor.getValue().getName());
        assertEquals("专注企业服务。", captor.getValue().getIntro());
    }

    @Test
    void 更新公司_字段校验失败42210() {
        stubAdmin();
        when(companyMapper.selectById("co-acgiw7")).thenReturn(company(true, "ACTIVE"));

        List<CompanyRequests.Patch> badPatches = List.of(
                new CompanyRequests.Patch("A", null, null, null, null, null, null),
                new CompanyRequests.Patch("这是一个超过三十个字的公司名称用于验证长度上限是否生效并且继续写长一点点", null,
                        null, null, null, null, null),
                new CompanyRequests.Patch(null, null, "x".repeat(501), null, null, null, null),
                new CompanyRequests.Patch(null, "x".repeat(513), null, null, null, null, null),
                new CompanyRequests.Patch(null, null, null, null, null, null, "x".repeat(257)));

        for (CompanyRequests.Patch patch : badPatches) {
            BusinessException e = assertThrows(BusinessException.class, () -> service.update(patch));
            assertEquals(CompanyCodes.PROFILE_INVALID, e.getCode());
        }
        BusinessException nullBody = assertThrows(BusinessException.class, () -> service.update(null));
        assertEquals(CompanyCodes.PROFILE_INVALID, nullBody.getCode());
        verify(companyMapper, never()).updateById(any(CompanyEntity.class));
    }

    @Test
    void 更新公司_非管理员40301() {
        CompanyMemberEntity member = member("cm-1", "u-hr-1", "co-acgiw7", "HR");
        when(companyMemberMapper.selectOne(any())).thenReturn(member);
        when(companyMapper.selectById("co-acgiw7")).thenReturn(company(true, "ACTIVE"));

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.update(new CompanyRequests.Patch("云杉科技（新）", null, null, null, null, null, null)));
        assertEquals(40301, e.getCode());
        verify(companyMapper, never()).updateById(any(CompanyEntity.class));
    }

    // ------------------------------------------------------------------
    // §5.5 公司成员
    // ------------------------------------------------------------------

    @Test
    void 公司成员_返回用户与招聘画像() {
        stubAdmin();
        CompanyMemberEntity admin = member("cm-1", "u-hr-1", "co-acgiw7", "ADMIN");
        CompanyMemberEntity hr = member("cm-2", "u-hr-2", "co-acgiw7", "HR");
        when(companyMemberMapper.selectList(any())).thenReturn(List.of(admin, hr));

        UserEntity user1 = user("u-hr-1", "13800000002");
        UserEntity user2 = user("u-hr-2", "13800000003");
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(user1, user2));

        HrProfileEntity profile1 = profile("hr-x2270n", "u-hr-1", "陈女士");
        HrProfileEntity profile2 = profile("hr-2", "u-hr-2", "李先生");
        when(hrProfileMapper.selectList(any())).thenReturn(List.of(profile1, profile2));

        List<AuthViews.CompanyMember> members = service.members();

        assertEquals(2, members.size());
        assertEquals("ADMIN", members.get(0).role());
        assertEquals("13800000002", members.get(0).user().phone());
        assertEquals("陈女士", members.get(0).hrProfile().name());
        assertEquals("HR", members.get(1).role());
        assertEquals("李先生", members.get(1).hrProfile().name());
    }

    @Test
    void 公司成员_非管理员40301() {
        when(companyMemberMapper.selectOne(any())).thenReturn(null);

        BusinessException e = assertThrows(BusinessException.class, service::members);
        assertEquals(40301, e.getCode());
    }

    // ------------------------------------------------------------------
    // §5.6 企业认证
    // ------------------------------------------------------------------

    @Test
    void 企业认证_提交PENDING并异步自动通过() {
        stubAdmin();
        CompanyEntity company = company(false, "ACTIVE");
        when(companyMapper.selectById("co-acgiw7")).thenReturn(company);
        when(companyMapper.updateById(any(CompanyEntity.class))).thenReturn(1);

        AuthViews.Company submitted = service.verify(validVerify());

        assertEquals(Boolean.FALSE, submitted.verified());
        assertEquals("PENDING", submitted.status());

        // 30ms 后后台线程再次 updateById：PENDING → verified=true / ACTIVE
        verify(companyMapper, timeout(2000).times(2)).updateById(any(CompanyEntity.class));
        assertTrue(company.getVerified());
        assertEquals("ACTIVE", company.getStatus());
    }

    @Test
    void 企业认证_已认证40916() {
        stubAdmin();
        when(companyMapper.selectById("co-acgiw7")).thenReturn(company(true, "ACTIVE"));

        BusinessException e = assertThrows(BusinessException.class, () -> service.verify(validVerify()));
        assertEquals(CompanyCodes.ALREADY_VERIFIED, e.getCode());
        verify(companyMapper, never()).updateById(any(CompanyEntity.class));
    }

    @Test
    void 企业认证_字段校验失败42210() {
        stubAdmin();
        when(companyMapper.selectById("co-acgiw7")).thenReturn(company(false, "ACTIVE"));

        List<CompanyRequests.Verify> badInputs = List.of(
                new CompanyRequests.Verify(null, "91310000MA1K35Q99X", "陈女士", "13800000002"),
                new CompanyRequests.Verify("云", "91310000MA1K35Q99X", "陈女士", "13800000002"),
                new CompanyRequests.Verify("云杉科技", "91310000MA1K35Q99", "陈女士", "13800000002"),
                new CompanyRequests.Verify("云杉科技", "91310000MA1K35Q9!X", "陈女士", "13800000002"),
                new CompanyRequests.Verify("云杉科技", "91310000MA1K35Q99X", " ", "13800000002"),
                new CompanyRequests.Verify("云杉科技", "91310000MA1K35Q99X", "陈女士", "23800000002"));

        for (CompanyRequests.Verify input : badInputs) {
            BusinessException e = assertThrows(BusinessException.class, () -> service.verify(input));
            assertEquals(CompanyCodes.PROFILE_INVALID, e.getCode());
        }
        BusinessException nullBody = assertThrows(BusinessException.class, () -> service.verify(null));
        assertEquals(CompanyCodes.PROFILE_INVALID, nullBody.getCode());
        verify(companyMapper, never()).updateById(any(CompanyEntity.class));
    }

    @Test
    void 企业认证_非管理员40301() {
        when(companyMemberMapper.selectOne(any())).thenReturn(null);
        when(companyMapper.selectById("co-acgiw7")).thenReturn(company(false, "ACTIVE"));

        BusinessException e = assertThrows(BusinessException.class, () -> service.verify(validVerify()));
        assertEquals(40301, e.getCode());
        verify(companyMapper, never()).updateById(any(CompanyEntity.class));
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    private static void loginHr() {
        UserContext.set(new UserContext.CurrentUser("u-hr-1", "HR", "co-acgiw7", "jti",
                Instant.now().plusSeconds(3600)));
    }

    private void stubAdmin() {
        when(companyMemberMapper.selectOne(any()))
                .thenReturn(member("cm-1", "u-hr-1", "co-acgiw7", "ADMIN"));
    }

    private static CompanyRequests.Verify validVerify() {
        return new CompanyRequests.Verify("云杉科技", "91310000MA1K35Q99X", "陈女士", "13800000002");
    }

    private static CompanyEntity company(boolean verified, String status) {
        CompanyEntity company = new CompanyEntity();
        company.setId("co-acgiw7");
        company.setName("云杉科技");
        company.setIntro("企业服务公司");
        company.setIndustry("企业服务");
        company.setStage("C轮");
        company.setSize("500-999人");
        company.setWebsite("https://yunshan.dev");
        company.setVerified(verified);
        company.setStatus(status);
        return company;
    }

    private static CompanyMemberEntity member(String id, String userId, String companyId, String role) {
        CompanyMemberEntity member = new CompanyMemberEntity();
        member.setId(id);
        member.setUserId(userId);
        member.setCompanyId(companyId);
        member.setRole(role);
        return member;
    }

    private static UserEntity user(String id, String phone) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setPhone(phone);
        user.setRole("HR");
        user.setStatus("ACTIVE");
        return user;
    }

    private static HrProfileEntity profile(String id, String userId, String name) {
        HrProfileEntity profile = new HrProfileEntity();
        profile.setId(id);
        profile.setUserId(userId);
        profile.setCompanyId("co-acgiw7");
        profile.setName(name);
        profile.setTitle("招聘经理");
        return profile;
    }
}
