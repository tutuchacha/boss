package com.pinliao.backend.message.dto;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 消息模块请求体（契约 §8）。
 *
 * <p>字段校验在 service 层完成，以便按契约返回 42201 / 40410 / 40912 等业务码。</p>
 */
public final class MessageRequests {

    private MessageRequests() {
    }

    /** §8.3 发起会话；firstMessage 可省略。 */
    public record Start(String jobId, String firstMessage) {
    }

    /**
     * §8.4 发送消息。
     *
     * <p>{@code content} 用 JsonNode 接收，兼容契约的 string | object 两种形状；
     * TEXT 只接受字符串，其余类型可为字符串或 JSON 对象。</p>
     */
    public record Send(String type, JsonNode content, String idempotencyKey) {
    }

    /** §8.6 正在输入。 */
    public record Typing(Boolean isTyping) {
    }

    /** §8.7 发送简历（resumeId 可省略：回退默认简历）。 */
    public record SendResume(String resumeId) {
    }
}
