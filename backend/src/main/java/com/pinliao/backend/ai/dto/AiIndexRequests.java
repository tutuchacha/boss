package com.pinliao.backend.ai.dto;

/**
 * AI 索引接口请求体（Step 4 T-AI1）。
 */
public final class AiIndexRequests {

    private AiIndexRequests() {
    }

    /**
     * POST /api/v1/ai/index/rebuild。
     *
     * @param resumeId 可选；为空表示全量重建（演示 / 初始化用）
     */
    public record Rebuild(String resumeId) {
    }
}
