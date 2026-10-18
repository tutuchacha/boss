package com.pinliao.backend.job.view;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * 职位接口视图（契约 §6.1~§6.4；字段与前端 {@code src/types/api.ts} 的 JobSummary / JobDetail 对齐）。
 *
 * <p>时间输出 ISO-8601 UTC（如 {@code 2026-10-18T07:12:34.567Z}），与 mock 的 {@code Date.toISOString()} 一致；
 * null 字段不序列化（对齐 mock 中 undefined 字段缺席的形状）。</p>
 */
public final class JobViews {

    private JobViews() {
    }

    /** JobSummary：列表 / 搜索 / 相似职位条目。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Summary(
            String id,
            String title,
            int salaryMin,
            int salaryMax,
            /** 展示用 '20-35K'（对齐 mock `${sMin}-${sMax}K`） */
            String salary,
            String city,
            String area,
            String exp,
            String edu,
            String cat,
            String sub,
            List<String> tags,
            CompanyBrief company,
            HrBrief hr,
            /** 距今天数（按 publishedAt 实时计算，随时间自然增长） */
            long days,
            Instant publishedAt,
            int viewCount,
            int applyCount) {
    }

    /** JobDetail = Summary + 详情字段 + 相似职位。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Detail(
            String id,
            String title,
            int salaryMin,
            int salaryMax,
            String salary,
            String city,
            String area,
            String exp,
            String edu,
            String cat,
            String sub,
            List<String> tags,
            CompanyBrief company,
            HrBrief hr,
            long days,
            Instant publishedAt,
            int viewCount,
            int applyCount,
            String companyId,
            String hrId,
            String responsibilities,
            String requirements,
            List<String> welfare,
            /** 职位介绍（演示数据未使用，保留契约字段） */
            String intro,
            List<Summary> similarJobs) {
    }

    /** JobSummary.company（Pick<Company,'id'|'name'|'stage'|'size'>）。 */
    public record CompanyBrief(String id, String name, String stage, String size) {
    }

    /** JobSummary.hr（Pick<HrProfile,'id'|'name'|'title'>）。 */
    public record HrBrief(String id, String name, String title) {
    }

    /** 游标分页响应（契约 §4.2）。 */
    public record Page<T>(List<T> list, Pagination pagination) {
    }

    /**
     * nextCursor / hasMore / total。
     *
     * <p>注意：{@code nextCursor} 必须显式输出 {@code null}（不能 @JsonInclude(NON_NULL)），
     * 与前端 {@code CursorPagination.nextCursor: string | null} 及 mock 形状一致。</p>
     */
    public record Pagination(String nextCursor, boolean hasMore, Integer total) {
    }
}
