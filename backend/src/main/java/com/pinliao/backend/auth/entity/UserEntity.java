package com.pinliao.backend.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** users 表（docs/data-model.md §3.1；跨租户共享表，不带 companyId）。 */
@Data
@TableName("users")
public class UserEntity {

    /** 业务侧字符串主键（种子沿用 mock 的 u-* 约定；新建记录由业务生成，保持确定性）。 */
    @TableId(type = IdType.INPUT)
    private String id;

    private String phone;
    private String email;
    private String passwordHash;
    /** JOB_SEEKER / HR / COMPANY_ADMIN / PLATFORM_ADMIN */
    private String role;
    /** ACTIVE / SUSPENDED / DELETED */
    private String status;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    /** 软删除标记 */
    private LocalDateTime deletedAt;
}
