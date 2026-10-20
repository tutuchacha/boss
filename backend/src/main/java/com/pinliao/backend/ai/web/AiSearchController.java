package com.pinliao.backend.ai.web;

import com.pinliao.backend.ai.dto.AiSearchRequests;
import com.pinliao.backend.ai.search.SemanticTalentSearchService;
import com.pinliao.backend.ai.view.AiSearchViews;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 语义人才检索接口（Step 4 T-AI4）。
 *
 * <p>与关键词版 {@code POST /api/v1/hr/candidates/match} 并行，供人才筛选页「语义模式」A/B 对比。
 * 鉴权 / 开关 / 配额 / 租户过滤都在 service 内完成。</p>
 */
@RestController
@Tag(name = "AI 语义人才检索", description = "HR 跨候选人语义检索（Step 4 T-AI4）")
public class AiSearchController {

    private final SemanticTalentSearchService searchService;

    public AiSearchController(SemanticTalentSearchService searchService) {
        this.searchService = searchService;
    }

    @PostMapping("/api/v1/hr/talent/semantic-search")
    public List<AiSearchViews.SemanticMatch> semanticSearch(
            @RequestBody(required = false) AiSearchRequests.SemanticSearch input) {
        return searchService.search(input);
    }
}
