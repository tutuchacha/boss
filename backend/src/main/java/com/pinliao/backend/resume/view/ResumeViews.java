package com.pinliao.backend.resume.view;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.pinliao.backend.application.entity.ResumeEntity;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 简历视图（契约 §3.1~§3.6；字段与 {@code src/types/api.ts} 的 Resume 对齐）。
 *
 * <p>content 保持 JSON 节点输出，避免把已有简历里后端未建模的字段裁掉；
 * 时间统一 ISO-8601 UTC，null 字段不序列化。</p>
 */
public final class ResumeViews {

    private ResumeViews() {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Resume(
            String id,
            String userId,
            String name,
            JsonNode content,
            String attachmentUrl,
            Boolean isDefault,
            Instant createdAt,
            Instant updatedAt) {

        public static Resume from(ResumeEntity r, JsonNode content) {
            return new Resume(r.getId(), r.getUserId(), r.getName(), content, r.getAttachmentUrl(),
                    r.getIsDefault(), instant(r.getCreatedAt()), instant(r.getUpdatedAt()));
        }
    }

    private static Instant instant(LocalDateTime time) {
        return time == null ? null : time.toInstant(ZoneOffset.UTC);
    }
}
