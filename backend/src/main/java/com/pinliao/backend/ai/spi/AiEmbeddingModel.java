package com.pinliao.backend.ai.spi;

import java.util.List;

/**
 * 向量模型 SPI（Step 4）：业务代码只依赖本接口，不直接依赖 Spring AI / LangChain4j。
 *
 * <p>这样后续切换框架（Spring AI → LangChain4j）或升级（Boot 4 / Spring AI 2.0）时，
 * 只需要替换 {@code ai/provider} 下的实现；索引 / 检索 / 问答代码不用动。</p>
 *
 * <p>实现方负责：批量请求按上游限制切批、失败统一抛 {@code 50020}、返回值维度校验。</p>
 */
public interface AiEmbeddingModel {

    /** 模型标识（写入 {@code resume_chunks.model}，换模型后需重建索引）。 */
    String modelId();

    /** 向量维度（写入 {@code resume_chunks.dim}）。 */
    int dimensions();

    /**
     * 批量向量化，返回值顺序与入参严格一致。
     *
     * @param texts 待向量化文本；空列表直接返回空列表，不发起上游调用
     */
    List<float[]> embedAll(List<String> texts);
}
