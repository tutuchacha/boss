package com.pinliao.backend.ai.service;

import com.pinliao.backend.ai.AiCodes;
import com.pinliao.backend.ai.config.AiProperties;
import com.pinliao.backend.ai.view.AiViews;
import com.pinliao.backend.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * AI 连通性自检（Step 4 T-AI0）。
 *
 * <p>只做「能否打通上游」的验证：一次 chat + 一次 embedding。
 * 业务能力（简历索引 / 检索 / 问答）在 T-AI1 起的增量里实现，本类不承载业务语义。</p>
 *
 * <p>模型名 / 地址 / 密钥来自 {@code spring.ai.openai.*}（由环境变量注入），
 * 因此本类不再重复定义一份模型配置，只读回用于回显。</p>
 */
@Service
public class AiConnectivityService {

    private static final Logger log = LoggerFactory.getLogger(AiConnectivityService.class);

    /** 自检提示词：短且确定性高，避免为一次自检浪费额度。 */
    private static final String PING_PROMPT = "用一句话说明什么是 RAG，不超过 40 字。";

    /** 自检向量文本（同时用于 T-AI1 的分块语义参考）。 */
    private static final String PING_EMBED_TEXT = "聘聊 RAG 自检：候选人简历问答";

    /** 自检最多生成 token（思考模式已关，128 足够回答一句话）。 */
    private static final int PING_MAX_TOKENS = 128;

    private final ChatModel chatModel;
    private final EmbeddingModel embeddingModel;
    private final AiProperties properties;
    private final String chatModelName;
    private final String embeddingModelName;

    public AiConnectivityService(ChatModel chatModel,
                                 EmbeddingModel embeddingModel,
                                 AiProperties properties,
                                 @Value("${spring.ai.openai.chat.options.model:unknown}") String chatModelName,
                                 @Value("${spring.ai.openai.embedding.options.model:unknown}") String embeddingModelName) {
        this.chatModel = chatModel;
        this.embeddingModel = embeddingModel;
        this.properties = properties;
        this.chatModelName = chatModelName;
        this.embeddingModelName = embeddingModelName;
    }

    /** 状态回显：不发起上游调用，AI 关闭时也可安全读取。 */
    public AiViews.Status status() {
        return new AiViews.Status(properties.enabled(), "openai-compatible", chatModelName, embeddingModelName);
    }

    /** 自检：真实调用一次 chat + embedding，返回模型名 / 耗时 / token / 向量维度。 */
    public AiViews.Ping ping() {
        requireEnabled();

        long chatStart = System.nanoTime();
        ChatResponse response = callChat();
        long chatLatencyMs = elapsedMs(chatStart);

        long embeddingStart = System.nanoTime();
        float[] vector = callEmbedding();
        long embeddingLatencyMs = elapsedMs(embeddingStart);

        return new AiViews.Ping(chatModelName,
                response.getResult().getOutput().getText(),
                chatLatencyMs,
                response.getMetadata() == null || response.getMetadata().getUsage() == null
                        ? null
                        : response.getMetadata().getUsage().getTotalTokens(),
                embeddingModelName,
                vector.length,
                embeddingLatencyMs);
    }

    private ChatResponse callChat() {
        // DashScope 的 Qwen3.5 默认思考模式：显式关闭换取演示级延迟（实测 ~1s，开启时 >10s 且多计 token）。
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(chatModelName)
                .maxTokens(PING_MAX_TOKENS)
                .extraBody(Map.of("enable_thinking", false))
                .build();
        try {
            ChatResponse response = chatModel.call(new Prompt(List.of(new UserMessage(PING_PROMPT)), options));
            if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
                throw new BusinessException(AiCodes.UPSTREAM_FAILED, "AI 上游返回空结果");
            }
            return response;
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("AI 自检失败（chat）：{}", e.toString());
            throw new BusinessException(AiCodes.UPSTREAM_FAILED, "AI 对话调用失败：" + e.getMessage());
        }
    }

    private float[] callEmbedding() {
        try {
            return embeddingModel.embed(PING_EMBED_TEXT);
        } catch (RuntimeException e) {
            log.warn("AI 自检失败（embedding）：{}", e.toString());
            throw new BusinessException(AiCodes.UPSTREAM_FAILED, "AI 向量调用失败：" + e.getMessage());
        }
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
