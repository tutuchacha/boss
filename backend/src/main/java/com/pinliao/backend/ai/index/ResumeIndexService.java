package com.pinliao.backend.ai.index;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pinliao.backend.ai.AiCodes;
import com.pinliao.backend.ai.config.AiProperties;
import com.pinliao.backend.ai.spi.AiEmbeddingModel;
import com.pinliao.backend.ai.view.AiIndexViews;
import com.pinliao.backend.application.entity.ResumeEntity;
import com.pinliao.backend.application.mapper.ResumeMapper;
import com.pinliao.backend.common.api.ApiCodes;
import com.pinliao.backend.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 简历索引服务（Step 4 T-AI1）。
 *
 * <p>链路：{@link ResumeChunker} 结构化切块 → 复用未变块的旧向量 → 只对新增 / 变更块调用
 * {@link AiEmbeddingModel} → 按简历整体替换 {@code resume_chunks}。</p>
 *
 * <p><b>可见范围</b>：本类只负责「建索引」，不返回任何简历内容；检索侧的跨租户过滤在 T-AI2
 * 用 {@code applications.company_id} 先取可见 resume_id（见 V9 注释），此处不做租户判断。</p>
 *
 * <p><b>费用控制</b>：内容哈希未变的块不再调用上游；{@code pinliao.ai.enabled=false} 时直接 42220，
 * 保证 CI / 无密钥机器零调用。</p>
 */
@Service
public class ResumeIndexService {

    private static final Logger log = LoggerFactory.getLogger(ResumeIndexService.class);
    private static final String ID_PREFIX = "rc_";
    private static final int ID_HASH_CHARS = 32;

    private final ResumeMapper resumeMapper;
    private final ResumeChunkStore chunkStore;
    private final ResumeChunker chunker;
    private final AiEmbeddingModel embeddingModel;
    private final AiProperties properties;

    public ResumeIndexService(ResumeMapper resumeMapper,
                              ResumeChunkStore chunkStore,
                              ResumeChunker chunker,
                              AiEmbeddingModel embeddingModel,
                              AiProperties properties) {
        this.resumeMapper = resumeMapper;
        this.chunkStore = chunkStore;
        this.chunker = chunker;
        this.embeddingModel = embeddingModel;
        this.properties = properties;
    }

