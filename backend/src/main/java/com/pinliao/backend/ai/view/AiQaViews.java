package com.pinliao.backend.ai.view;

import java.util.List;

/**
 * 单简历问答视图（Step 4 T-AI2）。
 *
 * <p>回答与引用分离：{@code answer} 是给 HR 看的正文（含 {@code [n]} 标记），
 * {@code citations} 是标记到简历分块的映射，前端据此渲染可点击/可展开的来源。</p>
 */
public final class AiQaViews {

    private AiQaViews() {
    }

    /**
     * 一条引用。
     *
     * @param index   回答中的引用序号（从 1 开始，对应资料编号）
     * @param chunkId resume_chunks.id
     * @param resumeId 简历 id（前端可据此定位「同一份简历」）
     * @param section 段落类型：basics / educations / experiences / projects / skills
     * @param title   段落标题（如「工作经历 · 某电商平台 · 高级后端工程师」）
     * @param snippet 证据片段（截断后的原文，便于在抽屉里快速核对）
     * @param score   余弦相似度（0~1，保留 4 位小数）
     */
    public record Citation(int index,
                           String chunkId,
                           String resumeId,
                           String section,
                           String title,
                           String snippet,
                           double score) {
    }

    /**
     * 问答结果。
     *
     * @param answer      回答正文；无依据时固定为「简历中没有相关信息。」
     * @param grounded    是否在简历中找到了依据（false 时 citations 必为空）
     * @param citations   被回答引用的分块（按首次出现顺序去重）
     * @param chatModel   对话模型标识（排查用）
     * @param elapsedMs   端到端耗时（含检索与生成）
     * @param totalTokens 上游 token 合计；未知为 null
     * @param retrieved   检索命中的分块数（用于解释「为什么说没有」）
     */
    public record Answer(String answer,
                         boolean grounded,
                         List<Citation> citations,
                         String chatModel,
                         long elapsedMs,
                         Integer totalTokens,
                         int retrieved) {
    }
}
