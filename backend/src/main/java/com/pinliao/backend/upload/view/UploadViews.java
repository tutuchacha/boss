package com.pinliao.backend.upload.view;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/** 文件上传视图（契约 §12.1 / §12.2）。 */
public final class UploadViews {

    private UploadViews() {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Sign(String uploadUrl, String objectKey, Instant expiresAt) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Confirm(String url, String contentType, long size) {
    }
}
