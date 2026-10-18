package com.pinliao.backend.favorite.web;

import com.pinliao.backend.common.api.PageViews;
import com.pinliao.backend.favorite.dto.FavoriteRequests;
import com.pinliao.backend.favorite.service.FavoriteService;
import com.pinliao.backend.favorite.view.FavoriteViews;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 收藏接口（契约 §11.1~§11.3）。 */
@RestController
@RequestMapping("/api/v1/favorites")
@Tag(name = "11. 收藏", description = "收藏 / 取消收藏 / 我的收藏列表（契约 §11）")
public class FavoriteController {

    private final FavoriteService favoriteService;

    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    /** §11.1 收藏职位（JOB_SEEKER；40910 已收藏）。 */
    @PostMapping
    public FavoriteViews.Favorite add(@Valid @RequestBody FavoriteRequests.AddRequest body) {
        return favoriteService.add(body);
    }

    /** §11.2 取消收藏（JOB_SEEKER；幂等）。 */
    @DeleteMapping("/{jobId}")
    public Map<String, Object> remove(@PathVariable String jobId) {
        favoriteService.remove(jobId);
        return Map.of();
    }

    /** §11.3 我的收藏列表（游标分页，含 total）。 */
    @GetMapping
    public PageViews.Page<FavoriteViews.WithJob> list(
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return favoriteService.list(cursor, limit);
    }
}
