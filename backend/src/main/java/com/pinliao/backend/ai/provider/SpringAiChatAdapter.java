package com.pinliao.backend.ai.provider;

import com.pinliao.backend.ai.AiCodes;
import com.pinliao.backend.ai.config.AiProperties;
import com.pinliao.backend.ai.spi.AiChatClient;
import com.pinliao.backend.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 真实对话模型适配器（{@code pinliao.ai.provider=springai}，默认）。
 *
 * <p>与 T-AI0 自检保持同样的上游约束：DashScope 的 Qwen3.5 是思考型模型，
 * 必须显式 {@code enable_thinking=false}，否则延迟 >10s 且多计 token；
 * 同时限制 {@code maxTokens}，避免一次问答产生不可控费用。</p>
 */
@Component
@ConditionalOnProperty(prefix = "pinliao.ai", name = "provider", havingValue = "springai", matchIfMissing = true)
public class SpringAiChatAdapter implements AiChatClient {

    private static final Logger log = LoggerFactory.getLogger(SpringAiChatAdapter.class);

    private final ChatModel delegate;
    private final String modelId;
    private final int maxTokens;

    public SpringAiChatAdapter(ChatModel delegate,
                               AiProperties properties,
                               @Value("${spring.ai.openai.chat.options.model:qwen3.5-122b-a10b}") String modelId) {
        this.delegate = delegate;
        this.modelId = modelId;
        this.maxTokens = properties.qaMaxTokens();
    }

    @Override
    public String modelId() {
        return modelId;
    }

    @Override
    public ChatResult chat(String systemPrompt, String userPrompt) {
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(modelId)
                .maxTokens(maxTokens)
                .extraBody(Map.of("enable_thinking", false))
                .build();
        Prompt prompt = new Prompt(List.of(
                new SystemMessage(systemPrompt),
                new UserMessage(userPrompt)), options);

        ChatResponse response;
        try {
            response = delegate.call(prompt);
        } catch (RuntimeException e) {
            log.warn("AI 简历问答失败（model={}）：{}", modelId, e.toString());
            throw new BusinessException(AiCodes.UPSTREAM_FAILED, "AI 对话调用失败：" + e.getMessage());
        }
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            throw new BusinessException(AiCodes.UPSTREAM_FAILED, "AI 上游返回空结果");
        }
        String text = response.getResult().getOutput().getText();
        if (text == null || text.isBlank()) {
            throw new BusinessException(AiCodes.UPSTREAM_FAILED, "AI 上游返回空回答");
        }
        Integer totalTokens = response.getMetadata() == null || response.getMetadata().getUsage() == null
                ? null
                : response.getMetadata().getUsage().getTotalTokens();
        return new ChatResult(text.trim(), totalTokens);
    }
}
