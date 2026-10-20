package com.pinliao.backend.ai.search;

import com.pinliao.backend.ai.AiCodes;
import com.pinliao.backend.ai.config.AiProperties;
import com.pinliao.backend.ai.dto.AiSearchRequests;
import com.pinliao.backend.ai.index.ResumeChunkEntity;
import com.pinliao.backend.ai.index.ResumeChunkStore;
import com.pinliao.backend.ai.index.ResumeIndexService;
import com.pinliao.backend.ai.qa.AiQuotaService;
import com.pinliao.backend.ai.spi.AiEmbeddingModel;
import com.pinliao.backend.ai.view.AiSearchViews;
import com.pinliao.backend.application.entity.ApplicationEntity;
import com.pinliao.backend.application.mapper.ApplicationMapper;
import com.pinliao.backend.auth.context.UserContext;
import com.pinliao.backend.common.api.ApiCodes;
import com.pinliao.backend.common.exception.BusinessException;
import com.pinliao.backend.job.entity.JobEntity;
import com.pinliao.backend.job.mapper.JobMapper;
import com.pinliao.backend.recruiter.RecruiterCodes;
import com.pinliao.backend.recruiter.service.CandidateSummaryService;
import com.pinliao.backend.recruiter.view.RecruiterViews;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * T-AI4：语义人才检索（开关 / 角色 / 入参 / 租户可见范围 / 索引兜底 / 聚合去重 / 阈值 / 配额）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SemanticTalentSearchServiceTest {

    private static final String COMPANY_ID = "co-test";
    private static final String MODEL = "fake-bag-of-words-v1";

    @Mock
    private ApplicationMapper applicationMapper;
    @Mock
    private ResumeChunkStore chunkStore;
    @Mock
    private ResumeIndexService indexService;
    @Mock
    private AiEmbeddingModel embeddingModel;
    @Mock
    private AiQuotaService quotaService;
    @Mock
    private JobMapper jobMapper;
    @Mock
    private CandidateSummaryService candidateSummaryService;

    @BeforeEach
    void setUp() {
        UserContext.set(new UserContext.CurrentUser("u-hr-1", "HR", COMPANY_ID, "jti",
                Instant.now().plusSeconds(60)));
        when(embeddingModel.modelId()).thenReturn(MODEL);
        when(embeddingModel.embedAll(any())).thenReturn(List.of(new float[]{1f, 0f}));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void 关闭时_42220且不触碰任何依赖() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> service(false).search(input("React", List.of(), null)));

        assertEquals(AiCodes.AI_DISABLED, error.getCode());
        verifyNoInteractions(applicationMapper, chunkStore, indexService, embeddingModel, quotaService);
    }

    @Test
    void 条件为空_42210() {
        assertEquals(RecruiterCodes.EMPTY_CRITERIA,
                assertThrows(BusinessException.class,
                        () -> service(true).search(new AiSearchRequests.SemanticSearch("  ", List.of(" "), null)))
                        .getCode());
        verifyNoInteractions(applicationMapper, chunkStore, quotaService);
    }

    @Test
    void 需求过长_42201() {
        assertEquals(ApiCodes.FIELD_VALIDATION,
                assertThrows(BusinessException.class,
                        () -> service(true).search(input("需".repeat(201), List.of(), null))).getCode());
    }

    @Test
    void 求职者调用_40301() {
        UserContext.set(new UserContext.CurrentUser("u-seeker", "JOB_SEEKER", COMPANY_ID, "jti",
                Instant.now().plusSeconds(60)));

        assertEquals(ApiCodes.FORBIDDEN,
                assertThrows(BusinessException.class,
                        () -> service(true).search(input("React", List.of(), null))).getCode());
        verifyNoInteractions(applicationMapper, chunkStore, quotaService);
    }

    @Test
    void 公司没有投递_返回空且不向量化() {
        when(applicationMapper.selectList(any())).thenReturn(List.of());

        assertTrue(service(true).search(input("React", List.of(), null)).isEmpty());
        verify(quotaService).consume("u-hr-1");
        verify(embeddingModel, never()).embedAll(any());
    }

    @Test
    void 成功检索_按相似度排序并返回证据片段() {
        ApplicationEntity app1 = application("app-1", "u-1", "resume-1", "job-1");
        ApplicationEntity app2 = application("app-2", "u-2", "resume-2", "job-2");
        when(applicationMapper.selectList(any())).thenReturn(List.of(app1, app2));
        when(chunkStore.countByResume(anyString(), eq(MODEL))).thenReturn(5L);
        when(chunkStore.findByResumesAndModel(any(), eq(MODEL))).thenReturn(List.of(
                chunk("rc-1", "resume-1", "skills", "技能标签", "技能标签\nReact、TypeScript", 1f, 0f),
                chunk("rc-2", "resume-1", "experiences", "工作经历 · 某公司", "工作经历 · 某公司\n负责前端工程化", 0.6f, 0.8f),
                chunk("rc-3", "resume-2", "skills", "技能标签", "技能标签\nJava、Redis", 0f, 1f)));
        when(candidateSummaryService.summarise("u-1")).thenReturn(candidate("u-1", "林小满"));
        when(candidateSummaryService.summarise("u-2")).thenReturn(candidate("u-2", "周叙白"));
        when(jobMapper.selectById("job-1")).thenReturn(job("前端工程师"));
        when(jobMapper.selectById("job-2")).thenReturn(job("后端工程师"));

        List<AiSearchViews.SemanticMatch> results = service(true).search(input("React", List.of("前端"), null));

        assertEquals(1, results.size());
        AiSearchViews.SemanticMatch top = results.get(0);
        assertEquals("app-1", top.applicationId());
        assertEquals("林小满", top.candidate().name());
        assertEquals("前端工程师", top.jobTitle());
        assertEquals(1.0, top.score());
        assertEquals(2, top.evidences().size());
        assertEquals("skills", top.evidences().get(0).section());
        assertEquals("React、TypeScript", top.evidences().get(0).snippet());
        assertFalse(top.evidences().get(1).snippet().contains("工作经历"));
        verify(quotaService).consume("u-hr-1");
    }

    @Test
    void 同一候选人多条投递_按最高分去重() {
        ApplicationEntity high = application("app-high", "u-1", "resume-1", "job-1");
        high.setUpdatedAt(LocalDateTime.of(2026, 10, 19, 10, 0));
        ApplicationEntity low = application("app-low", "u-1", "resume-2", "job-2");
        low.setUpdatedAt(LocalDateTime.of(2026, 10, 18, 10, 0));
        when(applicationMapper.selectList(any())).thenReturn(List.of(high, low));
        when(chunkStore.countByResume(anyString(), eq(MODEL))).thenReturn(5L);
        when(chunkStore.findByResumesAndModel(any(), eq(MODEL))).thenReturn(List.of(
                chunk("rc-1", "resume-1", "skills", "技能标签", "技能标签\nReact", 0.9f, 0f),
                chunk("rc-2", "resume-2", "skills", "技能标签", "技能标签\nReact", 0.6f, 0.8f)));
        when(candidateSummaryService.summarise("u-1")).thenReturn(candidate("u-1", "林小满"));
        when(jobMapper.selectById("job-1")).thenReturn(job("前端工程师"));

        List<AiSearchViews.SemanticMatch> results = service(true).search(input("React", List.of(), null));

        assertEquals(1, results.size());
        assertEquals("app-high", results.get(0).applicationId());
    }

    @Test
    void 低于最低相似度_不出现在结果里() {
        when(applicationMapper.selectList(any())).thenReturn(List.of(
                application("app-1", "u-1", "resume-1", "job-1")));
        when(chunkStore.countByResume(anyString(), eq(MODEL))).thenReturn(5L);
        when(chunkStore.findByResumesAndModel(any(), eq(MODEL))).thenReturn(List.of(
                chunk("rc-1", "resume-1", "skills", "技能标签", "技能标签\nJava", 0.05f, 0.99f)));

        assertTrue(service(true).search(input("React", List.of(), null)).isEmpty());
    }

    @Test
    void 索引缺失时按需补建() {
        when(applicationMapper.selectList(any())).thenReturn(List.of(
                application("app-1", "u-1", "resume-1", "job-1")));
        when(chunkStore.countByResume("resume-1", MODEL)).thenReturn(0L, 5L);
        when(chunkStore.findByResumesAndModel(any(), eq(MODEL))).thenReturn(List.of(
                chunk("rc-1", "resume-1", "skills", "技能标签", "技能标签\nReact", 1f, 0f)));
        when(candidateSummaryService.summarise("u-1")).thenReturn(candidate("u-1", "林小满"));
        when(jobMapper.selectById("job-1")).thenReturn(job("前端工程师"));

        assertEquals(1, service(true).search(input("React", List.of(), null)).size());
        verify(indexService).rebuildOne("resume-1");
    }

    @Test
    void 索引维度与模型不一致_42221() {
        when(applicationMapper.selectList(any())).thenReturn(List.of(
                application("app-1", "u-1", "resume-1", "job-1")));
        when(chunkStore.countByResume(anyString(), eq(MODEL))).thenReturn(5L);
        when(chunkStore.findByResumesAndModel(any(), eq(MODEL))).thenReturn(List.of(
                chunk("rc-1", "resume-1", "skills", "技能标签", "技能标签\nReact", 1f, 0f, 0f)));

        assertEquals(AiCodes.INDEX_NOT_READY,
                assertThrows(BusinessException.class,
                        () -> service(true).search(input("React", List.of(), null))).getCode());
    }

    @Test
    void 配额超限_42902且不查询分块() {
        when(applicationMapper.selectList(any())).thenReturn(List.of(
                application("app-1", "u-1", "resume-1", "job-1")));
        doThrow(new BusinessException(AiCodes.QUOTA_EXCEEDED, "quota")).when(quotaService).consume("u-hr-1");

        assertEquals(AiCodes.QUOTA_EXCEEDED,
                assertThrows(BusinessException.class,
                        () -> service(true).search(input("React", List.of(), null))).getCode());
        verifyNoInteractions(chunkStore);
    }

    @Test
    void limit超过上限时截断到20() {
        List<ApplicationEntity> apps = new java.util.ArrayList<>();
        for (int i = 1; i <= 25; i++) {
            apps.add(application("app-" + i, "u-" + i, "resume-" + i, "job-1"));
        }
        when(applicationMapper.selectList(any())).thenReturn(apps);
        when(chunkStore.countByResume(anyString(), eq(MODEL))).thenReturn(5L);
        List<ResumeChunkEntity> chunks = new java.util.ArrayList<>();
        for (int i = 1; i <= 25; i++) {
            chunks.add(chunk("rc-" + i, "resume-" + i, "skills", "技能标签", "技能标签\nReact", 1f, 0f));
        }
        when(chunkStore.findByResumesAndModel(any(), eq(MODEL))).thenReturn(chunks);
        when(candidateSummaryService.summarise(anyString())).thenAnswer(invocation ->
                candidate(invocation.getArgument(0), "候选人"));
        when(jobMapper.selectById(anyString())).thenReturn(job("职位"));

        assertEquals(20, service(true).search(input("React", List.of(), 100)).size());
    }

    @Test
    void snippet去掉标题行并截断() {
        assertEquals("负责订单链路", SemanticTalentSearchService.snippetOf("工作经历 · 某公司\n负责订单链路"));
        assertEquals("只有一行", SemanticTalentSearchService.snippetOf("只有一行"));
        String longText = "工作经历\n" + "字".repeat(200);
        assertTrue(SemanticTalentSearchService.snippetOf(longText).endsWith("…"));
    }

    private SemanticTalentSearchService service(boolean enabled) {
        AiProperties properties = new AiProperties(enabled, AiProperties.PROVIDER_FAKE, 800, 10, 6, 0.2, 700, 100, 8, 0.15);
        return new SemanticTalentSearchService(properties, applicationMapper, chunkStore, indexService,
                embeddingModel, quotaService, jobMapper, candidateSummaryService);
    }

    private static AiSearchRequests.SemanticSearch input(String query, List<String> tags, Integer limit) {
        return new AiSearchRequests.SemanticSearch(query, tags, limit);
    }

    private static ApplicationEntity application(String id, String userId, String resumeId, String jobId) {
        ApplicationEntity entity = new ApplicationEntity();
        entity.setId(id);
        entity.setUserId(userId);
        entity.setResumeId(resumeId);
        entity.setJobId(jobId);
        entity.setCompanyId(COMPANY_ID);
        entity.setUpdatedAt(LocalDateTime.of(2026, 10, 20, 10, 0));
        return entity;
    }

    private static ResumeChunkEntity chunk(String id, String resumeId, String section, String title,
                                           String content, float... vector) {
        ResumeChunkEntity entity = new ResumeChunkEntity();
        entity.setId(id);
        entity.setResumeId(resumeId);
        entity.setSection(section);
        entity.setTitle(title);
        entity.setContent(content);
        entity.setModel(MODEL);
        entity.setDim(vector.length);
        entity.setEmbedding(ResumeChunkStore.toJson(vector));
        return entity;
    }

    private static RecruiterViews.Candidate candidate(String userId, String name) {
        return new RecruiterViews.Candidate(userId, name, "前端开发", "上海", 3, "本科", 85);
    }

    private static JobEntity job(String title) {
        JobEntity entity = new JobEntity();
        entity.setTitle(title);
        return entity;
    }
}
