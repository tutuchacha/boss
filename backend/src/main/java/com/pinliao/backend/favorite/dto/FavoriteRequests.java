package com.pinliao.backend.favorite.dto;

import jakarta.validation.constraints.NotBlank;

/** 收藏模块请求体（契约 §11.1）。 */
public final class FavoriteRequests {

    private FavoriteRequests() {
    }

    public record AddRequest(
            @NotBlank(message = "jobId 不能为空")
            String jobId) {
    }
}
