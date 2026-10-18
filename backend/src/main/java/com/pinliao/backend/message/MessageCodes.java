package com.pinliao.backend.message;

/**
 * 消息模块专属业务码（契约 §8）。
 *
 * <p>与前端 mock 对齐：{@code src/api/conversations.ts} / {@code hrConversations.ts}
 * 使用 40912（会话已存在）与 40410（会话不存在 / 非参与方）。</p>
 */
public final class MessageCodes {

    /** 40410 会话不存在 / 当前用户不是参与方（含跨公司，不泄露存在性） */
    public static final int CONVERSATION_NOT_FOUND = 40410;

    /** 40912 会话已存在（同一 jobId + userId 唯一；前端应跳转已有会话） */
    public static final int CONVERSATION_EXISTS = 40912;

    /** 42201 消息类型 / 内容非法 */
    public static final int INVALID_MESSAGE = 42201;

    private MessageCodes() {
    }
}
