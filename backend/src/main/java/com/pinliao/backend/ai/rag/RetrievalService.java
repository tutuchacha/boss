package com.pinliao.backend.ai.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pinliao.backend.ai.AiCodes;
import com.pinliao.backend.ai.config.AiProperties;
import com.pinliao.backend.ai.index.ResumeChunkEntity;
import com.pinliao.backend.ai.index.ResumeChunkStore;
import com.pinliao.backend.ai.spi.AiEmbeddingModel;
import com.pinliao.backend.common.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 单简历向量检索（Step 4 T-AI2）。
 *
 * <p>只接收「已经通过可见性校验的 resumeId」，本类不处理租户逻辑；
 * 跨公司过滤在 {@code HrResumeQaService} 用 {@code applications.company_id} 先做（见 §5.3）。</p>
 *
 * <p>数据规模：演示库单份简历 5 个分块，直接内存余弦即可；
 * 上量后把过滤条件推 SQL / 换向量库，接口签名不变。</p>
 */
@Service
public class RetrievalService {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final ResumeChunkStore chunkStore;
    private final AiEmbeddingModel embeddingModel;
    private final AiProperties properties;

    public RetrievalService(ResumeChunkStore chunkStore,
                            AiEmbeddingModel embeddingModel,
                            AiProperties properties) {
        this.chunkStore = chunkStore;
        this.embeddingModel = embeddingModel;
        this.properties = properties;
    }

    /**
     * @param resumeId 已校验归属的简历 id
     * @param question HR 问题
     * @return 按相似度倒序的分块；索引为空返回空列表
     * @throws BusinessException 42221 索引维度与当前模型不一致（需重建）
     */
    public List<Hit> retrieve(String resumeId, String question) {
        List<ResumeChunkEntity> chunks = chunkStore.findByResumeAndModel(resumeId, embeddingModel.modelId());
        if (chunks.isEmpty()) {
            return List.of();
        }
        float[] query = embedQuestion(question);

        List<Hit> hits = new ArrayList<>(chunks.size());
        int dimensionMismatch = 0;
        for (ResumeChunkEntity chunk : chunks) {
            if (chunk.getEmbedding() == null || chunk.getDim() == null) {
                dimensionMismatch++;
                continue;
            }
            float[] vector = parseVector(chunk.getEmbedding());
            if (vector.length != query.length) {
                dimensionMismatch++;
                continue;
            }
            hits.add(new Hit(chunk.getId(), chunk.getResumeId(), chunk.getSection(), chunk.getTitle(),
                    chunk.getContent(), cosine(query, vector)));
        }
        if (hits.isEmpty() && dimensionMismatch > 0) {
            throw new BusinessException(AiCodes.INDEX_NOT_READY,
                    "简历索引维度与当前向量模型不一致，请重建索引");
        }
        hits.sort(Comparator.comparingDouble(Hit::score).reversed()
                .thenComparing(Hit::chunkId));
        int topK = Math.max(1, properties.qaTopK());
        return hits.size() <= topK ? List.copyOf(hits) : List.copyOf(hits.subList(0, topK));
    }

    private float[] embedQuestion(String question) {
        List<float[]> vectors = embeddingModel.embedAll(List.of(question));
        if (vectors == null || vectors.isEmpty() || vectors.get(0) == null) {
            throw new BusinessException(AiCodes.UPSTREAM_FAILED, "问题向量化失败");
        }
        return vectors.get(0);
    }

    /** 向量 JSON → float[]（T-AI4 语义检索复用；损坏时 42221）。 */
    public static float[] parseVector(String json) {
        try {
            return JSON.readValue(json, float[].class);
        } catch (Exception e) {
            throw new BusinessException(AiCodes.INDEX_NOT_READY, "简历索引向量损坏，请重建索引");
        }
    }

    /** 余弦相似度；任一向量为零向量时返回 0（不抛错，交给最低分阈值处理）。T-AI4 语义检索复用。 */
    public static double cosine(float[] a, float[] b) {
        double dot = 0;
        double normA = 0;
        double normB = 0;
        int length = Math.min(a.length, b.length);
        for (int i = 0; i < length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return 0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * 检索命中。
     *
     * @param content 分块原文（含上下文标题行），只在本进程内传给 Prompt，不直接下发前端
     * @param score   余弦相似度
     */
    public record Hit(String chunkId,
                      String resumeId,
                      String section,
                      String title,
                      String content,
                      double score) {
    }
}
