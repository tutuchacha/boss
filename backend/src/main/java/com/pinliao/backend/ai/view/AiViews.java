package com.pinliao.backend.ai.view;

/**
 * AI 模块视图（Step 4 T-AI0：自检用，契约外；正式问答 / 检索出参见
 * {@code docs/step4-ai-plan.md} §6.3 与其后续版本）。
 */
public final class AiViews {

    private AiViews() {
    }

    /** GET /api/v1/ai/status：只回显配置，不发起上游调用。 */
    public record Status(boolean enabled, String provider, String chatModel, String embeddingModel) {
    }

    /** GET /api/v1/ai/ping：真实调用一次 chat + embedding 的自检结果。 */
    public record Ping(String chatModel,
                       String answer,
                       long chatLatencyMs,
                       Integer totalTokens,
                       String embeddingModel,
                       int embeddingDim,
                       long embeddingLatencyMs) {
    }
}
