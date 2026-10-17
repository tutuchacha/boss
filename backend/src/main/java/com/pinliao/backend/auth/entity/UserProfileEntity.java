package com.pinliao.backend.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** user_profiles 表（docs/data-model.md §3.2）。 */
@Data
@TableName("user_profiles")
public class UserProfileEntity {

    @TableId(type = IdType.INPUT)
    private String id;

    private String userId;
    private String name;
    private String avatarUrl;
    /** male / female / other */
    private String gender;
    private LocalDate birthDate;
    private Integer workYears;
    private String edu;
    private String expectCat;
    private String expectSub;
    private String expectCity;
    private Integer expectSalaryMin;
    private Integer expectSalaryMax;
    /** ACTIVE / PASSIVE / NOT_LOOKING */
    private String seekingStatus;
    private String defaultResumeId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
