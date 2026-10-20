package com.pinliao.backend.ai.dto;

import java.util.List;

/**
 * 语义人才检索请求体（Step 4 T-AI4）。
 */
public final class AiSearchRequests {

    private AiSearchRequests() {
    }

    /**
     * POST /api/v1/hr/talent/semantic-search。
     *
     * @param query 自然语言需求（可空；与 tags 至少一个非空，否则 42210）
     * @param tags  技能标签（可空；拼接进检索文本一起做向量化）
     * @param limit 返回候选人数上限（可空；缺省取 {@code pinliao.ai.search-top-k}，最大 20）
     */
    public record SemanticSearch(String query, List<String> tags, Integer limit) {
    }
}
