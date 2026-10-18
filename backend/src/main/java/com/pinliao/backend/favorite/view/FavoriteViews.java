package com.pinliao.backend.favorite.view;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.pinliao.backend.favorite.entity.FavoriteEntity;
import com.pinliao.backend.job.view.JobViews;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** 收藏视图（契约 §11.1~§11.3；对齐前端 Favorite / FavoriteWithJob）。 */
public final class FavoriteViews {

    private FavoriteViews() {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Favorite(String id, String userId, String jobId, Instant createdAt) {

        public static Favorite from(FavoriteEntity f) {
            return new Favorite(f.getId(), f.getUserId(), f.getJobId(), instant(f.getCreatedAt()));
        }
    }

    /** §11.3 列表项 = Favorite + job 摘要。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record WithJob(String id, String userId, String jobId, Instant createdAt, JobViews.Summary job) {

        public static WithJob from(FavoriteEntity f, JobViews.Summary job) {
            return new WithJob(f.getId(), f.getUserId(), f.getJobId(), instant(f.getCreatedAt()), job);
        }
    }

    private static Instant instant(LocalDateTime time) {
        return time == null ? null : time.toInstant(ZoneOffset.UTC);
    }
}
