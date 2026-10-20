package com.pinliao.backend.ai.search;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pinliao.backend.ai.AiCodes;
import com.pinliao.backend.ai.config.AiProperties;
import com.pinliao.backend.ai.dto.AiSearchRequests;
import com.pinliao.backend.ai.index.ResumeChunkEntity;
import com.pinliao.backend.ai.index.ResumeChunkStore;
import com.pinliao.backend.ai.index.ResumeIndexService;
import com.pinliao.backend.ai.qa.AiQuotaService;
import com.pinliao.backend.ai.rag.RetrievalService;
import com.pinliao.backend.ai.spi.AiEmbeddingModel;
import com.pinliao.backend.ai.view.AiSearchViews;
import com.pinliao.backend.application.entity.ApplicationEntity;
import com.pinliao.backend.application.mapper.ApplicationMapper;
import com.pinliao.backend.auth.context.Roles;
import com.pinliao.backend.auth.context.UserContext;
import com.pinliao.backend.common.api.ApiCodes;
import com.pinliao.backend.common.exception.BusinessException;
import com.pinliao.backend.job.entity.JobEntity;
import com.pinliao.backend.job.mapper.JobMapper;
import com.pinliao.backend.recruiter.RecruiterCodes;
import com.pinliao.backend.recruiter.service.CandidateSummaryService;
import com.pinliao.backend.recruiter.view.RecruiterViews;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 语义人才检索（Step 4 T-AI4）：与关键词版 {@code TalentMatchService} 并行，供筛选页 A/B 对比。
 *
 * <p>链路：需求文本 + 标签 → 一次向量化 → 在公司可见的投递候选人范围内做分块余弦检索
 * → 按候选人聚合最高分与若干证据片段 → 按相似度倒序返回。</p>
 *
 * <p><b>多租户红线</b>：先用 {@code applications.company_id = 当前公司 AND deleted_at IS NULL}
 * 取可见 resumeId 集合，再以 {@code resume_chunks.resume_id IN (...)} 做 SQL 前置过滤；
 * 不存在「先全局检索再丢弃」的路径（见 {@code docs/step4-ai-plan.md} §5.3）。</p>
 *
 * <p><b>与关键词版的差异（诚实口径）</b>：本接口只做向量相似度，不理解「必须 / 优先」等约束，
 * 也不要求关键词字面出现；真实语义效果取决于当前嵌入模型。{@code provider=fake} 只用于管道验证，
 * 其向量是词袋哈希、不具语义。</p>
 *
 * <p><b>费用控制</b>：每次检索只向量化一次查询；候选人索引缺失时按需补建（单份失败不影响整体）；
 * 与问答共用按 HR / 按日配额（42902），Redis 故障降级放行。</p>
 */
@Service
public class SemanticTalentSearchService {

    private static final Logger log = LoggerFactory.getLogger(SemanticTalentSearchService.class);

    private static final int QUERY_MAX_CHARS = 200;
    private static final int MAX_TAGS = 20;
    private static final int TAG_MAX_CHARS = 40;
    private static final int DEFAULT_LIMIT = 8;
    private static final int MAX_LIMIT = 20;
    private static final int EVIDENCES_PER_CANDIDATE = 3;
    private static final int SNIPPET_MAX_CHARS = 120;

    private final AiProperties properties;
    private final ApplicationMapper applicationMapper;
    private final ResumeChunkStore chunkStore;
    private final ResumeIndexService indexService;
    private final AiEmbeddingModel embeddingModel;
    private final AiQuotaService quotaService;
    private final JobMapper jobMapper;
    private final CandidateSummaryService candidateSummaryService;

    public SemanticTalentSearchService(AiProperties properties,
                                       ApplicationMapper applicationMapper,
                                       ResumeChunkStore chunkStore,
                                       ResumeIndexService indexService,
                                       AiEmbeddingModel embeddingModel,
                                       AiQuotaService quotaService,
                                       JobMapper jobMapper,
                                       CandidateSummaryService candidateSummaryService) {
        this.properties = properties;
        this.applicationMapper = applicationMapper;
        this.chunkStore = chunkStore;
        this.indexService = indexService;
        this.embeddingModel = embeddingModel;
        this.quotaService = quotaService;
        this.jobMapper = jobMapper;
        this.candidateSummaryService = candidateSummaryService;
    }

