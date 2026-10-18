package com.pinliao.backend.dict.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * categories 表（跨租户共享字典；V4 种子 17 一级 + 67 二级）。
 *
 * <p>一级分类 {@code parentId == null}；二级挂在 {@code parentId} 下，按 {@code sort} 排序。</p>
 */
@Data
@TableName("categories")
public class CategoryEntity {

    @TableId(type = IdType.INPUT)
    private String id;

    private String parentId;
    private String name;
    private Integer sort;
    private String icon;
    /** 责任描述模板（HR 发布职位时可选填充，T3b 使用） */
    private String responsibilitiesTemplate;
}
