package com.pinliao.backend.ai.provider;

import com.pinliao.backend.ai.AiCodes;
import com.pinliao.backend.ai.config.AiProperties;
import com.pinliao.backend.ai.spi.AiEmbeddingModel;
import com.pinliao.backend.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 真实向量模型适配器（{@code pinliao.ai.provider=springai}，默认）。
 *
 * <p>把 Spring AI 的 {@link EmbeddingModel} 收敛到自研 SPI，并负责两件上游约束：</p>
 * <ul>
 *   <li>百炼 {@code text-embedding-v4} 单次最多 10 条文本，超出会 400；此处按 {@code embeddingBatchSize} 切批；</li>
 *   <li>上游异常统一转成业务码 {@code 50020}，不把原始报文透给前端（可能含密钥片段）。</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(prefix = "pinliao.ai", name = "provider", havingValue = "springai", matchIfMissing = true)
public class SpringAiEmbeddingAdapter implements AiEmbeddingModel {

    private static final Logger log = LoggerFactory.getLogger(SpringAiEmbeddingAdapter.class);

    private final EmbeddingModel delegate;
    private final String modelId;
    private final int dimensions;
    private final int batchSize;

    public SpringAiEmbeddingAdapter(EmbeddingModel delegate,
                                    AiProperties properties,
                                    @Value("${spring.ai.openai.embedding.options.model:text-embedding-v4}") String modelId,
                                    @Value("${spring.ai.openai.embedding.options.dimensions:1024}") int dimensions) {
        this.delegate = delegate;
        this.modelId = modelId;
        this.dimensions = dimensions > 0 ? dimensions : 1024;
        this.batchSize = properties.embeddingBatchSize();
    }

    @Override
    public String modelId() {
        return modelId;
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    @Override
    public List<float[]> embedAll(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        List<float[]> vectors = new ArrayList<>(texts.size());
        for (int start = 0; start < texts.size(); start += batchSize) {
            List<String> batch = texts.subList(start, Math.min(texts.size(), start + batchSize));
            vectors.addAll(embedBatch(batch));
        }
        return vectors;
    }

    private List<float[]> embedBatch(List<String> batch) {
        List<float[]> vectors;
        try {
            vectors = delegate.embed(batch);
        } catch (RuntimeException e) {
            log.warn("AI 向量调用失败（model={}，batch={}）：{}", modelId, batch.size(), e.toString());
            throw new BusinessException(AiCodes.UPSTREAM_FAILED, "AI 向量调用失败：" + e.getMessage());
        }
        if (vectors == null || vectors.size() != batch.size()) {
            throw new BusinessException(AiCodes.UPSTREAM_FAILED,
                    "AI 向量返回数量不匹配：期望 " + batch.size() + "，实际 "
                            + (vectors == null ? "null" : vectors.size()));
        }
        for (float[] vector : vectors) {
            if (vector == null || vector.length == 0) {
                throw new BusinessException(AiCodes.UPSTREAM_FAILED, "AI 向量返回空向量");
            }
        }
        return vectors;
    }
}
