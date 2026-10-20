package com.pinliao.backend.ai.provider;

import com.pinliao.backend.ai.AiCodes;
import com.pinliao.backend.ai.config.AiProperties;
import com.pinliao.backend.ai.spi.AiChatClient;
import com.pinliao.backend.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T-AI2：真实对话适配器单测（Mockito 假模型，不联网、不耗额度）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SpringAiChatAdapterTest {

    private static final String MODEL = "qwen3.5-122b-a10b";

    @Mock
    private ChatModel chatModel;

    @Test
    void 成功时_关闭思考模式并限制token_且系统与用户消息分离() {
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("回答 [1]")))));

        AiChatClient.ChatResult result = adapter().chat("系统规则", "资料 [1]\n问题");

        assertEquals("回答 [1]", result.text());
        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(captor.capture());
        Prompt prompt = captor.getValue();
        assertEquals(2, prompt.getInstructions().size());
        assertInstanceOf(SystemMessage.class, prompt.getInstructions().get(0));
        assertInstanceOf(UserMessage.class, prompt.getInstructions().get(1));
        OpenAiChatOptions options = (OpenAiChatOptions) prompt.getOptions();
        assertEquals(MODEL, options.getModel());
        assertEquals(Integer.valueOf(700), options.getMaxTokens());
        assertEquals(Boolean.FALSE, options.getExtraBody().get("enable_thinking"));
    }

    @Test
    void 上游异常转50020() {
        when(chatModel.call(any(Prompt.class))).thenThrow(new IllegalStateException("boom"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> adapter().chat("系统", "用户"));

        assertEquals(AiCodes.UPSTREAM_FAILED, error.getCode());
    }

    @Test
    void 空回答转50020() {
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("   ")))));

        assertEquals(AiCodes.UPSTREAM_FAILED,
                assertThrows(BusinessException.class, () -> adapter().chat("系统", "用户")).getCode());
    }

    private SpringAiChatAdapter adapter() {
        return new SpringAiChatAdapter(chatModel,
                new AiProperties(true, AiProperties.PROVIDER_SPRING_AI, 800, 10, 6, 0.2, 700, 100),
                MODEL);
    }
}
