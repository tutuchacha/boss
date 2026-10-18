package com.pinliao.backend.application.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** interviews 表（契约 §10；租户私有表带 company_id）。 */
@Data
@TableName("interviews")
public class InterviewEntity {

    @TableId(type = IdType.INPUT)
    private String id;

    private String applicationId;
    private String companyId;
    private LocalDateTime scheduledAt;
    /** ONSITE / VIDEO / PHONE */
    private String format;
    private String location;
    private String onlineUrl;
    private String note;
    /** PENDING / ACCEPTED / DECLINED / COMPLETED / CANCELLED */
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
