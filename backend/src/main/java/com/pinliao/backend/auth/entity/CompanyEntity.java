package com.pinliao.backend.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** companies 表（docs/data-model.md §3.4；即租户主表）。 */
@Data
@TableName("companies")
public class CompanyEntity {

    @TableId(type = IdType.INPUT)
    private String id;

    private String name;
    private String logoUrl;
    private String intro;
    private String industry;
    /** 融资阶段：天使轮 / B轮 / 已上市 等 */
    private String stage;
    /** 规模：20-99人 等 */
    private String size;
    private String website;
    private Boolean verified;
    /** ACTIVE / SUSPENDED / PENDING */
    private String status;
    private String customDomain;
    private String themeColor;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
