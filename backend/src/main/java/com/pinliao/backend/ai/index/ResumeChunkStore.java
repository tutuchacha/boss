package com.pinliao.backend.ai.index;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * resume_chunks 的读写门面（Step 4 T-AI1）。
 *
 * <p>把「按简历整体替换」收在一个事务里：先删旧块再插新块，
 * 保证重建过程中不会出现「新旧混排」被检索到的窗口。</p>
 */
@Service
public class ResumeChunkStore {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int EMBEDDING_LOOKUP_LIMIT = 500;

    private final ResumeChunkMapper mapper;

    public ResumeChunkStore(ResumeChunkMapper mapper) {
        this.mapper = mapper;
    }

    /** 单份简历的全部块（按 section / ordinal 排序，供调试与状态回显）。 */
    public List<ResumeChunkEntity> findByResume(String resumeId) {
        return mapper.selectList(Wrappers.<ResumeChunkEntity>lambdaQuery()
                .eq(ResumeChunkEntity::getResumeId, resumeId)
                .orderByAsc(ResumeChunkEntity::getSection)
                .orderByAsc(ResumeChunkEntity::getOrdinal));
    }

    public long countByResume(String resumeId, String model) {
        return mapper.selectCount(Wrappers.<ResumeChunkEntity>lambdaQuery()
                .eq(ResumeChunkEntity::getResumeId, resumeId)
                .eq(ResumeChunkEntity::getModel, model));
    }

    /**
     * 单份简历在当前模型下的全部分块（T-AI2 检索用）。
     *
     * <p>必须带 {@code model}：换 provider / 换嵌入模型后旧向量语义不同，检索不能混用。</p>
     */
    public List<ResumeChunkEntity> findByResumeAndModel(String resumeId, String model) {
        return mapper.selectList(Wrappers.<ResumeChunkEntity>lambdaQuery()
                .eq(ResumeChunkEntity::getResumeId, resumeId)
                .eq(ResumeChunkEntity::getModel, model)
                .orderByAsc(ResumeChunkEntity::getSection)
                .orderByAsc(ResumeChunkEntity::getOrdinal));
    }

    /**
     * 多份简历在当前模型下的全部分块（T-AI4 语义人才检索用）。
     *
     * <p>调用方必须先通过 {@code applications.company_id} 得到可见 resumeId 集合，
     * 本方法只做 {@code IN} 前置过滤，不做任何租户判断（见 §5.3）。</p>
     */
    public List<ResumeChunkEntity> findByResumesAndModel(Collection<String> resumeIds, String model) {
        if (resumeIds == null || resumeIds.isEmpty()) {
            return List.of();
        }
        return mapper.selectList(Wrappers.<ResumeChunkEntity>lambdaQuery()
                .in(ResumeChunkEntity::getResumeId, resumeIds)
                .eq(ResumeChunkEntity::getModel, model)
                .orderByAsc(ResumeChunkEntity::getResumeId)
                .orderByAsc(ResumeChunkEntity::getSection)
                .orderByAsc(ResumeChunkEntity::getOrdinal));
    }

    /** 最近一次索引时间（仅统计当前模型）；未建索引返回 null。 */
    public LocalDateTime latestUpdatedAt(String resumeId, String model) {
        ResumeChunkEntity latest = mapper.selectOne(new LambdaQueryWrapper<ResumeChunkEntity>()
                .select(ResumeChunkEntity::getUpdatedAt)
                .eq(ResumeChunkEntity::getResumeId, resumeId)
                .eq(ResumeChunkEntity::getModel, model)
                .orderByDesc(ResumeChunkEntity::getUpdatedAt)
                .last("LIMIT 1"));
        return latest == null ? null : latest.getUpdatedAt();
    }

    /**
     * 按内容哈希复用已有向量（跨简历共享同一段文字时也能命中），避免重复计费。
     *
     * @return contentHash → 向量；未命中的哈希不出现在结果里
     */
    public Map<String, float[]> findEmbeddings(Collection<String> contentHashes, String model) {
        if (contentHashes == null || contentHashes.isEmpty()) {
            return Map.of();
        }
        List<ResumeChunkEntity> rows = mapper.selectList(new LambdaQueryWrapper<ResumeChunkEntity>()
                .select(ResumeChunkEntity::getContentHash, ResumeChunkEntity::getEmbedding)
                .in(ResumeChunkEntity::getContentHash, contentHashes)
                .eq(ResumeChunkEntity::getModel, model)
                .isNotNull(ResumeChunkEntity::getEmbedding)
                .last("LIMIT " + EMBEDDING_LOOKUP_LIMIT));
        return parseVectors(rows);
    }

    /**
     * 单份简历已有的「哈希 → 向量」；内容未变的块重建时直接复用，不再调用上游。
     *
     * <p>必须按模型过滤：切换 provider（fake ↔ 真实）或换嵌入模型后，旧向量语义不同，
     * 不能复用，否则检索结果会悄悄错乱。</p>
     */
    public Map<String, float[]> findVectorsByResume(String resumeId, String model) {
        List<ResumeChunkEntity> rows = mapper.selectList(new LambdaQueryWrapper<ResumeChunkEntity>()
                .select(ResumeChunkEntity::getContentHash, ResumeChunkEntity::getEmbedding)
                .eq(ResumeChunkEntity::getResumeId, resumeId)
                .eq(ResumeChunkEntity::getModel, model)
                .isNotNull(ResumeChunkEntity::getEmbedding));
        return parseVectors(rows);
    }

    private static Map<String, float[]> parseVectors(List<ResumeChunkEntity> rows) {
        Map<String, float[]> vectors = new HashMap<>();
        for (ResumeChunkEntity row : rows) {
            if (row.getContentHash() == null || vectors.containsKey(row.getContentHash())) {
                continue;
            }
            try {
                vectors.put(row.getContentHash(), JSON.readValue(row.getEmbedding(), float[].class));
            } catch (Exception ignored) {
                // 单条脏数据不阻塞整体重建：该哈希视为未命中，后续重新嵌入覆盖
            }
        }
        return vectors;
    }

    /** 按简历整体替换（事务内先删后插）。 */
    @Transactional
    public void replace(String resumeId, List<ResumeChunkEntity> rows) {
        deleteByResume(resumeId);
        for (ResumeChunkEntity row : rows) {
            mapper.insert(row);
        }
    }

    public void deleteByResume(String resumeId) {
        mapper.delete(Wrappers.<ResumeChunkEntity>lambdaQuery()
                .eq(ResumeChunkEntity::getResumeId, resumeId));
    }

    /** 向量 → JSON 数组字符串（resume_chunks.embedding）。 */
    public static String toJson(float[] vector) {
        try {
            return JSON.writeValueAsString(vector);
        } catch (Exception e) {
            throw new IllegalStateException("向量序列化失败", e);
        }
    }
}
