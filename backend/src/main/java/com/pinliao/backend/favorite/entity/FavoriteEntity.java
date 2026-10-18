package com.pinliao.backend.favorite.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** favorites 表（全局表；求职者跨租户收藏职位）。 */
@Data
@TableName("favorites")
public class FavoriteEntity {

    @TableId(type = IdType.INPUT)
    private String id;

    private String userId;
    private String jobId;
    private LocalDateTime createdAt;
}
