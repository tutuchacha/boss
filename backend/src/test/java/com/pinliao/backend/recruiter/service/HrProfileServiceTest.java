package com.pinliao.backend.recruiter.service;

import com.pinliao.backend.auth.context.UserContext;
import com.pinliao.backend.auth.entity.HrProfileEntity;
import com.pinliao.backend.auth.mapper.HrProfileMapper;
import com.pinliao.backend.common.exception.BusinessException;
import com.pinliao.backend.recruiter.dto.RecruiterRequests;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * T8：§4.1 / §4.2 招聘者画像单元测试。
 *
 * <p>重点覆盖：按 user_id 归属读取、档案缺失 40410、部分更新语义（null 不改 / 空串清空可选字段）
 * 与角色守卫 40301。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HrProfileServiceTest {

    @Mock
    private HrProfileMapper hrProfileMapper;

    private HrProfileService service;

    @BeforeEach
    void setUp() {
        service = new HrProfileService(hrProfileMapper);
        manager();
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void 获取画像_按userId归属返回() {
        when(hrProfileMapper.selectOne(any())).thenReturn(profile());
        var view = service.profile();
        assertEquals("hr-1", view.id());
        assertEquals("u-hr-1", view.userId());
        assertEquals("陈女士", view.name());
    }

    @Test
    void 获取画像_档案缺失报40410() {
        when(hrProfileMapper.selectOne(any())).thenReturn(null);
        BusinessException e = assertThrows(BusinessException.class, () -> service.profile());
        assertEquals(40410, e.getCode());
    }

    @Test
    void 更新画像_仅改入参字段_空串清空可选字段() {
        HrProfileEntity existing = profile();
        when(hrProfileMapper.selectOne(any())).thenReturn(existing);

        var view = service.update(new RecruiterRequests.HrProfilePatch("  陈小满 ", null, "", null));
        assertEquals("陈小满", view.name(), "字符串应 trim 后写入");
        assertEquals("HRBP", view.title(), "null = 不修改");
        assertNull(view.avatarUrl(), "空串 = 清空可选字段");
        assertEquals("专注技术招聘", view.intro(), "未提供的字段保持原值");
    }

    @Test
    void 更新画像_非招聘者报40301() {
        seeker();
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.update(new RecruiterRequests.HrProfilePatch("张三", null, null, null)));
        assertEquals(40301, e.getCode());
    }

    // ------------------------------------------------------------------

    private static HrProfileEntity profile() {
        HrProfileEntity profile = new HrProfileEntity();
        profile.setId("hr-1");
        profile.setUserId("u-hr-1");
        profile.setCompanyId("co-1");
        profile.setName("陈女士");
        profile.setTitle("HRBP");
        profile.setAvatarUrl("https://cdn.test/a.png");
        profile.setIntro("专注技术招聘");
        return profile;
    }

    private static void manager() {
        UserContext.set(new UserContext.CurrentUser("u-hr-1", "HR", "co-1", "jti",
                Instant.now().plusSeconds(3600)));
    }

    private static void seeker() {
        UserContext.set(new UserContext.CurrentUser("u-seeker-1", "JOB_SEEKER", null, "jti",
                Instant.now().plusSeconds(3600)));
    }
}
