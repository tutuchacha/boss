package com.pinliao.backend.message.realtime;

import com.pinliao.backend.message.view.MessageViews;

import java.util.Collection;

/**
 * 聊天实时推送抽象（STOMP 实现见 {@link StompMessageRealtime}）。
 *
 * <p>service 只依赖此接口，便于单元测试不启动 WebSocket 容器；载荷用 Object 兼容
 * {@code ApplicationViews.Message} 与 {@code MessageViews.Message} 两种视图。</p>
 */
public interface MessageRealtime {

    /** 推送到 {@code /user/queue/messages}（统一包络，data 为消息视图）。 */
    void pushMessage(String userId, Object message);

    /** 推送到 {@code /user/queue/typing} 与 {@code /topic/conversation/{id}/typing}。 */
    void pushTyping(Collection<String> userIds, MessageViews.Typing event);
}
