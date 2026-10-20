package com.pinliao.backend.ai.index;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pinliao.backend.ai.AiCodes;
import com.pinliao.backend.ai.config.AiProperties;
import com.pinliao.backend.ai.provider.FakeEmbeddingModel;
import com.pinliao.backend.ai.spi.AiEmbeddingModel;
import com.pinliao.backend.ai.view.AiIndexViews;
import com.pinliao.backend.application.entity.ResumeEntity;
import com.pinliao.backend.application.mapper.ResumeMapper;
import com.pinliao.backend.common.api.ApiCodes;
import com.pinliao.backend.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 索引服务单测（Step 4 T-AI1）：用假向量模型跑通「切块 → 复用 → 嵌入 → 替换」全链路，
 * 不依赖网络与付费 key。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ResumeIndexServiceTest {

    private static final String CONTENT = """
            {
              "basics": {"name": "林小满", "phone": "13800001101", "city": "上海", "summary": "3 年前端开发经验。"},
              "educations": [{"school": "某某大学", "major": "计算机", "degree": "本科",
                              "startDate": "2018-09", "endDate": "2022-06"}],
              "experiences": [{"company": "某公司", "title": "前端开发",
                               "startDate": "2022-07", "endDate": "present", "description": "负责 B 端后台。"}],
              "projects": [{"name": "招聘系统重构", "role": "前端负责人",
                            "startDate": "2024-03", "endDate": "2025-06", "description": "首屏降低 40%。"}],
              "skills": ["React", "TypeScript"]
            }
            """;

    private static final ObjectMapper JSON = new ObjectMapper();

    @Mock
    private ResumeMapper resumeMapper;
    @Mock
    private ResumeChunkStore chunkStore;
    @Mock
    private AiEmbeddingModel embeddingModel;

    private final FakeEmbeddingModel fake = new FakeEmbeddingModel(64);

    @BeforeEach
    void setUp() {
        when(embeddingModel.modelId()).thenReturn("fake-bag-of-words-v1");
        when(embeddingModel.dimensions()).thenReturn(64);
        when(embeddingModel.embedAll(anyList())).thenAnswer(invocation -> fake.embedAll(invocation.getArgument(0)));
        when(chunkStore.findVectorsByResume(anyString(), anyString())).thenReturn(Map.of());
        when(chunkStore.findEmbeddings(anyList(), anyString())).thenReturn(Map.of());
    }

    @Test
    void rebuildAllIndexesEveryResumeAndReplacesChunks() {
        when(resumeMapper.selectList(any())).thenReturn(List.of(
                resume("resume-1", CONTENT),
                resume("resume-2", "{\"skills\": [\"Java\"]}")));

        AiIndexViews.Rebuild result = service(true).rebuildAll();

        assertEquals("all", result.scope());
        assertEquals(2, result.resumes());
        assertEquals(0, result.skipped());
        assertEquals(6, result.chunks(), "5 段 + 1 段技能");
        assertEquals(result.chunks(), result.embedded());
        assertEquals(0, result.reused());
        assertTrue(result.elapsedMs() >= 0);

        ArgumentCaptor<List<ResumeChunkEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(chunkStore, times(2)).replace(anyString(), captor.capture());
        List<ResumeChunkEntity> rows = captor.getAllValues().stream().flatMap(List::stream).toList();
        assertEquals(6, rows.size());
        assertTrue(rows.stream().allMatch(row -> "fake-bag-of-words-v1".equals(row.getModel())));
        assertTrue(rows.stream().allMatch(row -> Integer.valueOf(64).equals(row.getDim())));
        assertTrue(rows.stream().allMatch(row -> row.getEmbedding() != null && row.getEmbedding().startsWith("[")));
        assertTrue(rows.stream().allMatch(row -> row.getCreatedAt() != null && row.getUpdatedAt() != null));
        assertEquals(rows.size(), rows.stream().map(ResumeChunkEntity::getId).distinct().count());
        assertTrue(rows.stream().allMatch(row -> row.getId().startsWith("rc_")));
        // 联系方式不进索引
        assertTrue(rows.stream().noneMatch(row -> row.getContent().contains("13800001101")));
    }

    @Test
    void rebuildReusesVectorsWhenContentUnchanged() throws Exception {
        when(resumeMapper.selectById("resume-1")).thenReturn(resume("resume-1", CONTENT));

        service(true).rebuildOne("resume-1");
        List<ResumeChunkEntity> firstRows = capturedRows();
        assertFalse(firstRows.isEmpty());

        Map<String, float[]> existing = new HashMap<>();
        for (ResumeChunkEntity row : firstRows) {
            existing.put(row.getContentHash(), JSON.readValue(row.getEmbedding(), float[].class));
        }
        when(chunkStore.findVectorsByResume("resume-1", "fake-bag-of-words-v1")).thenReturn(existing);
        Mockito.clearInvocations(embeddingModel, chunkStore);

        AiIndexViews.Rebuild second = service(true).rebuildOne("resume-1");

        assertEquals(0, second.embedded(), "内容未变不应再调用上游");
        assertEquals(second.chunks(), second.reused());
        verify(embeddingModel, never()).embedAll(anyList());
        verify(chunkStore).replace(eq("resume-1"), anyList());
    }

    @Test
    void vectorsAreScopedToCurrentModel() {
        when(resumeMapper.selectById("resume-1")).thenReturn(resume("resume-1", CONTENT));

        service(true).rebuildOne("resume-1");

        // 换 provider / 换模型后不得复用旧向量：查询必须带当前 model
        verify(chunkStore).findVectorsByResume("resume-1", "fake-bag-of-words-v1");
        verify(embeddingModel).embedAll(anyList());
    }

    @Test
    void rebuildAllClearsStaleChunksWhenContentIsBlank() {
        when(resumeMapper.selectList(any())).thenReturn(List.of(resume("resume-2", "   ")));

        AiIndexViews.Rebuild result = service(true).rebuildAll();

        assertEquals(0, result.resumes());
        assertEquals(1, result.skipped());
        assertEquals(0, result.chunks());
        verify(chunkStore).deleteByResume("resume-2");
        verify(chunkStore, never()).replace(anyString(), anyList());
    }

    @Test
    void disabledAiRejectsRebuildWithoutTouchingStorage() {
        BusinessException error = assertThrows(BusinessException.class, () -> service(false).rebuildAll());

        assertEquals(AiCodes.AI_DISABLED, error.getCode());
        verifyNoInteractions(resumeMapper, chunkStore, embeddingModel);
    }

    @Test
    void unknownResumeIsNotFoundAndBlankIdIsInvalid() {
        when(resumeMapper.selectById("missing")).thenReturn(null);

        assertEquals(ApiCodes.NOT_FOUND,
                assertThrows(BusinessException.class, () -> service(true).rebuildOne("missing")).getCode());
        assertEquals(ApiCodes.PARAM_INVALID,
                assertThrows(BusinessException.class, () -> service(true).rebuildOne("  ")).getCode());
    }

    @Test
    void statusReportsIndexedCountsAndTimestamp() {
        LocalDateTime updatedAt = LocalDateTime.of(2026, 10, 20, 8, 0, 0, 123_000_000);
        when(chunkStore.countByResume("resume-1", "fake-bag-of-words-v1")).thenReturn(5L);
        when(chunkStore.latestUpdatedAt("resume-1", "fake-bag-of-words-v1")).thenReturn(updatedAt);

        AiIndexViews.IndexStatus status = service(false).status("resume-1");

        assertTrue(status.indexed());
        assertEquals(5, status.chunkCount());
        assertEquals("fake-bag-of-words-v1", status.model());
        assertEquals(64, status.dim());
        assertEquals(updatedAt.toInstant(ZoneOffset.UTC).toEpochMilli(), status.updatedAt());
    }

    @Test
    void statusOfUnindexedResumeHasNoTimestamp() {
        when(chunkStore.countByResume("resume-9", "fake-bag-of-words-v1")).thenReturn(0L);

        AiIndexViews.IndexStatus status = service(true).status("resume-9");

        assertFalse(status.indexed());
        assertEquals(0, status.chunkCount());
        assertEquals(null, status.updatedAt());
    }

    @Test
    void chunkIdIsStableAndDependsOnSlot() {
        String id = ResumeIndexService.chunkId("resume-1", "basics", 0);

        assertTrue(id.startsWith("rc_"));
        assertEquals(35, id.length());
        assertEquals(id, ResumeIndexService.chunkId("resume-1", "basics", 0));
        assertNotEquals(id, ResumeIndexService.chunkId("resume-1", "basics", 1));
        assertNotEquals(id, ResumeIndexService.chunkId("resume-1", "skills", 0));
    }

    @SuppressWarnings("unchecked")
    private List<ResumeChunkEntity> capturedRows() {
        ArgumentCaptor<List<ResumeChunkEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(chunkStore).replace(eq("resume-1"), captor.capture());
        List<ResumeChunkEntity> rows = captor.getValue();
        assertNotNull(rows);
        return rows;
    }

    private ResumeIndexService service(boolean enabled) {
        return new ResumeIndexService(resumeMapper, chunkStore, new ResumeChunker(800), embeddingModel,
                new AiProperties(enabled, AiProperties.PROVIDER_FAKE, 800, 10, 6, 0.2, 700, 100));
    }

    private static ResumeEntity resume(String id, String content) {
        ResumeEntity resume = new ResumeEntity();
        resume.setId(id);
        resume.setUserId("u-cand-1");
        resume.setContent(content);
        return resume;
    }
}
