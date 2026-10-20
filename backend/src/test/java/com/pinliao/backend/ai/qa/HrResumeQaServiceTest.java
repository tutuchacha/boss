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
import com.pinliao.backend.auth.context.UserContext;
import com.pinliao.backend.common.api.ApiCodes;
import com.pinliao.backend.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * T-AI2：单简历问答编排（开关 / 角色 / 租户归属 / 索引兜底 / 无依据短路 / 引用 / 配额 / 错误透传）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HrResumeQaServiceTest {

    private static final String COMPANY_ID = "co-test";
    private static final String MODEL = "fake-bag-of-words-v1";
    private static final String NO_EVIDENCE = PromptBuilder.NO_EVIDENCE_ANSWER;

    @Mock
    private ApplicationMapper applicationMapper;
    @Mock
    private ResumeChunkStore chunkStore;
    @Mock
    private ResumeIndexService indexService;
    @Mock
    private AiEmbeddingModel embeddingModel;
    @Mock
    private RetrievalService retrievalService;
    @Mock
    private AiChatClient chatClient;
    @Mock
    private AiQuotaService quotaService;

    @BeforeEach
    void setUp() {
        UserContext.set(new UserContext.CurrentUser("u-hr-1", "HR", COMPANY_ID, "jti", Instant.now().plusSeconds(60)));
        when(embeddingModel.modelId()).thenReturn(MODEL);
        when(chatClient.modelId()).thenReturn("fake-chat-v1");
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void 关闭时_42220且不触碰任何依赖() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> service(false).ask("app-1", "他做过什么？"));

        assertEquals(AiCodes.AI_DISABLED, error.getCode());
        verifyNoInteractions(applicationMapper, chunkStore, indexService, retrievalService, chatClient, quotaService);
    }

    @Test
    void 空问题与超长问题_42201() {
        assertEquals(ApiCodes.FIELD_VALIDATION,
                assertThrows(BusinessException.class, () -> service(true).ask("app-1", "  ")).getCode());
        assertEquals(ApiCodes.FIELD_VALIDATION,
                assertThrows(BusinessException.class, () -> service(true).ask("app-1", "问".repeat(501))).getCode());
        verifyNoInteractions(applicationMapper, quotaService);
    }

    @Test
    void 不存在的投递_40401() {
        when(applicationMapper.selectById("app-1")).thenReturn(null);

        assertEquals(ApiCodes.NOT_FOUND,
                assertThrows(BusinessException.class, () -> service(true).ask("app-1", "问题")).getCode());
    }

    @Test
    void 跨公司投递_40401且不消耗配额() {
        ApplicationEntity app = application("app-1", "co-other", "resume-1");
        when(applicationMapper.selectById("app-1")).thenReturn(app);

        assertEquals(ApiCodes.NOT_FOUND,
                assertThrows(BusinessException.class, () -> service(true).ask("app-1", "问题")).getCode());
        verify(quotaService, never()).consume(anyString());
    }

    @Test
    void 投递没有简历_42221() {
        when(applicationMapper.selectById("app-1")).thenReturn(application("app-1", COMPANY_ID, null));

        assertEquals(AiCodes.INDEX_NOT_READY,
                assertThrows(BusinessException.class, () -> service(true).ask("app-1", "问题")).getCode());
    }

    @Test
    void 索引为空时按需重建_仍为空则42221() {
        when(applicationMapper.selectById("app-1")).thenReturn(application("app-1", COMPANY_ID, "resume-1"));
        when(chunkStore.countByResume("resume-1", MODEL)).thenReturn(0L);

        assertEquals(AiCodes.INDEX_NOT_READY,
                assertThrows(BusinessException.class, () -> service(true).ask("app-1", "问题")).getCode());
        verify(indexService).rebuildOne("resume-1");
    }

    @Test
    void 索引为空时先自动重建再检索() {
        when(applicationMapper.selectById("app-1")).thenReturn(application("app-1", COMPANY_ID, "resume-1"));
        when(chunkStore.countByResume("resume-1", MODEL)).thenReturn(0L, 5L);
        when(retrievalService.retrieve("resume-1", "他做过高并发吗？")).thenReturn(List.of(hit(0.9)));
        when(chatClient.chat(anyString(), anyString())).thenReturn(new AiChatClient.ChatResult("结论 [1]", 42));

        AiQaViews.Answer answer = service(true).ask("app-1", "他做过高并发吗？");

        verify(indexService).rebuildOne("resume-1");
        assertTrue(answer.grounded());
        assertEquals(42, answer.totalTokens());
    }

    @Test
    void 相似度低于阈值直接回答没有相关信息且不调用对话模型() {
        when(applicationMapper.selectById("app-1")).thenReturn(application("app-1", COMPANY_ID, "resume-1"));
        when(chunkStore.countByResume("resume-1", MODEL)).thenReturn(5L);
        when(retrievalService.retrieve("resume-1", "他会潜水吗？")).thenReturn(List.of(hit(0.1)));

        AiQaViews.Answer answer = service(true).ask("app-1", "他会潜水吗？");

        assertFalse(answer.grounded());
        assertEquals(NO_EVIDENCE, answer.answer());
        assertTrue(answer.citations().isEmpty());
        assertNull(answer.totalTokens());
        assertEquals(1, answer.retrieved());
        verify(chatClient, never()).chat(anyString(), anyString());
    }

    @Test
    void 检索为空也是没有相关信息() {
        when(applicationMapper.selectById("app-1")).thenReturn(application("app-1", COMPANY_ID, "resume-1"));
        when(chunkStore.countByResume("resume-1", MODEL)).thenReturn(5L);
        when(retrievalService.retrieve("resume-1", "问题")).thenReturn(List.of());

        AiQaViews.Answer answer = service(true).ask("app-1", "问题");

        assertFalse(answer.grounded());
        assertEquals(NO_EVIDENCE, answer.answer());
        assertEquals(0, answer.retrieved());
    }

    @Test
    void 成功问答_返回引用并使用裁剪后的问题与配额() {
        when(applicationMapper.selectById("app-1")).thenReturn(application("app-1", COMPANY_ID, "resume-1"));
        when(chunkStore.countByResume("resume-1", MODEL)).thenReturn(5L);
        when(retrievalService.retrieve("resume-1", "他做过高并发吗？"))
                .thenReturn(List.of(hit(0.9), hit2(0.8)));
        when(chatClient.chat(anyString(), anyString()))
                .thenReturn(new AiChatClient.ChatResult("他在电商核心链路做过高并发 [2]。", 128));

        AiQaViews.Answer answer = service(true).ask("app-1", "  他做过高并发吗？  ");

        assertTrue(answer.grounded());
        assertEquals(1, answer.citations().size());
        assertEquals(2, answer.citations().get(0).index());
        assertEquals("fake-chat-v1", answer.chatModel());
        assertEquals(128, answer.totalTokens());
        assertEquals(2, answer.retrieved());
        verify(quotaService).consume("u-hr-1");
        verify(chunkStore, never()).countByResume(eq("resume-2"), anyString());

        ArgumentCaptor<String> userPrompt = ArgumentCaptor.forClass(String.class);
        verify(chatClient).chat(anyString(), userPrompt.capture());
        assertTrue(userPrompt.getValue().contains("[1]"));
        assertTrue(userPrompt.getValue().contains("[2]"));
        assertTrue(userPrompt.getValue().contains("HR 的问题：他做过高并发吗？"));
    }

    @Test
    void 模型漏标引用时仍保证带引用() {
        when(applicationMapper.selectById("app-1")).thenReturn(application("app-1", COMPANY_ID, "resume-1"));
        when(chunkStore.countByResume("resume-1", MODEL)).thenReturn(5L);
        when(retrievalService.retrieve("resume-1", "问题")).thenReturn(List.of(hit(0.9)));
        when(chatClient.chat(anyString(), anyString())).thenReturn(new AiChatClient.ChatResult("他有 6 年经验。", 50));

        AiQaViews.Answer answer = service(true).ask("app-1", "问题");

        assertTrue(answer.grounded());
        assertEquals(1, answer.citations().size());
        assertTrue(answer.answer().endsWith("来源：[1]"));
    }

    @Test
    void 模型明确说没有信息时不附引用() {
        when(applicationMapper.selectById("app-1")).thenReturn(application("app-1", COMPANY_ID, "resume-1"));
        when(chunkStore.countByResume("resume-1", MODEL)).thenReturn(5L);
        when(retrievalService.retrieve("resume-1", "问题")).thenReturn(List.of(hit(0.9)));
        when(chatClient.chat(anyString(), anyString()))
                .thenReturn(new AiChatClient.ChatResult(NO_EVIDENCE, 20));

        AiQaViews.Answer answer = service(true).ask("app-1", "问题");

        assertFalse(answer.grounded());
        assertTrue(answer.citations().isEmpty());
    }

    @Test
    void 上游失败透传50020() {
        when(applicationMapper.selectById("app-1")).thenReturn(application("app-1", COMPANY_ID, "resume-1"));
        when(chunkStore.countByResume("resume-1", MODEL)).thenReturn(5L);
        when(retrievalService.retrieve("resume-1", "问题")).thenReturn(List.of(hit(0.9)));
        when(chatClient.chat(anyString(), anyString()))
                .thenThrow(new BusinessException(AiCodes.UPSTREAM_FAILED, "boom"));

        assertEquals(AiCodes.UPSTREAM_FAILED,
                assertThrows(BusinessException.class, () -> service(true).ask("app-1", "问题")).getCode());
    }

    @Test
    void 配额超限42902且不检索() {
        when(applicationMapper.selectById("app-1")).thenReturn(application("app-1", COMPANY_ID, "resume-1"));
        org.mockito.Mockito.doThrow(new BusinessException(AiCodes.QUOTA_EXCEEDED, "quota"))
                .when(quotaService).consume("u-hr-1");

        assertEquals(AiCodes.QUOTA_EXCEEDED,
                assertThrows(BusinessException.class, () -> service(true).ask("app-1", "问题")).getCode());
        verifyNoInteractions(retrievalService, chatClient);
    }

    // ==================== T-AI3 流式 ====================

    @Test
    void 流式_逐段回调_结束后给出引用与完整回答() {
        when(applicationMapper.selectById("app-1")).thenReturn(application("app-1", COMPANY_ID, "resume-1"));
        when(chunkStore.countByResume("resume-1", MODEL)).thenReturn(5L);
        when(retrievalService.retrieve("resume-1", "他做过高并发吗？")).thenReturn(List.of(hit(0.9)));
        when(chatClient.stream(anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    java.util.function.Consumer<String> onDelta = invocation.getArgument(2);
                    onDelta.accept("他在电商核心链路");
                    onDelta.accept("做过高并发 [1]。");
                    return new AiChatClient.ChatResult("他在电商核心链路做过高并发 [1]。", 88);
                });

        CollectingSink sink = new CollectingSink();
        HrResumeQaService service = service(true);
        service.stream(service.prepare("app-1", "  他做过高并发吗？  "), sink);

        assertEquals(List.of("他在电商核心链路", "做过高并发 [1]。"), sink.deltas);
        assertEquals(1, sink.citations.size());
        assertEquals(1, sink.citations.get(0).index());
        assertTrue(sink.done.grounded());
        assertEquals("他在电商核心链路做过高并发 [1]。", sink.done.answer());
        assertEquals(88, sink.done.totalTokens());
        assertEquals(1, sink.done.retrieved());
        verify(quotaService).consume("u-hr-1");
    }

    @Test
    void 流式_无依据时只发一次固定话术且不调用对话模型() {
        when(applicationMapper.selectById("app-1")).thenReturn(application("app-1", COMPANY_ID, "resume-1"));
        when(chunkStore.countByResume("resume-1", MODEL)).thenReturn(5L);
        when(retrievalService.retrieve("resume-1", "他会潜水吗？")).thenReturn(List.of(hit(0.1)));

        CollectingSink sink = new CollectingSink();
        HrResumeQaService service = service(true);
        service.stream(service.prepare("app-1", "他会潜水吗？"), sink);

        assertEquals(List.of(NO_EVIDENCE), sink.deltas);
        assertTrue(sink.citations.isEmpty());
        assertFalse(sink.done.grounded());
        assertEquals(NO_EVIDENCE, sink.done.answer());
        assertNull(sink.done.totalTokens());
        verify(chatClient, never()).stream(anyString(), anyString(), any());
        verify(chatClient, never()).chat(anyString(), anyString());
    }

    @Test
    void 流式_模型漏标引用时以done中的兜底正文为准() {
        when(applicationMapper.selectById("app-1")).thenReturn(application("app-1", COMPANY_ID, "resume-1"));
        when(chunkStore.countByResume("resume-1", MODEL)).thenReturn(5L);
        when(retrievalService.retrieve("resume-1", "问题")).thenReturn(List.of(hit(0.9)));
        when(chatClient.stream(anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    java.util.function.Consumer<String> onDelta = invocation.getArgument(2);
                    onDelta.accept("他有 6 年经验。");
                    return new AiChatClient.ChatResult("他有 6 年经验。", 50);
                });

        CollectingSink sink = new CollectingSink();
        HrResumeQaService service = service(true);
        service.stream(service.prepare("app-1", "问题"), sink);

        assertEquals(List.of("他有 6 年经验。"), sink.deltas);
        assertTrue(sink.done.answer().endsWith("来源：[1]"));
        assertEquals(1, sink.citations.size());
    }

    @Test
    void 流式_上游失败透传50020() {
        when(applicationMapper.selectById("app-1")).thenReturn(application("app-1", COMPANY_ID, "resume-1"));
        when(chunkStore.countByResume("resume-1", MODEL)).thenReturn(5L);
        when(retrievalService.retrieve("resume-1", "问题")).thenReturn(List.of(hit(0.9)));
        when(chatClient.stream(anyString(), anyString(), any()))
                .thenThrow(new BusinessException(AiCodes.UPSTREAM_FAILED, "boom"));

        HrResumeQaService service = service(true);
        HrResumeQaService.Prepared prepared = service.prepare("app-1", "问题");
        CollectingSink sink = new CollectingSink();

        assertEquals(AiCodes.UPSTREAM_FAILED,
                assertThrows(BusinessException.class, () -> service.stream(prepared, sink)).getCode());
        assertTrue(sink.done == null, "失败时不应发 done，由 Web 层发 error 事件");
    }

    @Test
    void 流式前置校验与非流式一致_关闭时42220() {
        CollectingSink sink = new CollectingSink();

        assertEquals(AiCodes.AI_DISABLED,
                assertThrows(BusinessException.class,
                        () -> service(false).prepare("app-1", "问题")).getCode());
        assertTrue(sink.deltas.isEmpty());
    }

    /** 收集流式事件（顺序：delta → citation → done）。 */
    private static final class CollectingSink implements AiStreamSink {
        private final List<String> deltas = new java.util.ArrayList<>();
        private final List<AiQaViews.Citation> citations = new java.util.ArrayList<>();
        private AiQaViews.Answer done;

        @Override
        public void delta(String text) {
            deltas.add(text);
        }

        @Override
        public void citation(AiQaViews.Citation citation) {
            citations.add(citation);
        }

        @Override
        public void done(AiQaViews.Answer answer) {
            done = answer;
        }
    }

    private HrResumeQaService service(boolean enabled) {
        AiProperties properties = new AiProperties(enabled, AiProperties.PROVIDER_FAKE, 800, 10, 6, 0.2, 700, 100, 8, 0.15);
        return new HrResumeQaService(properties, applicationMapper, chunkStore, indexService, embeddingModel,
                retrievalService, new PromptBuilder(), new CitationAssembler(), chatClient, quotaService);
    }

    private static ApplicationEntity application(String id, String companyId, String resumeId) {
        ApplicationEntity entity = new ApplicationEntity();
        entity.setId(id);
        entity.setCompanyId(companyId);
        entity.setResumeId(resumeId);
        entity.setUserId("u-cand-2");
        return entity;
    }

    private static RetrievalService.Hit hit(double score) {
        return new RetrievalService.Hit("rc_1", "resume-1", "experiences", "工作经历 · 某电商平台",
                "工作经历 · 某电商平台\n负责订单与营销核心链路，QPS 12000。", score);
    }

    private static RetrievalService.Hit hit2(double score) {
        return new RetrievalService.Hit("rc_2", "resume-1", "projects", "项目经历 · 秒杀系统",
                "项目经历 · 秒杀系统\n基于 Redis 预扣库存。", score);
    }
}
