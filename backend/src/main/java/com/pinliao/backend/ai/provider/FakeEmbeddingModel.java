package com.pinliao.backend.ai.provider;

import com.pinliao.backend.ai.spi.AiEmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 本地确定性假向量（{@code pinliao.ai.provider=fake}）。
 *
 * <p>用途：单元测试与冒烟脚本在**无密钥 / 不产生费用**的前提下跑通索引链路。
 * 算法是「词袋哈希 + L2 归一化」：同一文本必然得到同一向量，共享词越多的文本余弦越接近，
 * 足以验证「写入 → 复用 → 检索排序」的管道，但**不具备真实语义理解**，
 * 因此仅当 {@code provider=fake} 时装配，演示与验收一律使用 {@code springai}。</p>
 */
@Component
@ConditionalOnProperty(prefix = "pinliao.ai", name = "provider", havingValue = "fake")
public class FakeEmbeddingModel implements AiEmbeddingModel {

    private static final String MODEL_ID = "fake-bag-of-words-v1";

    private final int dimensions;

    public FakeEmbeddingModel(@Value("${spring.ai.openai.embedding.options.dimensions:1024}") int dimensions) {
        this.dimensions = Math.max(1, dimensions);
    }

    @Override
    public String modelId() {
        return MODEL_ID;
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    @Override
    public List<float[]> embedAll(List<String> texts) {
        List<float[]> vectors = new ArrayList<>(texts.size());
        for (String text : texts) {
            vectors.add(embed(text));
        }
        return vectors;
    }

    private float[] embed(String text) {
        float[] vector = new float[dimensions];
        for (String token : tokenize(text)) {
            vector[Math.floorMod(token.hashCode(), dimensions)] += 1.0f;
        }
        double norm = 0;
        for (float value : vector) {
            norm += value * value;
        }
        if (norm == 0) {
            vector[0] = 1.0f;
            return vector;
        }
        double scale = 1.0 / Math.sqrt(norm);
        for (int i = 0; i < vector.length; i++) {
            vector[i] = (float) (vector[i] * scale);
        }
        return vector;
    }

    /** 中文按单字切、拉丁按词切，统一小写；标点与空白忽略。 */
    static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        if (text == null) {
            return tokens;
        }
        StringBuilder latin = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
                flush(latin, tokens);
                tokens.add(String.valueOf(c));
            } else if (Character.isLetterOrDigit(c)) {
                latin.append(Character.toLowerCase(c));
            } else {
                flush(latin, tokens);
            }
        }
        flush(latin, tokens);
        return tokens;
    }

    private static void flush(StringBuilder buffer, List<String> tokens) {
        if (!buffer.isEmpty()) {
            tokens.add(buffer.toString().toLowerCase(Locale.ROOT));
            buffer.setLength(0);
        }
    }
}
