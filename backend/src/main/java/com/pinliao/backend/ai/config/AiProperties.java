package com.pinliao.backend.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.Locale;

/**
 * AI 总开关与索引 / 问答参数（前缀 {@code pinliao.ai}）。
 *
 * <p>provider 侧参数（地址 / 密钥 / 模型 / 向量维度）统一走 {@code spring.ai.openai.*}，
 * 由环境变量注入，详见 {@code application.yml} 与 {@code backend/README.md}「AI 配置」。</p>
 *
 * @param enabled            总开关：false 时 AI 接口返回 42220 且不做任何上游调用（默认 false）
 * @param provider           {@code springai}（真实上游）或 {@code fake}（本地确定性假模型，见 T-AI1/T-AI2）
 * @param chunkMaxChars      单个分块正文上限（含标题），超长按句切分并续写
 * @param embeddingBatchSize 单次嵌入请求的文本条数上限（百炼 text-embedding-v4 限制 ≤10）
 * @param qaTopK             单简历问答检索返回的分块数上限（默认 6）
 * @param qaMinScore         最低余弦相似度；低于该值直接回答「简历中没有相关信息」，不调用对话模型
 * @param qaMaxTokens        单次问答生成 token 上限（默认 700，防止一次回答产生不可控费用）
 * @param qaDailyLimit       每 HR 每日问答次数上限（Redis 计数；≤0 表示不限制）
 * @param searchTopK         语义人才检索返回的候选人上限（默认 8）
 * @param searchMinScore     语义人才检索的最低余弦相似度（默认 0.15；低于该值的候选人不出现在结果里）
 */
@ConfigurationProperties(prefix = "pinliao.ai")
public record AiProperties(boolean enabled,
                           @DefaultValue("springai") String provider,
                           @DefaultValue("800") int chunkMaxChars,
                           @DefaultValue("10") int embeddingBatchSize,
                           @DefaultValue("6") int qaTopK,
                           @DefaultValue("0.2") double qaMinScore,
                           @DefaultValue("700") int qaMaxTokens,
                           @DefaultValue("100") int qaDailyLimit,
                           @DefaultValue("8") int searchTopK,
                           @DefaultValue("0.15") double searchMinScore) {

    /** 真实上游：Spring AI OpenAI 兼容协议。 */
    public static final String PROVIDER_SPRING_AI = "springai";
    /** 假模型：确定性、零网络、零费用；只用于单测与冒烟，回答质量不具参考价值。 */
    public static final String PROVIDER_FAKE = "fake";

    public AiProperties {
        provider = provider == null || provider.isBlank()
                ? PROVIDER_SPRING_AI
                : provider.trim().toLowerCase(Locale.ROOT);
        if (chunkMaxChars <= 0) {
            chunkMaxChars = 800;
        }
        if (embeddingBatchSize <= 0) {
            embeddingBatchSize = 10;
        }
        if (qaTopK <= 0) {
            qaTopK = 6;
        }
        if (qaMinScore < 0 || qaMinScore > 1) {
            qaMinScore = 0.2;
        }
        if (qaMaxTokens <= 0) {
            qaMaxTokens = 700;
        }
        if (searchTopK <= 0) {
            searchTopK = 8;
        }
        if (searchMinScore < 0 || searchMinScore > 1) {
            searchMinScore = 0.15;
        }
    }

    /** 是否使用本地假模型（冒烟脚本 / 无密钥环境）。 */
    public boolean fake() {
        return PROVIDER_FAKE.equals(provider);
    }
}
