package com.pinliao.backend.dict.web;

import com.pinliao.backend.dict.service.DictService;
import com.pinliao.backend.dict.view.DictViews;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

/**
 * 分类 / 字典模块接口（契约 docs/api-endpoints.md §7.1~§7.3；公开，缓存 24h）。
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "7. 分类/字典模块", description = "分类树 / 城市 / 字典聚合（契约 §7；公开只读）")
public class DictController {

    private static final CacheControl DAILY_CACHE = CacheControl.maxAge(Duration.ofHours(24)).cachePublic();

    private final DictService dictService;

    public DictController(DictService dictService) {
        this.dictService = dictService;
    }

    /** §7.1 分类树（公开；Cache-Control 24h）。 */
    @GetMapping("/categories")
    public ResponseEntity<List<DictViews.CategoryNode>> categories() {
        return ResponseEntity.ok().cacheControl(DAILY_CACHE).body(dictService.categories());
    }

    /** §7.2 城市列表（公开；Cache-Control 24h）。 */
    @GetMapping("/cities")
    public ResponseEntity<List<DictViews.City>> cities() {
        return ResponseEntity.ok().cacheControl(DAILY_CACHE).body(dictService.cities());
    }

    /** §7.3 字典聚合（公开；Cache-Control 24h）。 */
    @GetMapping("/dict")
    public ResponseEntity<DictViews.Bundle> dict() {
        return ResponseEntity.ok().cacheControl(DAILY_CACHE).body(dictService.dict());
    }
}
