package com.pinliao.backend.application.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * applications 表（契约 §9；租户私有表带 company_id）。
 *
 * <p>created_at / updated_at 由 service 显式写入并截到毫秒，保证「写响应」与「后续读库」时间一致
 * （MySQL DATETIME(3)；对齐 T3b 的 publishedAt 处理）。</p>
 */
@Data
@TableName("applications")
public class ApplicationEntity {

    @TableId(type = IdType.INPUT)
    private String id;

    private String userId;
    private String jobId;
    private String companyId;
    private String resumeId;
    /** SUBMITTED / VIEWED / INTERVIEW / OFFER / REJECTED / WITHDRAWN */
    private String status;
    private String coverLetter;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
}
