package com.pinliao.backend.favorite.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pinliao.backend.auth.context.Roles;
import com.pinliao.backend.auth.context.UserContext;
import com.pinliao.backend.common.api.ApiCodes;
import com.pinliao.backend.common.api.PageViews;
import com.pinliao.backend.common.exception.BusinessException;
import com.pinliao.backend.favorite.dto.FavoriteRequests;
import com.pinliao.backend.favorite.entity.FavoriteEntity;
import com.pinliao.backend.favorite.mapper.FavoriteMapper;
import com.pinliao.backend.favorite.view.FavoriteViews;
import com.pinliao.backend.job.entity.JobEntity;
import com.pinliao.backend.job.mapper.JobMapper;
import com.pinliao.backend.job.service.JobQueryService;
import com.pinliao.backend.job.view.JobViews;
import com.pinliao.backend.user.UserCodes;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;

/**
 * 收藏服务（契约 §11.1~§11.3）。
 *
 * <p>收藏是求职者跨租户行为：favorites 为全局表，service 显式按 user_id 过滤。
 * 列表按 createdAt DESC, id ASC 游标分页；total 返回该用户全部收藏数。</p>
 */
@Service
public class FavoriteService {

    static final int DEFAULT_LIMIT = 20;
    static final int MAX_LIMIT = 100;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final FavoriteMapper favoriteMapper;
    private final JobMapper jobMapper;
    private final JobQueryService jobQueryService;

    public FavoriteService(FavoriteMapper favoriteMapper, JobMapper jobMapper, JobQueryService jobQueryService) {
        this.favoriteMapper = favoriteMapper;
        this.jobMapper = jobMapper;
        this.jobQueryService = jobQueryService;
    }

    /** §11.1 收藏职位（重复返回 40910；职位不可见 40410）。 */
    @Transactional
    public FavoriteViews.Favorite add(FavoriteRequests.AddRequest req) {
        UserContext.CurrentUser user = Roles.requireSeeker();
        String jobId = req.jobId().trim();
        JobEntity job = jobMapper.selectVisibleById(jobId);
        if (job == null) {
            throw new BusinessException(UserCodes.NOT_FOUND, "职位不存在或已下架");
        }
        FavoriteEntity existing = favoriteMapper.selectOne(new LambdaQueryWrapper<FavoriteEntity>()
                .eq(FavoriteEntity::getUserId, user.userId())
                .eq(FavoriteEntity::getJobId, jobId)
                .last("LIMIT 1"));
        if (existing != null) {
            throw new BusinessException(UserCodes.FAVORITE_EXISTS, "已收藏过该职位");
        }

        FavoriteEntity favorite = new FavoriteEntity();
        favorite.setId("fav-" + user.userId() + "-" + jobId);
        favorite.setUserId(user.userId());
        favorite.setJobId(jobId);
        favorite.setCreatedAt(now());
        try {
            favoriteMapper.insert(favorite);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(UserCodes.FAVORITE_EXISTS, "已收藏过该职位");
        }
        return FavoriteViews.Favorite.from(favorite);
    }

    /** §11.2 取消收藏（幂等；不存在也返回成功）。 */
    @Transactional
    public void remove(String jobId) {
        UserContext.CurrentUser user = Roles.requireSeeker();
        favoriteMapper.delete(new LambdaQueryWrapper<FavoriteEntity>()
                .eq(FavoriteEntity::getUserId, user.userId())
                .eq(FavoriteEntity::getJobId, jobId));
    }

    /** §11.3 我的收藏列表（含职位摘要；total 为全部收藏数）。 */
    public PageViews.Page<FavoriteViews.WithJob> list(String cursor, Integer limit) {
        UserContext.CurrentUser user = Roles.requireSeeker();
        List<FavoriteEntity> rows = favoriteMapper.selectList(new LambdaQueryWrapper<FavoriteEntity>()
                .eq(FavoriteEntity::getUserId, user.userId()));
        rows = new ArrayList<>(rows);
        rows.sort(Comparator.comparing((FavoriteEntity f) -> epochMillis(f.getCreatedAt())).reversed()
                .thenComparing(FavoriteEntity::getId));

        Slice<FavoriteEntity> slice = slice(rows, cursor, limit);
        List<FavoriteViews.WithJob> views = slice.items().stream()
                .map(f -> FavoriteViews.WithJob.from(f, jobSummary(f.getJobId())))
                .toList();
        return new PageViews.Page<>(views,
                new PageViews.Pagination(slice.nextCursor(), slice.hasMore(), rows.size()));
    }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    private JobViews.Summary jobSummary(String jobId) {
        JobEntity job = jobMapper.selectById(jobId);
        return job == null ? null : jobQueryService.summaryOf(job);
    }

    private record Cursor(long k, String id) {
    }

    private record Slice<T>(List<T> items, String nextCursor, boolean hasMore) {
    }

    private static Slice<FavoriteEntity> slice(List<FavoriteEntity> rows, String rawCursor, Integer limit) {
        int size = clampLimit(limit);
        int start = 0;
        Cursor cursor = trimToNull(rawCursor) == null ? null : decodeCursor(rawCursor);
        if (cursor != null) {
            start = firstIndexAfter(rows, cursor);
        }
        int end = Math.min(start + size, rows.size());
        List<FavoriteEntity> items = new ArrayList<>(rows.subList(start, end));
        boolean hasMore = end < rows.size();
        String nextCursor = null;
        if (hasMore && !items.isEmpty()) {
            FavoriteEntity last = items.get(items.size() - 1);
            nextCursor = encodeCursor(epochMillis(last.getCreatedAt()), last.getId());
        }
        return new Slice<>(items, nextCursor, hasMore);
    }

    private static int firstIndexAfter(List<FavoriteEntity> rows, Cursor cursor) {
        for (int i = 0; i < rows.size(); i++) {
            FavoriteEntity row = rows.get(i);
            long k = epochMillis(row.getCreatedAt());
            if (k < cursor.k() || (k == cursor.k() && row.getId().compareTo(cursor.id()) > 0)) {
                return i;
            }
        }
        return rows.size();
    }

    private static String encodeCursor(long keyMillis, String id) {
        try {
            ObjectNode node = JSON.createObjectNode();
            node.put("k", keyMillis);
            node.put("id", id);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(JSON.writeValueAsBytes(node));
        } catch (Exception e) {
            throw new IllegalStateException("游标编码失败", e);
        }
    }

    private static Cursor decodeCursor(String raw) {
        byte[] bytes;
        try {
            bytes = Base64.getUrlDecoder().decode(raw);
        } catch (IllegalArgumentException urlSafeFailed) {
            try {
                bytes = Base64.getDecoder().decode(raw);
            } catch (IllegalArgumentException standardFailed) {
                throw invalidCursor();
            }
        }
        try {
            JsonNode node = JSON.readTree(bytes);
            if (node == null || !node.path("k").isNumber() || !node.path("id").isTextual()) {
                throw invalidCursor();
            }
            return new Cursor(node.path("k").asLong(), node.path("id").asText());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw invalidCursor();
        }
    }

    private static BusinessException invalidCursor() {
        return new BusinessException(ApiCodes.PARAM_INVALID, "分页游标无效");
    }

    private static int clampLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.max(1, Math.min(limit, MAX_LIMIT));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static long epochMillis(LocalDateTime time) {
        return time == null ? 0 : time.toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
