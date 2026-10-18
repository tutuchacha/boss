package com.pinliao.backend.resume.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 简历模块请求体（契约 §3.1~§3.6；形状对齐前端 {@code ResumeContent}）。 */
public final class ResumeRequests {

    private ResumeRequests() {
    }

    /** §3.3 创建简历。 */
    public record CreateRequest(
            @NotBlank(message = "简历名称不能为空")
            @Size(max = 64, message = "简历名称不能超过 64 个字符")
            String name,
            @NotNull(message = "简历内容不能为空")
            @Valid
            ResumeContent content,
            @Size(max = 512, message = "附件地址不能超过 512 个字符")
            String attachmentUrl) {
    }

    /** §3.4 更新简历（部分字段；content 整体替换）。 */
    public record UpdateRequest(
            @Pattern(regexp = ".*\\S.*", message = "简历名称不能为空")
            @Size(max = 64, message = "简历名称不能超过 64 个字符")
            String name,
            @Valid
            ResumeContent content,
            @Size(max = 512, message = "附件地址不能超过 512 个字符")
            String attachmentUrl,
            Boolean isDefault) {
    }

    /** 与前端 ResumeContent 一致；数组缺省时由 service 归一为空数组。 */
    public record ResumeContent(
            @NotNull(message = "基本信息不能为空")
            @Valid
            Basics basics,
            @Size(max = 20, message = "教育经历不能超过 20 条")
            @Valid
            List<Education> educations,
            @Size(max = 20, message = "工作经历不能超过 20 条")
            @Valid
            List<Experience> experiences,
            @Size(max = 30, message = "项目经历不能超过 30 条")
            @Valid
            List<Project> projects,
            @Size(max = 50, message = "技能不能超过 50 个")
            List<@NotBlank(message = "技能不能为空") @Size(max = 64, message = "技能不能超过 64 个字符") String> skills) {

        /** 缺失数组归一为空数组，保证落库与出参形状稳定。 */
        public ResumeContent normalized() {
            return new ResumeContent(
                    basics,
                    educations == null ? List.of() : educations,
                    experiences == null ? List.of() : experiences,
                    projects == null ? List.of() : projects,
                    skills == null ? List.of() : skills);
        }
    }

    public record Basics(
            @NotBlank(message = "姓名不能为空")
            @Size(max = 64, message = "姓名不能超过 64 个字符")
            String name,
            @NotBlank(message = "手机号不能为空")
            @Pattern(regexp = "^1\\d{10}$", message = "请输入正确的手机号")
            String phone,
            @Size(max = 128, message = "邮箱不能超过 128 个字符")
            String email,
            @Size(max = 64, message = "城市不能超过 64 个字符")
            String city,
            @Size(max = 512, message = "头像地址不能超过 512 个字符")
            String avatarUrl,
            @Size(max = 2000, message = "个人优势不能超过 2000 个字符")
            String summary) {
    }

    public record Education(
            @NotBlank(message = "学校不能为空")
            @Size(max = 128, message = "学校不能超过 128 个字符")
            String school,
            @NotBlank(message = "专业不能为空")
            @Size(max = 128, message = "专业不能超过 128 个字符")
            String major,
            @NotBlank(message = "学历不能为空")
            @Size(max = 32, message = "学历不能超过 32 个字符")
            String degree,
            @Size(max = 16, message = "开始时间格式不正确")
            String startDate,
            @Size(max = 16, message = "结束时间格式不正确")
            String endDate) {
    }

    public record Experience(
            @NotBlank(message = "公司名称不能为空")
            @Size(max = 128, message = "公司名称不能超过 128 个字符")
            String company,
            @NotBlank(message = "职位名称不能为空")
            @Size(max = 128, message = "职位名称不能超过 128 个字符")
            String title,
            @Size(max = 16, message = "开始时间格式不正确")
            String startDate,
            @Size(max = 16, message = "结束时间格式不正确")
            String endDate,
            @Size(max = 2000, message = "工作描述不能超过 2000 个字符")
            String description) {
    }

    public record Project(
            @NotBlank(message = "项目名称不能为空")
            @Size(max = 128, message = "项目名称不能超过 128 个字符")
            String name,
            @Size(max = 64, message = "项目角色不能超过 64 个字符")
            String role,
            @Size(max = 16, message = "开始时间格式不正确")
            String startDate,
            @Size(max = 16, message = "结束时间格式不正确")
            String endDate,
            @Size(max = 2000, message = "项目描述不能超过 2000 个字符")
            String description,
            @Size(max = 512, message = "项目链接不能超过 512 个字符")
            String link) {
    }
}
