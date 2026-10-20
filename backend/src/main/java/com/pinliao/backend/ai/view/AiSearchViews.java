package com.pinliao.backend.ai.view;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.pinliao.backend.recruiter.view.RecruiterViews;

import java.util.List;

/**
 * 语义人才检索视图（Step 4 T-AI4）。
 *
 * <p>与关键词版 §4.5 的 {@code CandidateMatchView} 并行：候选人摘要与职位标题复用同一口径，
 * 差异在于分值是**余弦相似度**（0~1）而非关键词加权分，证据来自简历向量分块（段落标题 + 原文片段）。</p>
 */
public final class AiSearchViews {

    private AiSearchViews() {
    }

    /**
     * 单个候选人的语义命中。
     *
     * @param applicationId 用于跳转候选人详情（同一候选人多条投递时取分数最高的一条）
     * @param candidate     候选人摘要（与投递列表 / 关键词筛选同源）
     * @param jobTitle      应聘职位
     * @param score         候选人最高分块的余弦相似度（0~1，保留 4 位小数）
     * @param evidences     该候选人得分最高的若干简历分块（按相似度倒序）
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SemanticMatch(String applicationId,
                                RecruiterViews.Candidate candidate,
                                String jobTitle,
                                double score,
                                List<Evidence> evidences) {
    }

    /**
     * 语义证据片段。
     *
     * @param chunkId resume_chunks.id（排查 / 高亮定位用）
     * @param section 段落类型：basics / educations / experiences / projects / skills
     * @param title   段落标题（如「项目经历 · 招聘管理系统前端重构」）
     * @param snippet 去掉标题行后的原文片段（前端直接展示，不做关键词高亮）
     * @param score   该分块的余弦相似度
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Evidence(String chunkId,
                           String section,
                           String title,
                           String snippet,
                           double score) {
    }
}
