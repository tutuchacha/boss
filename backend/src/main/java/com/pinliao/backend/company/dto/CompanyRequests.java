package com.pinliao.backend.company.dto;

/**
 * 公司模块请求体（契约 §5.4 / §5.6）。
 *
 * <p>PATCH 为部分更新：{@code null} 表示保留原值；校验在 service 层完成，以便返回
 * 契约约定的 42210（而不是通用的 42201）。</p>
 */
public final class CompanyRequests {

    private CompanyRequests() {
    }

    /** §5.4 更新公司信息（全部字段可选）。 */
    public record Patch(
            String name,
            String logoUrl,
            String intro,
            String industry,
            String stage,
            String size,
            String website) {
    }

    /** §5.6 企业认证提交。 */
    public record Verify(
            String companyName,
            String licenseNo,
            String contactName,
            String contactPhone) {
    }
}
