package com.pinliao.backend.dict.service;

import com.pinliao.backend.dict.entity.CategoryEntity;
import com.pinliao.backend.dict.entity.CityEntity;
import com.pinliao.backend.dict.mapper.CategoryMapper;
import com.pinliao.backend.dict.mapper.CityMapper;
import com.pinliao.backend.dict.view.DictViews;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** T3a 分类字典（§7）：分类树组装 / 城市映射 / 枚举与 mock 一致。 */
class DictServiceTest {

    private final CityMapper cityMapper = mock(CityMapper.class);
    private final CategoryMapper categoryMapper = mock(CategoryMapper.class);
    private final DictService service = new DictService(cityMapper, categoryMapper);

    @Test
    void 分类树_按sort组装且仅一级为根() {
        when(categoryMapper.selectList(any())).thenReturn(List.of(
                category("sub-2", "cat-1", "后端开发", 1),
                category("cat-2", null, "产品", 1),
                category("sub-1", "cat-1", "前端开发", 0),
                category("cat-1", null, "互联网", 0)));

        List<DictViews.CategoryNode> tree = service.categories();

        assertEquals(2, tree.size());
        assertEquals("cat-1", tree.get(0).id());
        assertEquals("cat-2", tree.get(1).id());
        assertEquals(List.of("sub-1", "sub-2"),
                tree.get(0).children().stream().map(DictViews.CategoryNode::id).toList());
        assertTrue(tree.get(1).children().isEmpty());
    }

    @Test
    void 城市_字段映射与hot默认值() {
        when(cityMapper.selectList(any())).thenReturn(List.of(
                city("city-0", "10000", "全国", 0, false),
                city("city-1", "10001", "上海", 1, true)));

        List<DictViews.City> cities = service.cities();

        assertEquals(2, cities.size());
        assertEquals("全国", cities.get(0).name());
        assertFalse(cities.get(0).hot());
        assertEquals(0, cities.get(0).sort());
        assertTrue(cities.get(1).hot());
        assertEquals("10001", cities.get(1).code());
    }

    @Test
    void 字典枚举与前端mock一致() {
        DictViews.Bundle bundle = service.dict();

        assertEquals(List.of("不限", "应届", "1-3年", "3-5年", "5-10年"), bundle.exps());
        assertEquals(List.of("学历不限", "大专", "本科", "硕士", "博士"), bundle.edus());
        assertEquals(List.of("不限", "10K以下", "10-20K", "20-40K", "40K以上"), bundle.salaryRanges());
        assertEquals(List.of("五险一金", "带薪年假", "年度体检", "弹性工作", "年终奖", "餐补"), bundle.welfare());
    }

    private static CategoryEntity category(String id, String parentId, String name, int sort) {
        CategoryEntity category = new CategoryEntity();
        category.setId(id);
        category.setParentId(parentId);
        category.setName(name);
        category.setSort(sort);
        return category;
    }

    private static CityEntity city(String id, String code, String name, int sort, boolean hot) {
        CityEntity city = new CityEntity();
        city.setId(id);
        city.setCode(code);
        city.setName(name);
        city.setSort(sort);
        city.setHot(hot);
        return city;
    }
}
