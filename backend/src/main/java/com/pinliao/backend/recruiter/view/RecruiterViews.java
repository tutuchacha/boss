package com.pinliao.backend.recruiter.view;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.pinliao.backend.application.view.ApplicationViews;

import java.util.List;

/**
 * 招聘者模块视图（契约 §4.4 / §4.5；字段与 {@code src/types/api.ts} 对齐）。
 *
 * <p><b>出参形状口径（T8 决策）</b>：§4.3 / §4.4 直接返回前端 service 需要的结构（超集），
 * 避免 T9 联调时二次适配：
 * <ul>
 *   <li>§4.3 → {@code List<JobViews.HrRecord>}（JobSummary + 状态 + 详情字段，即前端
 *       {@code HrJobRecord}）；</li>
 *   <li>§4.4 → {@code List<ApplicationCard>}（对齐前端 {@code HrApplicationView}：
 *       application + candidate + job 摘要 + conversationId + 最近面试）。</li>
 * </ul>
 * 时间统一 ISO-8601 UTC；null 字段不序列化（对齐 mock 的 undefined 缺席形状）。</p>
 */
public final class RecruiterViews {

    private RecruiterViews() {
    }

    /**
     * §4.4 投递卡片（对齐前端 {@code HrApplicationView} / mock {@code companyApplicationViews}）。
     *
     * <p>{@code conversationId} 为「候选人 × 职位」会话 id（尚未创建则缺席）；
     * {@code interview} 为待确认面试优先、否则最近一条（对齐 mock）。</p>
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ApplicationCard(
            ApplicationViews.Application application,
            Candidate candidate,
            JobBrief job,
            String conversationId,
            ApplicationViews.Interview interview) {
    }

    /**
     * 候选人摘要（对齐 mock {@code CandidateSeed} 与 message 模块的
     * {@code MessageViews.Candidate}；§4.4 投递列表与 §4.5 匹配结果共用）。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Candidate(
            String userId,
            String name,
            String expect,
            String city,
            Integer workYears,
            String edu,
            Integer resumeCompleteness) {
    }

    /** 投递卡片中的职位摘要（对齐 mock {@code HrApplicationView.job}）。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record JobBrief(String id, String title, String salary, String city) {
    }

    /**
     * §4.5 单个候选人的匹配结果（按匹配度倒序返回）。
     *
     * <p>{@code applicationId} 供前端跳转候选人详情（同一候选人多条投递时取最近一条）；
     * {@code score} 为加权命中分（技能 3 / 文本 2），仅用于排序与展示。</p>
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CandidateMatch(
            String applicationId,
            Candidate candidate,
            String jobTitle,
            int score,
            List<String> hitTokens,
            List<Evidence> evidences) {
    }

    /** §4.5 命中证据（关联简历原文片段，供前端高亮）。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Evidence(String token, String field, String snippet) {
    }
}
