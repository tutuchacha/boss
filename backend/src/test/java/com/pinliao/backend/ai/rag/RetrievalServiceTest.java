package com.pinliao.backend.ai.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pinliao.backend.ai.AiCodes;
import com.pinliao.backend.ai.config.AiProperties;
import com.pinliao.backend.ai.index.ResumeChunkEntity;
import com.pinliao.backend.ai.index.ResumeChunkStore;
import com.pinliao.backend.ai.spi.AiEmbeddingModel;
import com.pinliao.backend.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * T-AI2：单简历向量检索（排序 / topK / 模型隔离 / 维度不一致）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RetrievalServiceTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String MODEL = "fake-bag-of-words-v1";

    @Mock
    private ResumeChunkStore chunkStore;
    @Mock
    private AiEmbeddingModel embeddingModel;

    @BeforeEach
    void setUp() {
        when(embeddingModel.modelId()).thenReturn(MODEL);
        when(embeddingModel.embedAll(anyList())).thenReturn(List.of(new float[]{1, 0, 0}));
    }

    @Test
    void 按余弦相似度倒序并在topK处截断() {
        when(chunkStore.findByResumeAndModel("resume-1", MODEL)).thenReturn(List.of(
                chunk("rc_b", "B", new float[]{0, 1, 0}),
                chunk("rc_a", "A", new float[]{1, 0, 0}),
                chunk("rc_c", "C", new float[]{0.6f, 0.8f, 0})));

        List<RetrievalService.Hit> hits = service(2).retrieve("resume-1", "问题");

        assertEquals(2, hits.size());
        assertEquals("rc_a", hits.get(0).chunkId());
        assertEquals(1.0, hits.get(0).score(), 0.000001);
        assertEquals("rc_c", hits.get(1).chunkId());
    }

    @Test
    void 索引为空返回空列表且不调用向量模型() {
        when(chunkStore.findByResumeAndModel("resume-1", MODEL)).thenReturn(List.of());

        assertTrue(service(6).retrieve("resume-1", "问题").isEmpty());
        org.mockito.Mockito.verify(embeddingModel, org.mockito.Mockito.never()).embedAll(anyList());
    }

    @Test
    void 维度与当前模型不一致时返回42221() {
        when(chunkStore.findByResumeAndModel("resume-1", MODEL)).thenReturn(List.of(
                chunk("rc_a", "A", new float[]{1, 0})));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service(6).retrieve("resume-1", "问题"));

        assertEquals(AiCodes.INDEX_NOT_READY, error.getCode());
    }

    @Test
    void 检索必须带当前模型() {
        when(chunkStore.findByResumeAndModel(anyString(), anyString())).thenReturn(List.of());

        service(6).retrieve("resume-1", "问题");

        org.mockito.Mockito.verify(chunkStore).findByResumeAndModel("resume-1", MODEL);
    }

    @Test
    void 余弦相似度边界() {
        assertEquals(1.0, RetrievalService.cosine(new float[]{1, 0}, new float[]{1, 0}), 0.000001);
        assertEquals(0.0, RetrievalService.cosine(new float[]{1, 0}, new float[]{0, 1}), 0.000001);
        assertEquals(0.0, RetrievalService.cosine(new float[]{0, 0}, new float[]{1, 0}), 0.000001);
    }

    private RetrievalService service(int topK) {
        return new RetrievalService(chunkStore, embeddingModel,
                new AiProperties(true, AiProperties.PROVIDER_FAKE, 800, 10, topK, 0.2, 700, 100, 8, 0.15));
    }

    private static ResumeChunkEntity chunk(String id, String title, float[] vector) {
        ResumeChunkEntity entity = new ResumeChunkEntity();
        entity.setId(id);
        entity.setResumeId("resume-1");
        entity.setSection("experiences");
        entity.setOrdinal(0);
        entity.setTitle(title);
        entity.setContent(title + "\n正文");
        entity.setModel(MODEL);
        entity.setDim(vector.length);
        try {
            entity.setEmbedding(JSON.writeValueAsString(vector));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return entity;
    }
}
