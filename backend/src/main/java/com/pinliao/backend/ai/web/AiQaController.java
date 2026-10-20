package com.pinliao.backend.ai.web;

import com.pinliao.backend.ai.dto.AiQaRequests;
import com.pinliao.backend.ai.qa.HrResumeQaService;
import com.pinliao.backend.ai.view.AiQaViews;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 单简历问答接口（Step 4 T-AI2）。
 *
 * <p>{@code POST /api/v1/hr/applications/{applicationId}/ai/qa}：HR 就某条投递的简历提问，
 * 返回带引用的回答。接口受 {@code /api/v1/**} 登录拦截器保护，并在 service 内要求
 * HR / COMPANY_ADMIN 与本公司归属（跨公司 404）。</p>
 */
@RestController
@Tag(name = "AI 问答", description = "HR 单简历问答（Step 4 T-AI2；契约外，后续并入正式契约）")
public class AiQaController {

    private final HrResumeQaService qaService;

    public AiQaController(HrResumeQaService qaService) {
        this.qaService = qaService;
    }

    @PostMapping("/api/v1/hr/applications/{applicationId}/ai/qa")
    public AiQaViews.Answer ask(@PathVariable String applicationId,
                                @RequestBody(required = false) AiQaRequests.Ask request) {
        return qaService.ask(applicationId, request == null ? null : request.question());
    }
}
