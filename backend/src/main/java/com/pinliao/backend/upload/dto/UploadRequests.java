package com.pinliao.backend.upload.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 文件上传请求体（契约 §12.1 / §12.2）。 */
public final class UploadRequests {

    private UploadRequests() {
    }

    /** §12.1 获取预签名上传 URL。 */
    public record SignRequest(
            @NotBlank(message = "type 不能为空")
            @Pattern(regexp = "avatar|logo|resume|company",
                    message = "type 只能是 avatar / logo / resume / company")
            String type,
            @NotBlank(message = "contentType 不能为空")
            String contentType,
            @NotBlank(message = "ext 不能为空")
            @Pattern(regexp = "jpg|jpeg|png|webp|pdf|docx", message = "不支持的扩展名")
            String ext) {
    }

    /** §12.2 确认上传完成。 */
    public record ConfirmRequest(
            @NotBlank(message = "objectKey 不能为空")
            String objectKey) {
    }
}
