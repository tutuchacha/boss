package com.pinliao.backend.ai.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pinliao.backend.ai.AiCodes;
import com.pinliao.backend.ai.dto.AiQaRequests;
import com.pinliao.backend.ai.qa.HrResumeQaService;
import com.pinliao.backend.ai.view.AiQaViews;
import com.pinliao.backend.common.exception.BusinessException;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.ExecutorService;

/**
 * 单简历问答接口（Step 4 T-AI2 非流式 / T-AI3 流式）。
 *
 * <ul>
 *   <li>{@code POST /api/v1/hr/applications/{applicationId}/ai/qa}：一次性返回完整回答（JSON）。</li>
 *   <li>{@code POST .../ai/qa/stream}：同参数，返回 {@code text/event-stream}（delta / citation / done / error）。</li>
 * </ul>
 *
 * <p>两个接口共用 {@link HrResumeQaService#prepare}：开关 / 角色 / 入参 / 归属 / 配额 / 索引 / 检索
 * 都在**返回 emitter 之前**同步完成，因此这些错误仍是普通 JSON 包络（HTTP 状态由业务码前 3 位决定），
 * 前端先看响应头再决定是否读流。流开始后的失败才走 {@code error} 事件。</p>
 */
@RestController
@Tag(name = "AI 问答", description = "HR 单简历问答（Step 4 T-AI2 非流式 / T-AI3 流式；契约外，后续并入正式契约）")
public class AiQaController {

    private static final Logger log = LoggerFactory.getLogger(AiQaController.class);
    private static final long STREAM_TIMEOUT_MS = 120_000L;

    private final HrResumeQaService qaService;
    private final ObjectMapper objectMapper;
    private final ExecutorService streamExecutor;

    public AiQaController(HrResumeQaService qaService,
                          ObjectMapper objectMapper,
                          @Qualifier("aiStreamExecutor") ExecutorService streamExecutor) {
        this.qaService = qaService;
        this.objectMapper = objectMapper;
        this.streamExecutor = streamExecutor;
    }

    @PostMapping("/api/v1/hr/applications/{applicationId}/ai/qa")
    public AiQaViews.Answer ask(@PathVariable String applicationId,
                                @RequestBody(required = false) AiQaRequests.Ask request) {
        return qaService.ask(applicationId, request == null ? null : request.question());
    }

    @PostMapping(value = "/api/v1/hr/applications/{applicationId}/ai/qa/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter askStream(@PathVariable String applicationId,
                                @RequestBody(required = false) AiQaRequests.Ask request,
                                HttpServletResponse response) {
        // 同步阶段：任何业务异常都在这里抛出 → 走上方的 JSON 包络，而不是半截 SSE
        HrResumeQaService.Prepared prepared = qaService.prepare(applicationId,
                request == null ? null : request.question());

        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no"); // nginx / 部分反代：禁用响应缓冲

        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        AiQaSseSink sink = new AiQaSseSink(emitter, objectMapper);
        emitter.onTimeout(sink::complete);
        emitter.onError(e -> sink.complete());

        streamExecutor.execute(() -> {
            try {
                qaService.stream(prepared, sink);
            } catch (BusinessException e) {
                sink.error(e.getCode(), e.getMessage());
            } catch (RuntimeException e) {
                log.error("AI 流式问答异常（applicationId={}）", prepared.applicationId(), e);
                sink.error(AiCodes.UPSTREAM_FAILED, "AI 服务暂时不可用，请稍后重试");
            } finally {
                sink.complete();
            }
        });
        return emitter;
    }
}
