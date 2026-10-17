package com.pinliao.backend.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** company_members 表（docs/data-model.md §3.5；租户私有表）。 */
@Data
@TableName("company_members")
public class CompanyMemberEntity {

    @TableId(type = IdType.INPUT)
    private String id;

    private String userId;
    private String companyId;
    /** ADMIN / HR */
    private String role;
    private LocalDateTime joinedAt;
}