    /** 全量重建（演示 / 初始化）。逐份替换，单份失败不会留下半份新旧混排。 */
    public AiIndexViews.Rebuild rebuildAll() {
        requireEnabled();
        long start = System.nanoTime();
        List<ResumeEntity> resumes = resumeMapper.selectList(new LambdaQueryWrapper<ResumeEntity>()
                .isNull(ResumeEntity::getDeletedAt));

        int indexed = 0;
        int skipped = 0;
        int chunks = 0;
        int embedded = 0;
        int reused = 0;
        for (ResumeEntity resume : resumes) {
            Outcome outcome = indexOne(resume);
            if (outcome.indexed()) {
                indexed++;
            } else {
                skipped++;
            }
            chunks += outcome.chunks();
            embedded += outcome.embedded();
            reused += outcome.reused();
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        log.info("AI 索引全量重建完成：resumes={} skipped={} chunks={} embedded={} reused={} elapsedMs={}",
                indexed, skipped, chunks, embedded, reused, elapsedMs);
        return new AiIndexViews.Rebuild("all", indexed, skipped, chunks, embedded, reused, elapsedMs);
    }

    /** 单份重建（简历保存 / 首次被 HR 打开时按需触发）。 */
    public AiIndexViews.Rebuild rebuildOne(String resumeId) {
        requireEnabled();
        if (resumeId == null || resumeId.isBlank()) {
            throw new BusinessException(ApiCodes.PARAM_INVALID, "resumeId 不能为空");
        }
        ResumeEntity resume = resumeMapper.selectById(resumeId);
        if (resume == null || resume.getDeletedAt() != null) {
            throw new BusinessException(ApiCodes.NOT_FOUND, "简历不存在");
        }
        long start = System.nanoTime();
        Outcome outcome = indexOne(resume);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        log.info("AI 索引单份重建：resumeId={} chunks={} embedded={} reused={} elapsedMs={}",
                resumeId, outcome.chunks(), outcome.embedded(), outcome.reused(), elapsedMs);
        return new AiIndexViews.Rebuild("resume", outcome.indexed() ? 1 : 0, outcome.indexed() ? 0 : 1,
                outcome.chunks(), outcome.embedded(), outcome.reused(), elapsedMs);
    }

    /** 索引状态（不调用上游，AI 关闭时也可读）；只统计当前模型。 */
    public AiIndexViews.IndexStatus status(String resumeId) {
        String model = embeddingModel.modelId();
        long chunkCount = chunkStore.countByResume(resumeId, model);
        Long updatedAt = null;
        if (chunkCount > 0) {
            LocalDateTime latest = chunkStore.latestUpdatedAt(resumeId, model);
            if (latest != null) {
                updatedAt = latest.toInstant(ZoneOffset.UTC).toEpochMilli();
            }
        }
        return new AiIndexViews.IndexStatus(resumeId, chunkCount > 0, chunkCount,
                model, embeddingModel.dimensions(), updatedAt);
    }

    private Outcome indexOne(ResumeEntity resume) {
        List<ResumeChunk> chunks = chunker.chunk(resume.getContent());
        if (chunks.isEmpty()) {
            // 简历被清空 / JSON 非法：旧索引必须一并清掉，避免检索到过期内容
            chunkStore.deleteByResume(resume.getId());
            return new Outcome(false, 0, 0, 0);
        }

        Map<String, float[]> vectors = new HashMap<>(
                chunkStore.findVectorsByResume(resume.getId(), embeddingModel.modelId()));
        List<String> missingHashes = new ArrayList<>();
        List<String> missingTexts = new ArrayList<>();
        for (ResumeChunk chunk : chunks) {
            String hash = chunk.contentHash();
            if (!vectors.containsKey(hash) && !missingHashes.contains(hash)) {
                missingHashes.add(hash);
                missingTexts.add(chunk.content());
            }
        }
        if (!missingHashes.isEmpty()) {
            vectors.putAll(chunkStore.findEmbeddings(missingHashes, embeddingModel.modelId()));
        }

        List<String> toEmbedHashes = new ArrayList<>();
        List<String> toEmbedTexts = new ArrayList<>();
        for (int i = 0; i < missingHashes.size(); i++) {
            if (!vectors.containsKey(missingHashes.get(i))) {
                toEmbedHashes.add(missingHashes.get(i));
                toEmbedTexts.add(missingTexts.get(i));
            }
        }
        List<float[]> embedded = toEmbedTexts.isEmpty()
                ? List.of()
                : embeddingModel.embedAll(toEmbedTexts);
        for (int i = 0; i < toEmbedHashes.size(); i++) {
            vectors.put(toEmbedHashes.get(i), embedded.get(i));
        }

        int embeddedChunks = 0;
        List<ResumeChunkEntity> rows = new ArrayList<>(chunks.size());
        for (ResumeChunk chunk : chunks) {
            float[] vector = vectors.get(chunk.contentHash());
            if (vector == null) {
                throw new BusinessException(AiCodes.UPSTREAM_FAILED, "向量化结果缺失，索引未写入");
            }
            if (toEmbedHashes.contains(chunk.contentHash())) {
                embeddedChunks++;
            }
            rows.add(toEntity(resume, chunk, vector));
        }
        chunkStore.replace(resume.getId(), rows);
        return new Outcome(true, rows.size(), embeddedChunks, rows.size() - embeddedChunks);
    }

    private ResumeChunkEntity toEntity(ResumeEntity resume, ResumeChunk chunk, float[] vector) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS);
        ResumeChunkEntity entity = new ResumeChunkEntity();
        entity.setId(chunkId(resume.getId(), chunk.section(), chunk.ordinal()));
        entity.setResumeId(resume.getId());
        entity.setUserId(resume.getUserId());
        entity.setSection(chunk.section());
        entity.setOrdinal(chunk.ordinal());
        entity.setTitle(chunk.title());
        entity.setContent(chunk.content());
        entity.setContentHash(chunk.contentHash());
        entity.setModel(embeddingModel.modelId());
        entity.setDim(vector.length);
        entity.setEmbedding(ResumeChunkStore.toJson(vector));
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return entity;
    }

    /** 分块 id 由「简历 + 段落 + 序号」决定：重建不会产生重复行，便于对比 before/after。 */
    static String chunkId(String resumeId, String section, int ordinal) {
        String hash = ResumeChunker.sha256Hex(resumeId + "|" + section + "|" + ordinal);
        return ID_PREFIX + hash.substring(0, ID_HASH_CHARS);
    }

    private void requireEnabled() {
        if (!properties.enabled()) {
            throw new BusinessException(AiCodes.AI_DISABLED, "AI 功能未启用（pinliao.ai.enabled=false）");
        }
    }

    private record Outcome(boolean indexed, int chunks, int embedded, int reused) {
    }
}
