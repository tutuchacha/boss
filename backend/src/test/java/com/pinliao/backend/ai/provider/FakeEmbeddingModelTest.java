package com.pinliao.backend.ai.provider;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 本地假向量单测（Step 4 T-AI1）：确定性 + 归一化 + 相似文本更接近。
 */
class FakeEmbeddingModelTest {

    private final FakeEmbeddingModel model = new FakeEmbeddingModel(64);

    @Test
    void sameTextProducesSameNormalizedVector() {
        float[] first = model.embedAll(List.of("Java 后端开发，熟悉 Spring")).get(0);
        float[] second = model.embedAll(List.of("Java 后端开发，熟悉 Spring")).get(0);

        assertArrayEquals(first, second);
        assertEquals(64, first.length);
        assertEquals(1.0, norm(first), 1e-5);
        assertEquals("fake-bag-of-words-v1", model.modelId());
        assertEquals(64, model.dimensions());
    }

    @Test
    void sharedTokensAreCloserThanUnrelatedText() {
        float[] java = model.embedAll(List.of("Java 后端开发")).get(0);
        float[] javaSpring = model.embedAll(List.of("Java 后端开发，Spring Boot")).get(0);
        float[] frontend = model.embedAll(List.of("React 前端开发，组件库")).get(0);

        assertTrue(cosine(java, javaSpring) > cosine(java, frontend));
    }

    @Test
    void emptyInputReturnsEmptyListWithoutTouchingAnything() {
        assertEquals(0, model.embedAll(List.of()).size());
    }

    private static double norm(float[] vector) {
        double sum = 0;
        for (float value : vector) {
            sum += value * value;
        }
        return Math.sqrt(sum);
    }

    private static double cosine(float[] a, float[] b) {
        double dot = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
        }
        return dot / (norm(a) * norm(b));
    }
}
