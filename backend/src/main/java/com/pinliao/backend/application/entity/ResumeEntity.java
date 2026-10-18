package com.pinliao.backend.application.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * resumes 表（契约 §11；T4 仅读取投递详情中的简历快照）。
 *
 * <p>content 保持原始 JSON 字符串，由视图层解析为 JsonNode，避免在 T4 提前固化 §11 的强类型结构。</p>
 */
@Data
@TableName("resumes")
public class ResumeEntity {

    @TableId(type = IdType.INPUT)
    private String id;

    private String userId;
    private String name;
    private String content;
    private String attachmentUrl;
    private Boolean isDefault;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
}
