package com.pinliao.backend.ai.service;

import com.pinliao.backend.ai.AiCodes;
import com.pinliao.backend.ai.config.AiProperties;
import com.pinliao.backend.ai.view.AiViews;
import com.pinliao.backend.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * T-AI0：AI 连通性自检（开关 / 调用参数）单元测试。
 *
 * <p>用 Mockito 假模型，**不联网、不消耗额度**，与既有 173 项测试风格一致（对齐 §8「假模型优先」）。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiConnectivityServiceTest {

    private static final String CHAT_MODEL = "qwen3.5-122b-a10b";
    private static final String EMBEDDING_MODEL = "text-embedding-v4";

    @Mock
    private ChatModel chatModel;
    @Mock
    private EmbeddingModel embeddingModel;

    private AiConnectivityService service(boolean enabled) {
        return new AiConnectivityService(chatModel, embeddingModel,
                new AiProperties(enabled, AiProperties.PROVIDER_SPRING_AI, 800, 10, 6, 0.2, 700, 100, 8, 0.15),
                CHAT_MODEL, EMBEDDING_MODEL);
    }

    @Test
    void 关闭时_ping_抛42220且不调用上游() {
        AiConnectivityService svc = service(false);

        BusinessException ex = assertThrows(BusinessException.class, svc::ping);

        assertEquals(AiCodes.AI_DISABLED, ex.getCode());
        assertEquals(false, svc.status().enabled());
        Mockito.verifyNoInteractions(chatModel, embeddingModel);
    }

    @Test
    void 开启时_ping回显模型与向量维度() {
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("测试回答")))));
        when(embeddingModel.embed(anyString())).thenReturn(new float[1024]);

        AiViews.Ping ping = service(true).ping();

        assertEquals(CHAT_MODEL, ping.chatModel());
        assertEquals("测试回答", ping.answer());
        assertEquals(EMBEDDING_MODEL, ping.embeddingModel());
        assertEquals(1024, ping.embeddingDim());
        assertTrue(ping.chatLatencyMs() >= 0);
        assertTrue(ping.embeddingLatencyMs() >= 0);
        // Mockito 假响应无真实 usage（计为 0）；真实调用才有 token 统计（实测 44 tokens）
        assertEquals(Integer.valueOf(0), ping.totalTokens());
    }

    @Test
    void 开启时_chat关闭思考模式并限制token() {
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("ok")))));
        when(embeddingModel.embed(anyString())).thenReturn(new float[1024]);

        service(true).ping();

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        Mockito.verify(chatModel).call(captor.capture());
        OpenAiChatOptions options = (OpenAiChatOptions) captor.getValue().getOptions();
        assertEquals(CHAT_MODEL, options.getModel());
        assertEquals(Integer.valueOf(128), options.getMaxTokens());
        // DashScope/Qwen 思考模式必须显式关闭（否则延迟与 token 都不可接受）
        assertEquals(Boolean.FALSE, options.getExtraBody().get("enable_thinking"));
    }
}
