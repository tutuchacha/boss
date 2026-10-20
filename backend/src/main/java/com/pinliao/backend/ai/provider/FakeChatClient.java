package com.pinliao.backend.ai.provider;

import com.pinliao.backend.ai.spi.AiChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 本地确定性假对话模型（{@code pinliao.ai.provider=fake}）。
 *
 * <p>用途：单测与冒烟在**无密钥 / 不产生费用**的前提下跑通「检索 → Prompt → 回答 → 引用」链路。
 * 回答是固定模板，不具任何语义能力；只保证格式与引用编号可被下游解析。</p>
 */
@Component
@ConditionalOnProperty(prefix = "pinliao.ai", name = "provider", havingValue = "fake")
public class FakeChatClient implements AiChatClient {

    private static final String MODEL_ID = "fake-chat-v1";

    @Override
    public String modelId() {
        return MODEL_ID;
    }

    @Override
    public ChatResult chat(String systemPrompt, String userPrompt) {
        // 假模型只验证管道：固定引用 [1]，真实语义由 springai provider 提供。
        return new ChatResult("（假模型）根据简历资料 [1]，候选人具备相关资料中描述的经历与技能。", 32);
    }
}
