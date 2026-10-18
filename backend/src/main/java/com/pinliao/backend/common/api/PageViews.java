package com.pinliao.backend.common.api;

import java.util.List;

/**
 * 通用游标分页视图（契约 §3.2）。
 *
 * <p>与 {@code JobViews.Page} 的 JSON 形状一致；T4 投递 / 面试模块复用，避免跨模块依赖职位视图。
 * {@code nextCursor} 必须显式输出 null（不能加 NON_NULL）。</p>
 */
public final class PageViews {

    private PageViews() {
    }

    public record Page<T>(List<T> list, Pagination pagination) {
    }

    public record Pagination(String nextCursor, boolean hasMore, Integer total) {
    }
}
