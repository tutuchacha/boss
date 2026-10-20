package com.pinliao.backend.ai.qa;

import com.pinliao.backend.ai.AiCodes;
import com.pinliao.backend.ai.config.AiProperties;
import com.pinliao.backend.ai.index.ResumeChunkStore;
import com.pinliao.backend.ai.index.ResumeIndexService;
import com.pinliao.backend.ai.rag.CitationAssembler;
import com.pinliao.backend.ai.rag.PromptBuilder;
import com.pinliao.backend.ai.rag.RetrievalService;
import com.pinliao.backend.ai.spi.AiChatClient;
import com.pinliao.backend.ai.spi.AiEmbeddingModel;
import com.pinliao.backend.ai.view.AiQaViews;
import com.pinliao.backend.application.entity.ApplicationEntity;
import com.pinliao.backend.application.mapper.ApplicationMapper;
import com.pinliao.backend.auth.context.Roles;
import com.pinliao.backend.auth.context.UserContext;
import com.pinliao.backend.common.api.ApiCodes;
import com.pinliao.backend.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 单简历问答（Step 4 T-AI2）。
 *
 * <p>链路：校验开关 / 角色 / 归属 → 确保索引就绪 → 向量检索 topK → 组装带编号的资料 Prompt
 * → 对话模型生成 → 把 {@code [n]} 映射为引用。回答只能基于**该 HR 本公司可见的投递简历**。</p>
 *
 * <p><b>多租户红线</b>：{@code applications} 是租户表，{@code selectById} 会被租户插件追加
 * {@code company_id}；本类仍显式比对一次 companyId，保证纯 Mockito 单测（无插件）也覆盖越权。</p>
 *
 * <p><b>费用控制</b>：相似度低于 {@code qa-min-score} 时直接返回「简历中没有相关信息」，
 * 不调用对话模型；每日次数由 {@link AiQuotaService} 限制（42902）。</p>
 */
@Service
public class HrResumeQaService {

    private static final Logger log = LoggerFactory.getLogger(HrResumeQaService.class);
    private static final int QUESTION_MAX_CHARS = 500;

    private final AiProperties properties;
    private final ApplicationMapper applicationMapper;
    private final ResumeChunkStore chunkStore;
    private final ResumeIndexService indexService;
    private final AiEmbeddingModel embeddingModel;
    private final RetrievalService retrievalService;
    private final PromptBuilder promptBuilder;
    private final CitationAssembler citationAssembler;
    private final AiChatClient chatClient;
    private final AiQuotaService quotaService;

    public HrResumeQaService(AiProperties properties,
                             ApplicationMapper applicationMapper,
                             ResumeChunkStore chunkStore,
                             ResumeIndexService indexService,
                             AiEmbeddingModel embeddingModel,
                             RetrievalService retrievalService,
                             PromptBuilder promptBuilder,
                             CitationAssembler citationAssembler,
                             AiChatClient chatClient,
                             AiQuotaService quotaService) {
        this.properties = properties;
        this.applicationMapper = applicationMapper;
        this.chunkStore = chunkStore;
        this.indexService = indexService;
        this.embeddingModel = embeddingModel;
        this.retrievalService = retrievalService;
        this.promptBuilder = promptBuilder;
        this.citationAssembler = citationAssembler;
        this.chatClient = chatClient;
        this.quotaService = quotaService;
    }

    public AiQaViews.Answer ask(String applicationId, String question) {
        requireEnabled();
        UserContext.CurrentUser user = Roles.requireManager();
        String normalized = normalizeQuestion(question);
        ApplicationEntity application = requireVisibleApplication(applicationId, user.companyId());
        quotaService.consume(user.userId());

        long start = System.nanoTime();
        String resumeId = application.getResumeId();
        if (resumeId == null || resumeId.isBlank()) {
            throw new BusinessException(AiCodes.INDEX_NOT_READY, "该投递没有可检索的简历");
        }
        ensureIndexed(resumeId);

        List<RetrievalService.Hit> hits = retrievalService.retrieve(resumeId, normalized);
        double topScore = hits.isEmpty() ? 0 : hits.get(0).score();
        if (hits.isEmpty() || topScore < properties.qaMinScore()) {
            long elapsedMs = elapsedMs(start);
            log.info("AI 简历问答（无依据）：applicationId={} userId={} questionLength={} retrieved={} elapsedMs={}",
                    applicationId, user.userId(), normalized.length(), hits.size(), elapsedMs);
            return new AiQaViews.Answer(PromptBuilder.NO_EVIDENCE_ANSWER, false, List.of(),
                    chatClient.modelId(), elapsedMs, null, hits.size());
        }

        PromptBuilder.Prompt prompt = promptBuilder.build(normalized, hits);
        AiChatClient.ChatResult result = chatClient.chat(prompt.system(), prompt.user());
        CitationAssembler.Assembly assembly = citationAssembler.assemble(result.text(), hits);
        long elapsedMs = elapsedMs(start);

        // 审计只记录长度 / 条数 / 耗时 / token，不记录简历原文与完整 Prompt（§5.3）
        log.info("AI 简历问答：applicationId={} userId={} questionLength={} retrieved={} citations={} "
                        + "answerLength={} tokens={} elapsedMs={}",
                applicationId, user.userId(), normalized.length(), hits.size(), assembly.citations().size(),
                assembly.answer().length(), result.totalTokens(), elapsedMs);

        return new AiQaViews.Answer(assembly.answer(), !assembly.citations().isEmpty(),
                assembly.citations(), chatClient.modelId(), elapsedMs, result.totalTokens(), hits.size());
    }

    /** 索引未就绪时按需建一次（首次提问自动补齐；仍为空则 42221）。 */
    private void ensureIndexed(String resumeId) {
        String model = embeddingModel.modelId();
        if (chunkStore.countByResume(resumeId, model) > 0) {
            return;
        }
        indexService.rebuildOne(resumeId);
        if (chunkStore.countByResume(resumeId, model) == 0) {
            throw new BusinessException(AiCodes.INDEX_NOT_READY, "简历索引未就绪，请稍后重试");
        }
    }

    private ApplicationEntity requireVisibleApplication(String applicationId, String companyId) {
        ApplicationEntity application = applicationId == null || applicationId.isBlank()
                ? null
                : applicationMapper.selectById(applicationId);
        // 不存在 / 非本公司 / 已软删统一 40401，不泄露存在性（对齐 T4/T8 口径）
        if (application == null || application.getDeletedAt() != null
                || !companyId.equals(application.getCompanyId())) {
            throw new BusinessException(ApiCodes.NOT_FOUND, "投递不存在");
        }
        return application;
    }

    private static String normalizeQuestion(String question) {
        if (question == null || question.isBlank()) {
            throw new BusinessException(ApiCodes.FIELD_VALIDATION, "问题不能为空");
        }
        String trimmed = question.trim();
        if (trimmed.length() > QUESTION_MAX_CHARS) {
            throw new BusinessException(ApiCodes.FIELD_VALIDATION,
                    "问题过长（最多 " + QUESTION_MAX_CHARS + " 字）");
        }
        return trimmed;
    }

    private void requireEnabled() {
        if (!properties.enabled()) {
            throw new BusinessException(AiCodes.AI_DISABLED, "AI 功能未启用（pinliao.ai.enabled=false）");
        }
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
