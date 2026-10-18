package com.pinliao.backend.job.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * jobs 表（docs/data-model.md；租户私有表，V3 见 backend/src/main/resources/db/migration）。
 *
 * <p>tags / welfare 为 MySQL JSON 列，经 {@link JacksonTypeHandler} 映射为 {@code List<String>}；
 * 因此 {@code @TableName} 必须开 {@code autoResultMap}。</p>
 *
 * <p>hr_id / hr_name / hr_title 为冗余展示字段（演示种子中除云杉陈女士外 HR 无账号，见 V3 头注释）。</p>
 */
@Data
@TableName(value = "jobs", autoResultMap = true)
public class JobEntity {

    /** 业务侧字符串主键（job-1 …；HR 新建由业务生成，见 T3b） */
    @TableId(type = IdType.INPUT)
    private String id;

    private String companyId;
    private String hrId;
    private String hrName;
    private String hrTitle;

    private String title;
    private Integer salaryMin;
    private Integer salaryMax;
    private String city;
    private String area;
    private String exp;
    private String edu;
    private String cat;
    private String sub;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> tags;

    private String responsibilities;
    private String requirements;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> welfare;

    /** OPEN / CLOSED / DRAFT */
    private String status;
    private LocalDateTime publishedAt;
    private Integer viewCount;
    private Integer applyCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 软删除（非空即对 HR 列表与求职者端不可见） */
    private LocalDateTime deletedAt;
}