    /**
     * 按语义检索候选人。
     *
     * @param input query / tags（至少一个非空）、可选 limit
     * @return 按相似度倒序的候选人；无命中返回空列表
     * @throws BusinessException 42220 未启用 / 42201 入参非法 / 42210 条件为空 / 42902 超配额 /
     *                           42221 索引维度不一致 / 50020 上游失败
     */
    public List<AiSearchViews.SemanticMatch> search(AiSearchRequests.SemanticSearch input) {
        long start = System.nanoTime();
        requireEnabled();
        UserContext.CurrentUser user = Roles.requireManager();

        String query = normalizeQuery(input == null ? null : input.query());
        List<String> tags = normalizeTags(input == null ? null : input.tags());
        if (query.isEmpty() && tags.isEmpty()) {
            throw new BusinessException(RecruiterCodes.EMPTY_CRITERIA, "请填写技能需求或选择技能标签");
        }
        int limit = normalizeLimit(input == null ? null : input.limit());
        quotaService.consume(user.userId());

        Map<String, ApplicationEntity> applicationByResume = visibleApplications(user.companyId());
        if (applicationByResume.isEmpty()) {
            return List.of();
        }
        ensureIndexed(applicationByResume.keySet());

        String model = embeddingModel.modelId();
        List<ResumeChunkEntity> chunks = chunkStore.findByResumesAndModel(applicationByResume.keySet(), model);
        if (chunks.isEmpty()) {
            log.info("AI 语义人才检索（无可检索索引）：companyId={} userId={} queryLength={} tags={}",
                    user.companyId(), user.userId(), query.length(), tags.size());
            return List.of();
        }

        float[] queryVector = embedQuery(composeQuery(query, tags));
        Map<String, List<ChunkHit>> hitsByResume = new LinkedHashMap<>();
        int dimensionMismatch = 0;
        for (ResumeChunkEntity chunk : chunks) {
            if (chunk.getEmbedding() == null || chunk.getDim() == null) {
                dimensionMismatch++;
                continue;
            }
            float[] vector = RetrievalService.parseVector(chunk.getEmbedding());
            if (vector.length != queryVector.length) {
                dimensionMismatch++;
                continue;
            }
            double score = RetrievalService.cosine(queryVector, vector);
            if (score < properties.searchMinScore()) {
                continue;
            }
            hitsByResume.computeIfAbsent(chunk.getResumeId(), key -> new ArrayList<>())
                    .add(new ChunkHit(chunk, score));
        }
        if (hitsByResume.isEmpty() && dimensionMismatch > 0) {
            throw new BusinessException(AiCodes.INDEX_NOT_READY,
                    "简历索引维度与当前向量模型不一致，请重建索引");
        }

        Map<String, AiSearchViews.SemanticMatch> bestByUser = new LinkedHashMap<>();
        for (Map.Entry<String, List<ChunkHit>> entry : hitsByResume.entrySet()) {
            ApplicationEntity application = applicationByResume.get(entry.getKey());
            if (application == null) {
                continue;
            }
            List<ChunkHit> hits = entry.getValue();
            hits.sort(Comparator.comparingDouble(ChunkHit::score).reversed()
                    .thenComparing(hit -> hit.chunk().getId()));
            AiSearchViews.SemanticMatch candidate = toMatch(application, hits);
            bestByUser.merge(application.getUserId(), candidate,
                    (existing, incoming) -> existing.score() >= incoming.score() ? existing : incoming);
        }

        List<AiSearchViews.SemanticMatch> results = new ArrayList<>(bestByUser.values());
        results.sort(Comparator.comparingDouble(AiSearchViews.SemanticMatch::score).reversed()
                .thenComparing(Comparator.comparingInt(
                        (AiSearchViews.SemanticMatch match) -> completenessOf(match)).reversed()));
        if (results.size() > limit) {
            results = new ArrayList<>(results.subList(0, limit));
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        log.info("AI 语义人才检索：companyId={} userId={} queryLength={} tags={} resumes={} chunks={} "
                        + "candidates={} topScore={} elapsedMs={}",
                user.companyId(), user.userId(), query.length(), tags.size(),
                applicationByResume.size(), chunks.size(), results.size(),
                results.isEmpty() ? 0 : results.get(0).score(), elapsedMs);
        return List.copyOf(results);
    }

    // ------------------------------------------------------------------
    // 数据装配
    // ------------------------------------------------------------------

    /**
     * 当前公司可见的投递 → resumeId 映射（同一简历多条投递时取最近一条）。
     *
     * <p>{@code applications} 是租户表，插件会追加 {@code company_id}；这里仍显式带一次条件，
     * 保证纯 Mockito 单测（无插件）也覆盖越权。</p>
     */
    private Map<String, ApplicationEntity> visibleApplications(String companyId) {
        List<ApplicationEntity> rows = new ArrayList<>(applicationMapper.selectList(
                new LambdaQueryWrapper<ApplicationEntity>()
                        .eq(ApplicationEntity::getCompanyId, companyId)
                        .isNull(ApplicationEntity::getDeletedAt)));
        rows.sort(Comparator.comparing(ApplicationEntity::getUpdatedAt,
                        Comparator.nullsFirst(Comparator.naturalOrder()))
                .reversed()
                .thenComparing(ApplicationEntity::getId));
        Map<String, ApplicationEntity> byResume = new LinkedHashMap<>();
        for (ApplicationEntity row : rows) {
            if (row.getResumeId() != null && !row.getResumeId().isBlank()) {
                byResume.putIfAbsent(row.getResumeId(), row);
            }
        }
        return byResume;
    }

    /** 索引缺失的简历按需补建；单份简历已删除 / 无内容时跳过，不影响整体检索。 */
    private void ensureIndexed(Set<String> resumeIds) {
        String model = embeddingModel.modelId();
        for (String resumeId : resumeIds) {
            if (chunkStore.countByResume(resumeId, model) > 0) {
                continue;
            }
            try {
                indexService.rebuildOne(resumeId);
            } catch (BusinessException e) {
                if (e.getCode() == ApiCodes.NOT_FOUND) {
                    log.warn("AI 语义人才检索：简历不可索引，已跳过 resumeId={}", resumeId);
                    continue;
                }
                throw e;
            }
        }
    }

    private AiSearchViews.SemanticMatch toMatch(ApplicationEntity application, List<ChunkHit> hits) {
        RecruiterViews.Candidate candidate = candidateSummaryService.summarise(application.getUserId());
        List<AiSearchViews.Evidence> evidences = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (ChunkHit hit : hits) {
            if (evidences.size() >= EVIDENCES_PER_CANDIDATE) {
                break;
            }
            ResumeChunkEntity chunk = hit.chunk();
            if (!seen.add(chunk.getId())) {
                continue;
            }
            evidences.add(new AiSearchViews.Evidence(chunk.getId(), chunk.getSection(), chunk.getTitle(),
                    snippetOf(chunk.getContent()), round(hit.score())));
        }
        return new AiSearchViews.SemanticMatch(application.getId(), candidate,
                jobTitle(application.getJobId()), round(hits.get(0).score()), List.copyOf(evidences));
    }

    private String jobTitle(String jobId) {
        JobEntity job = jobMapper.selectById(jobId);
        return job == null || job.getTitle() == null ? "职位已删除" : job.getTitle();
    }

    private float[] embedQuery(String text) {
        List<float[]> vectors = embeddingModel.embedAll(List.of(text));
        if (vectors == null || vectors.isEmpty() || vectors.get(0) == null) {
            throw new BusinessException(AiCodes.UPSTREAM_FAILED, "需求向量化失败");
        }
        return vectors.get(0);
    }

    // ------------------------------------------------------------------
    // 入参校验
    // ------------------------------------------------------------------

    private static String normalizeQuery(String query) {
        String trimmed = query == null ? "" : query.trim();
        if (trimmed.length() > QUERY_MAX_CHARS) {
            throw new BusinessException(ApiCodes.FIELD_VALIDATION,
                    "技能需求过长（最多 " + QUERY_MAX_CHARS + " 字）");
        }
        return trimmed;
    }

    /** 去空白 / 去重（大小写不敏感），保留展示原文；超过上限按 42201 拒绝。 */
    private static List<String> normalizeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        if (tags.size() > MAX_TAGS) {
            throw new BusinessException(ApiCodes.FIELD_VALIDATION, "技能标签最多 " + MAX_TAGS + " 个");
        }
        Set<String> seen = new LinkedHashSet<>();
        List<String> normalized = new ArrayList<>();
        for (String tag : tags) {
            String value = tag == null ? "" : tag.trim();
            if (value.isEmpty()) {
                continue;
            }
            if (value.length() > TAG_MAX_CHARS) {
                throw new BusinessException(ApiCodes.FIELD_VALIDATION,
                        "单个技能标签最多 " + TAG_MAX_CHARS + " 字");
            }
            if (seen.add(value.toLowerCase(Locale.ROOT))) {
                normalized.add(value);
            }
        }
        return List.copyOf(normalized);
    }

