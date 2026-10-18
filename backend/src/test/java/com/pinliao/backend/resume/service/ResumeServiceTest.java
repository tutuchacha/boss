package com.pinliao.backend.resume.service;

import com.pinliao.backend.application.entity.ApplicationEntity;
import com.pinliao.backend.application.entity.ResumeEntity;
import com.pinliao.backend.application.mapper.ApplicationMapper;
import com.pinliao.backend.application.mapper.ResumeMapper;
import com.pinliao.backend.auth.context.UserContext;
import com.pinliao.backend.auth.entity.UserProfileEntity;
import com.pinliao.backend.auth.mapper.UserProfileMapper;
import com.pinliao.backend.common.api.ApiCodes;
import com.pinliao.backend.common.exception.BusinessException;
import com.pinliao.backend.resume.ResumeCodes;
import com.pinliao.backend.resume.dto.ResumeRequests;
import com.pinliao.backend.resume.view.ResumeViews;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** T7：简历模块（§3.1~§3.6）单元测试。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ResumeServiceTest {

    @Mock
    private ResumeMapper resumeMapper;
    @Mock
    private UserProfileMapper userProfileMapper;
    @Mock
    private ApplicationMapper applicationMapper;

    private ResumeService service;

    @BeforeAll
    static void initMybatisLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace("test");
        TableInfoHelper.initTableInfo(assistant, ResumeEntity.class);
        TableInfoHelper.initTableInfo(assistant, UserProfileEntity.class);
    }

    @BeforeEach
    void setUp() {
        service = new ResumeService(resumeMapper, userProfileMapper, applicationMapper);
        seeker();
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void listPutsDefaultFirstThenUpdatedAtDesc() {
        ResumeEntity oldDefault = resume("resume-1", "默认简历", true, "2026-09-01T09:00:00");
        ResumeEntity newer = resume("resume-2", "新简历", false, "2026-09-10T09:00:00");
        when(resumeMapper.selectList(any())).thenReturn(List.of(newer, oldDefault));

        List<ResumeViews.Resume> result = service.list();
        assertEquals(2, result.size());
        assertEquals("resume-1", result.get(0).id());
        assertEquals("resume-2", result.get(1).id());
    }

    @Test
    void detailRejectsOtherSeekersResume() {
        ResumeEntity other = resume("resume-other", "他人简历", false, "2026-09-01T09:00:00");
        other.setUserId("u-other");
        when(resumeMapper.selectById("resume-other")).thenReturn(other);

        BusinessException e = assertThrows(BusinessException.class, () -> service.detail("resume-other"));
        assertEquals(ResumeCodes.NOT_FOUND, e.getCode());
    }

    @Test
    void detailAllowsHrOnlyWhenResumeWasSubmittedToCompany() {
        ResumeEntity resume = resume("resume-1", "默认简历", true, "2026-09-01T09:00:00");
        when(resumeMapper.selectById("resume-1")).thenReturn(resume);
        when(applicationMapper.selectCount(any())).thenReturn(0L);

        UserContext.set(new UserContext.CurrentUser("u-hr-1", "HR", "co-1",
                "jti-hr", Instant.now().plus(Duration.ofHours(2))));
        BusinessException denied = assertThrows(BusinessException.class, () -> service.detail("resume-1"));
        assertEquals(ResumeCodes.NOT_FOUND, denied.getCode());

        when(applicationMapper.selectCount(any())).thenReturn(1L);
        assertEquals("resume-1", service.detail("resume-1").id());
    }

    @Test
    void detailRejectsPlatformAdmin() {
        when(resumeMapper.selectById("resume-1"))
                .thenReturn(resume("resume-1", "默认简历", true, "2026-09-01T09:00:00"));
        UserContext.set(new UserContext.CurrentUser("u-admin", "PLATFORM_ADMIN", null,
                "jti-admin", Instant.now().plus(Duration.ofHours(2))));
        BusinessException e = assertThrows(BusinessException.class, () -> service.detail("resume-1"));
        assertEquals(ApiCodes.FORBIDDEN, e.getCode());
    }

    @Test
    void createFirstResumeUsesIncrementalIdAndMakesItDefault() {
        when(resumeMapper.selectList(any())).thenReturn(List.of());
        ResumeViews.Resume result = service.create(createRequest("我的简历"));

        assertTrue(result.isDefault());
        assertTrue(result.id().startsWith("resume-u-seeker-1-"));
        ArgumentCaptor<ResumeEntity> captor = ArgumentCaptor.forClass(ResumeEntity.class);
        verify(resumeMapper).insert(captor.capture());
        assertEquals("我的简历", captor.getValue().getName());
        verify(userProfileMapper).update(any(), any());
    }

    @Test
    void createSecondResumeDoesNotChangeDefault() {
        ResumeEntity existing = resume("resume-u-seeker-1-1", "默认简历", true, "2026-09-01T09:00:00");
        when(resumeMapper.selectList(any())).thenReturn(List.of(existing));

        ResumeViews.Resume result = service.create(createRequest("第二份"));
        assertFalse(result.isDefault());
        assertTrue(result.id().endsWith("-2"));
        verify(userProfileMapper, never()).update(any(), any());
    }

    @Test
    void updateOwnResumeReplacesContentAndName() {
        ResumeEntity existing = resume("resume-1", "默认简历", false, "2026-09-01T09:00:00");
        when(resumeMapper.selectById("resume-1")).thenReturn(existing);

        ResumeViews.Resume result = service.update("resume-1",
                new ResumeRequests.UpdateRequest("更新后的简历", content("新名字"), null, null));
        assertEquals("更新后的简历", result.name());
        assertEquals("新名字", result.content().path("basics").path("name").asText());
        verify(resumeMapper).updateById(existing);
    }

    @Test
    void setDefaultClearsOtherDefaultsAndSyncsProfile() {
        ResumeEntity existing = resume("resume-1", "默认简历", false, "2026-09-01T09:00:00");
        when(resumeMapper.selectById("resume-1")).thenReturn(existing);

        ResumeViews.Resume result = service.setDefault("resume-1");
        assertTrue(result.isDefault());
        verify(resumeMapper).update(any(), any());
        verify(resumeMapper).updateById(existing);
        verify(userProfileMapper).update(any(), any());
    }

    @Test
    void deletingDefaultPromotesMostRecentRemainingResume() {
        ResumeEntity target = resume("resume-1", "默认简历", true, "2026-09-01T09:00:00");
        ResumeEntity remaining = resume("resume-2", "新简历", false, "2026-09-10T09:00:00");
        when(resumeMapper.selectById("resume-1")).thenReturn(target);
        when(resumeMapper.selectList(any())).thenReturn(List.of(remaining));

        service.delete("resume-1");
        assertTrue(remaining.getIsDefault());
        verify(resumeMapper).updateById(target);
        verify(resumeMapper).updateById(remaining);
    }

    @Test
    void nextResumeIdIgnoresSeedIdAndUsesMaxSuffix() {
        ResumeEntity seed = resume("resume-seeker", "种子", true, "2026-09-01T09:00:00");
        ResumeEntity generated = resume("resume-u-seeker-1-3", "第三份", false, "2026-09-01T09:00:00");
        assertEquals("resume-u-seeker-1-4", ResumeService.nextResumeId("u-seeker-1", List.of(seed, generated)));
    }

    // ------------------------------------------------------------------

    private void seeker() {
        UserContext.set(new UserContext.CurrentUser("u-seeker-1", "JOB_SEEKER", null,
                "jti-seeker", Instant.now().plus(Duration.ofHours(2))));
    }

    private static ResumeEntity resume(String id, String name, boolean isDefault, String updatedAt) {
        ResumeEntity r = new ResumeEntity();
        r.setId(id);
        r.setUserId("u-seeker-1");
        r.setName(name);
        r.setContent("{\"basics\":{\"name\":\"李小明\",\"phone\":\"13800000001\"},\"educations\":[],"
                + "\"experiences\":[],\"projects\":[],\"skills\":[]}");
        r.setIsDefault(isDefault);
        r.setCreatedAt(LocalDateTime.parse("2026-09-01T09:00:00"));
        r.setUpdatedAt(LocalDateTime.parse(updatedAt));
        return r;
    }

    private static ResumeRequests.CreateRequest createRequest(String name) {
        return new ResumeRequests.CreateRequest(name, content("李小明"), null);
    }

    private static ResumeRequests.ResumeContent content(String name) {
        return new ResumeRequests.ResumeContent(
                new ResumeRequests.Basics(name, "13800000001", null, "上海", null, "个人优势"),
                List.of(), List.of(), List.of(), List.of("Java"));
    }
}
