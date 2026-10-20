package com.pinliao.backend.ai.spi;

import java.util.function.Consumer;

/**
 * 对话模型 SPI（Step 4 T-AI2 / T-AI3）。
 *
 * <p>与 {@link AiEmbeddingModel} 同样的目的：业务代码（RAG / 问答）只依赖本接口，
 * 不直接依赖 Spring AI / LangChain4j。切换框架或升级时只需替换 {@code ai/provider} 下的实现。</p>
 *
 * <p>实现方负责：把上游异常统一转成 {@code 50020}，不把原始报文（可能含密钥片段）透给前端；
 * 返回结果中的 token 统计允许为 {@code null}（部分兼容实现不返回 usage）。</p>
 */
public interface AiChatClient {

    /** 模型标识（回显给前端，便于排查「用的是哪个模型」）。 */
    String modelId();

    /**
     * 单轮对话（非流式）。系统提示与用户提示分开传入，避免把「资料」拼进系统指令区。
     *
     * @param systemPrompt 系统指令（角色、引用规则、防注入声明）
     * @param userPrompt   用户消息（编号资料 + HR 问题）
     */
    ChatResult chat(String systemPrompt, String userPrompt);

    /**
     * 单轮对话（流式，T-AI3）：每收到一段增量文本立即回调 {@code onDelta}，
     * 全部结束后返回与 {@link #chat} 相同结构的完整结果。
     *
     * <p>约束：实现必须保证「回调的增量拼接 == 返回的完整文本」，下游据此边推边渲染、
     * 结束后再用完整文本做引用装配（模型漏标兜底会改写正文，见 {@code CitationAssembler}）。</p>
     *
     * @param onDelta 增量文本回调；可能被调用多次，单次内容非空
     */
    ChatResult stream(String systemPrompt, String userPrompt, Consumer<String> onDelta);

    /**
     * @param text       模型回答正文
     * @param totalTokens 本次调用 token 合计；上游未返回时为 null
     */
    record ChatResult(String text, Integer totalTokens) {
    }
}
