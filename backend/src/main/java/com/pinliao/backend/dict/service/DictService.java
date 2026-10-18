package com.pinliao.backend.dict.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pinliao.backend.dict.entity.CategoryEntity;
import com.pinliao.backend.dict.entity.CityEntity;
import com.pinliao.backend.dict.mapper.CategoryMapper;
import com.pinliao.backend.dict.mapper.CityMapper;
import com.pinliao.backend.dict.view.DictViews;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 分类 / 字典服务（契约 §7）。
 *
 * <p>分类树与城市来自数据库（V4 种子，跨租户共享）；§7.3 的枚举字典目前无数据表，
 * 常量与前端 {@code src/api/mock/dict.ts} 保持一致（后续如需运营配置可迁移到 sys_configs）。</p>
 */
@Service
public class DictService {

    /** 经验选项（对齐 mock EXPS_RAW） */
    static final List<String> EXPS = List.of("不限", "应届", "1-3年", "3-5年", "5-10年");
    /** 学历选项（对齐 mock EDUS_RAW） */
    static final List<String> EDUS = List.of("学历不限", "大专", "本科", "硕士", "博士");
    /** 薪资区间选项（对齐 mock SALS_RAW / SalaryRangeKey） */
    static final List<String> SALARY_RANGES = List.of("不限", "10K以下", "10-20K", "20-40K", "40K以上");
    /** 福利选项（对齐 mock WELFARE_RAW） */
    static final List<String> WELFARE = List.of("五险一金", "带薪年假", "年度体检", "弹性工作", "年终奖", "餐补");

    private final CityMapper cityMapper;
    private final CategoryMapper categoryMapper;

    public DictService(CityMapper cityMapper, CategoryMapper categoryMapper) {
        this.cityMapper = cityMapper;
        this.categoryMapper = categoryMapper;
    }

    /** §7.1 分类树（一级 17 个，各含二级岗位；按 sort 升序）。 */
    public List<DictViews.CategoryNode> categories() {
        List<CategoryEntity> all = categoryMapper.selectList(
                new LambdaQueryWrapper<CategoryEntity>().orderByAsc(CategoryEntity::getSort));
        Map<String, List<CategoryEntity>> childrenByParent = all.stream()
                .filter(category -> category.getParentId() != null)
                .collect(Collectors.groupingBy(CategoryEntity::getParentId));
        return all.stream()
                .filter(category -> category.getParentId() == null)
                .sorted(Comparator.comparingInt(DictService::sortOf))
                .map(parent -> toNode(parent, childrenByParent.getOrDefault(parent.getId(), List.of())))
                .toList();
    }

    /** §7.2 城市列表（按 sort 升序，含「全国」占位）。 */
    public List<DictViews.City> cities() {
        return cityMapper.selectList(new LambdaQueryWrapper<CityEntity>().orderByAsc(CityEntity::getSort))
                .stream()
                .map(city -> new DictViews.City(city.getId(), city.getCode(), city.getName(),
                        city.getProvince(), Boolean.TRUE.equals(city.getHot()), sortOf(city)))
                .toList();
    }

    /** §7.3 字典聚合。 */
    public DictViews.Bundle dict() {
        return new DictViews.Bundle(EXPS, EDUS, SALARY_RANGES, WELFARE);
    }

    private static DictViews.CategoryNode toNode(CategoryEntity parent, List<CategoryEntity> children) {
        List<DictViews.CategoryNode> childNodes = children.stream()
                .sorted(Comparator.comparingInt(DictService::sortOf))
                .map(child -> new DictViews.CategoryNode(child.getId(), child.getName(),
                        sortOf(child), child.getIcon(), List.of()))
                .toList();
        return new DictViews.CategoryNode(parent.getId(), parent.getName(),
                sortOf(parent), parent.getIcon(), childNodes);
    }

    private static int sortOf(CategoryEntity category) {
        return category.getSort() == null ? 0 : category.getSort();
    }

    private static int sortOf(CityEntity city) {
        return city.getSort() == null ? 0 : city.getSort();
    }
}
