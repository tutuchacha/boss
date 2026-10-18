package com.pinliao.backend.dict.view;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 分类 / 字典接口视图（契约 §7；字段与前端 {@code src/types/api.ts} 的
 * CategoryNode / City / DictBundle 对齐；null 字段不序列化）。
 */
public final class DictViews {

    private DictViews() {
    }

    /** 分类树节点（children 为一叶子数组，一级节点的 children 即二级岗位）。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CategoryNode(String id, String name, int sort, String icon, List<CategoryNode> children) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record City(String id, String code, String name, String province, boolean hot, int sort) {
    }

    /** 字典聚合（§7.3）：经验 / 学历 / 薪资区间 / 福利枚举。 */
    public record Bundle(List<String> exps, List<String> edus, List<String> salaryRanges, List<String> welfare) {
    }
}
