package com.pinliao.backend.ai.qa;

import com.pinliao.backend.ai.view.AiQaViews;

/**
 * 问答流式输出回调（Step 4 T-AI3）。
 *
 * <p>service 只依赖本接口，不依赖 Spring MVC 的 {@code SseEmitter}：Web 层负责把
 * {@code delta / citation / done} 翻译成 SSE 事件，单测可直接用列表收集事件。</p>
 *
 * <p>调用顺序保证：若干 {@code delta} → 若干 {@code citation} → 一次 {@code done}；
 * 无依据（相似度不足）时不调用对话模型，只发一次 {@code delta}（固定话术）+ {@code done}。</p>
 */
public interface AiStreamSink {

    /** 增量文本。 */
    void delta(String text);

    /** 一条引用（在 {@code done} 之前发出，前端可先渲染来源卡片）。 */
    void citation(AiQaViews.Citation citation);

    /** 结束事件：携带完整回答（含引用兜底改写后的正文）。 */
    void done(AiQaViews.Answer answer);
}
