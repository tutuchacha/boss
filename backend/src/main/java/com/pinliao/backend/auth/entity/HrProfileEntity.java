package com.pinliao.backend.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** hr_profiles 表（docs/data-model.md §3.6；租户私有表）。 */
@Data
@TableName("hr_profiles")
public class HrProfileEntity {

    @TableId(type = IdType.INPUT)
    private String id;

    private String userId;
    private String companyId;
    private String name;
    private String title;
    private String avatarUrl;
    private String intro;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
