package com.pinliao.backend.ai.web;

import com.pinliao.backend.ai.dto.AiIndexRequests;
import com.pinliao.backend.ai.index.ResumeIndexService;
import com.pinliao.backend.ai.view.AiIndexViews;
import com.pinliao.backend.application.entity.ApplicationEntity;
import com.pinliao.backend.application.mapper.ApplicationMapper;
import com.pinliao.backend.auth.context.Roles;
import com.pinliao.backend.common.api.ApiCodes;
import com.pinliao.backend.common.exception.BusinessException;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 简历索引接口（Step 4 T-AI1）。
 *
 * <ul>
 *   <li>{@code POST /api/v1/ai/index/rebuild}：HR / 管理员触发重建（不传 resumeId 为全量）；
 *       索引是全局的，但接口只回计数，不返回任何简历内容；</li>
 *   <li>{@code GET /api/v1/hr/applications/{applicationId}/ai/index-status}：候选人详情页展示
 *       「索引是否就绪」，走投递记录校验可见性（跨公司 404，不泄露存在性）。</li>
 * </ul>
 *
 * <p>调用方需登录：{@code /api/v1/**} 默认受 {@code WebAuthConfig} 拦截器保护。</p>
 */
@RestController
@Tag(name = "AI 索引", description = "简历分块索引重建与状态（Step 4 T-AI1；契约外，后续并入正式契约）")
public class AiIndexController {

    private final ResumeIndexService indexService;
    private final ApplicationMapper applicationMapper;

    public AiIndexController(ResumeIndexService indexService, ApplicationMapper applicationMapper) {
        this.indexService = indexService;
        this.applicationMapper = applicationMapper;
    }

    @PostMapping("/api/v1/ai/index/rebuild")
    public AiIndexViews.Rebuild rebuild(@RequestBody(required = false) AiIndexRequests.Rebuild request) {
        Roles.requireManager();
        String resumeId = request == null ? null : request.resumeId();
        if (resumeId == null || resumeId.isBlank()) {
            return indexService.rebuildAll();
        }
        return indexService.rebuildOne(resumeId);
    }

    @GetMapping("/api/v1/hr/applications/{applicationId}/ai/index-status")
    public AiIndexViews.IndexStatus indexStatus(@PathVariable String applicationId) {
        Roles.requireManager();
        ApplicationEntity application = applicationMapper.selectById(applicationId);
        if (application == null || application.getDeletedAt() != null) {
            throw new BusinessException(ApiCodes.NOT_FOUND, "投递不存在");
        }
        return indexService.status(application.getResumeId());
    }
}
