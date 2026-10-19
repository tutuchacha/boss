package com.pinliao.backend.ai.web;

import com.pinliao.backend.ai.service.AiConnectivityService;
import com.pinliao.backend.ai.view.AiViews;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 模块接口（Step 4）。
 *
 * <p>T-AI0 只暴露两个「契约外」的自检端点；正式问答 / 检索接口在 T-AI1~T-AI4 增量接入，
 * 规划见 {@code docs/step4-ai-plan.md} §6.3。</p>
 *
 * <p>安全：{@code /api/v1/**} 默认受 {@code WebAuthConfig} 拦截器保护，因此这两个端点需要
 * 登录态（避免未授权调用产生费用）。</p>
 */
@RestController
@RequestMapping("/api/v1/ai")
@Tag(name = "AI 模块", description = "连通性自检（Step 4 T-AI0；契约外）")
public class AiController {

    private final AiConnectivityService aiConnectivityService;

    public AiController(AiConnectivityService aiConnectivityService) {
        this.aiConnectivityService = aiConnectivityService;
    }

    /** AI 状态（只回显配置，不调用上游）。 */
    @GetMapping("/status")
    public AiViews.Status status() {
        return aiConnectivityService.status();
    }

    /** 连通性自检：真实调用一次 chat + embedding（需登录；消耗少量额度）。 */
    @GetMapping("/ping")
    public AiViews.Ping ping() {
        return aiConnectivityService.ping();
    }
}
