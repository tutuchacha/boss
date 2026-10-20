package com.pinliao.backend.ai.dto;

/**
 * 单简历问答请求体（Step 4 T-AI2）。
 */
public final class AiQaRequests {

    private AiQaRequests() {
    }

    /**
     * POST /api/v1/hr/applications/{applicationId}/ai/qa。
     *
     * @param question HR 的问题（1~500 字；为空 / 超长 42201）
     */
    public record Ask(String question) {
    }
}
