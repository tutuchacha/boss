package com.pinliao.backend.user.view;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 用户模块视图（契约 §2.6 / §2.7；对齐前端 {@code src/types/api.ts} 的 UserPreferences）。
 *
 * <p>外观偏好当前只有 theme，后续字段按契约以可选形式追加；null 字段不序列化。</p>
 */
public final class UserViews {

    private UserViews() {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Preferences(String theme) {
    }
}
