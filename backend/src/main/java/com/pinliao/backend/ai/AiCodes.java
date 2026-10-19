package com.pinliao.backend.ai;

/**
 * AI 模块专属错误码（契约 §5：5 位数字，前 3 位对应 HTTP 状态码）。
 *
 * <p>Step 4 新增。选号避开既有段：42220 / 42221 / 42902 / 50020 均未被占用
 * （既有 42210 为「业务规则不允许」通用槽位，AI 用独立码便于前端区分「没开 AI」与「业务规则」）。</p>
 */
public final class AiCodes {

    /** 42220 AI 未启用（{@code pinliao.ai.enabled=false}，未发起任何上游调用） */
    public static final int AI_DISABLED = 42220;

    /** 42221 索引未就绪（T-AI1 起使用：简历尚未建立向量索引） */
    public static final int INDEX_NOT_READY = 42221;

    /** 42902 AI 调用超出配额（T-AI2 起使用：按 HR / 按日限流） */
    public static final int QUOTA_EXCEEDED = 42902;

    /** 50020 AI 上游调用失败或超时（不暴露上游原始报文，只回显简要原因） */
    public static final int UPSTREAM_FAILED = 50020;

    private AiCodes() {
    }
}
