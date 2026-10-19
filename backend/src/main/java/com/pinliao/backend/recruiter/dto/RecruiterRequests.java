package com.pinliao.backend.recruiter.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 招聘者模块请求体（契约 §4.1~§4.6）。 */
public final class RecruiterRequests {

    private RecruiterRequests() {
    }

    /**
     * §4.2 更新招聘者画像（部分字段；null = 不修改）。
     *
     * <p>契约未给校验规则，这里按 hr_profiles 列宽与「空字符串不算修改、显式空白视为非法」的口径
     * 自行约束；错误返回 42201。字段长度与 V1 建表一致（name / title 64，avatarUrl / intro 512）。</p>
     */
    public record HrProfilePatch(
            @Pattern(regexp = ".*\\S.*", message = "姓名不能为空")
            @Size(max = 64, message = "姓名不能超过 64 个字符")
            String name,
            @Pattern(regexp = ".*\\S.*", message = "职位不能为空")
            @Size(max = 64, message = "职位不能超过 64 个字符")
            String title,
            @Size(max = 512, message = "头像地址不能超过 512 个字符")
            String avatarUrl,
            @Size(max = 512, message = "个人简介不能超过 512 个字符")
            String intro) {
    }

    /**
     * §4.5 候选人匹配入参。
     *
     * <p>{@code query} 为自然语言技能需求；{@code tags} 为技能标签；两者至少一个非空（否则 42210）。</p>
     */
    public record CandidateMatchInput(String query, List<String> tags) {
    }
}
