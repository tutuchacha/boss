package com.pinliao.backend.ai.view;

/**
 * AI 索引视图（Step 4 T-AI1）。
 *
 * <p>时间统一为 UTC epoch 毫秒，与其他模块视图一致。</p>
 */
public final class AiIndexViews {

    private AiIndexViews() {
    }

    /**
     * 重建结果。
     *
     * @param scope    all（全量）/ resume（单份）
     * @param resumes  实际写入的简历数
     * @param skipped  跳过的简历数（无内容 / 内容为空）
     * @param chunks   写入的分块数
     * @param embedded 本次新调用上游产生的分块数
     * @param reused   复用旧向量（内容未变）的分块数
     */
    public record Rebuild(String scope, int resumes, int skipped, int chunks,
                          int embedded, int reused, long elapsedMs) {
    }

    /** 单份简历索引状态；indexed=false 时前端展示「索引未就绪（42221）」。 */
    public record IndexStatus(String resumeId, boolean indexed, long chunkCount,
                              String model, int dim, Long updatedAt) {
    }
}
