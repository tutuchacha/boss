package com.pinliao.backend.dict.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** cities 表（跨租户共享字典；V4 种子 9 城）。 */
@Data
@TableName("cities")
public class CityEntity {

    @TableId(type = IdType.INPUT)
    private String id;

    /** 行政区划码（演示值 10000+） */
    private String code;
    private String name;
    private String province;
    /** 是否热门城市 */
    private Boolean hot;
    private Integer sort;
}