    private int normalizeLimit(Integer raw) {
        int fallback = properties.searchTopK() > 0 ? properties.searchTopK() : DEFAULT_LIMIT;
        if (raw == null) {
            return Math.min(fallback, MAX_LIMIT);
        }
        if (raw <= 0) {
            throw new BusinessException(ApiCodes.FIELD_VALIDATION, "limit 必须大于 0");
        }
        return Math.min(raw, MAX_LIMIT);
    }

    private static String composeQuery(String query, List<String> tags) {
        if (tags.isEmpty()) {
            return query;
        }
        return query.isEmpty() ? String.join(" ", tags) : query + " " + String.join(" ", tags);
    }

    private void requireEnabled() {
        if (!properties.enabled()) {
            throw new BusinessException(AiCodes.AI_DISABLED, "AI 功能未启用（pinliao.ai.enabled=false）");
        }
    }

    // ------------------------------------------------------------------
    // 小工具
    // ------------------------------------------------------------------

    /** 去掉分块首行标题后的正文片段（前端直接展示，不做关键词高亮）。 */
    static String snippetOf(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        int lineBreak = content.indexOf('\n');
        String body = (lineBreak >= 0 ? content.substring(lineBreak + 1) : content)
                .replaceAll("\\s+", " ")
                .trim();
        return body.length() <= SNIPPET_MAX_CHARS ? body : body.substring(0, SNIPPET_MAX_CHARS) + "…";
    }

    private static double round(double score) {
        return Math.round(score * 10_000d) / 10_000d;
    }

    private static int completenessOf(AiSearchViews.SemanticMatch match) {
        return match.candidate().resumeCompleteness() == null ? 0 : match.candidate().resumeCompleteness();
    }

    /** 单个分块的命中（分块 + 相似度）。 */
    private record ChunkHit(ResumeChunkEntity chunk, double score) {
    }
}
