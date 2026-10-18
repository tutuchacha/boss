package com.pinliao.backend.job.dto;

import java.util.List;

/**
 * 职位写接口请求体（契约 §6.5 / §6.6）。
 *
 * <p><b>字段语义</b>：POST 发布时由 service 校验必填（title / salary / city / exp / edu / cat / sub）；
 * PATCH 更新为部分更新——{@code null} 表示保留原值，非 null 才覆盖（契约「同 6.5 部分字段」）。
 * 因此这里不挂 Bean Validation 注解，跨字段校验（薪资下限 < 上限）需要合并后再判断。</p>
 */
public final class JobRequests {

    private JobRequests() {
    }

    /** §6.5 发布 / §6.6 更新（PATCH 时可只传部分字段）。 */
    public record Upsert(
            String title,
            Integer salaryMin,
            Integer salaryMax,
            String city,
            String area,
            String exp,
            String edu,
            String cat,
            String sub,
            List<String> tags,
            String responsibilities,
            String requirements,
            List<String> welfare) {
    }
}
