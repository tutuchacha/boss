package com.pinliao.backend.ai.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pinliao.backend.ai.qa.AiStreamSink;
import com.pinliao.backend.ai.view.AiQaViews;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * {@link AiStreamSink} 的 SSE 实现（Step 4 T-AI3）。
 *
 * <p>事件格式（契约见 {@code docs/step4-ai-plan.md} §6.3）：</p>
 * <pre>
 * event: delta      data: {"text":"..."}
 * event: citation   data: {"index":1,"chunkId":"rc_...", ...}
 * event: done       data: {"answer":"...","grounded":true,"citations":[...], ...}
 * event: error      data: {"code":50020,"message":"..."}
 * </pre>
 *
 * <p>payload 统一先序列化为**单行 JSON**（Jackson 会把正文里的换行转义成 {@code \n}），
 * 避免多行 {@code data:} 被 SSE 解析器拆成多条；客户端只需 {@code JSON.parse(data)}。</p>
 */
final class AiQaSseSink implements AiStreamSink {

    private static final Logger log = LoggerFactory.getLogger(AiQaSseSink.class);

    private final SseEmitter emitter;
    private final ObjectMapper objectMapper;
    private volatile boolean closed;

    AiQaSseSink(SseEmitter emitter, ObjectMapper objectMapper) {
        this.emitter = emitter;
        this.objectMapper = objectMapper;
    }

    @Override
    public void delta(String text) {
        send("delta", new AiQaViews.StreamDelta(text));
    }

    @Override
    public void citation(AiQaViews.Citation citation) {
        send("citation", citation);
    }

    @Override
    public void done(AiQaViews.Answer answer) {
        send("done", answer);
    }

    /** 流已开始后的失败（上游中断 / 超时）：以 error 事件告知，前端展示可读提示。 */
    void error(int code, String message) {
        send("error", new AiQaViews.StreamError(code, message));
    }

    /** 结束连接（幂等；客户端提前断开时也会走到这里）。 */
    void complete() {
        closed = true;
        try {
            emitter.complete();
        } catch (RuntimeException e) {
            log.debug("SSE 结束失败（客户端可能已断开）：{}", e.toString());
        }
    }

    private void send(String event, Object payload) {
        if (closed) {
            return;
        }
        try {
            emitter.send(SseEmitter.event()
                    .name(event)
                    .data(objectMapper.writeValueAsString(payload), MediaType.TEXT_PLAIN));
        } catch (Exception e) {
            // 客户端断开 / 超时：停止推送，但不要把异常抛回业务线程
            closed = true;
            log.debug("SSE 发送失败，停止推流：{}", e.toString());
        }
    }
}
