package com.pinliao.backend.message.realtime;

import com.pinliao.backend.common.api.ApiResponse;
import com.pinliao.backend.message.view.MessageViews;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * STOMP 实时推送实现（契约 §10.2~§10.4）。
 *
 * <p>新消息走用户队列；typing 同时走用户队列（§8.6）与会话主题（§10.2）。
 * 帧体统一为 {@code {code,message,data,traceId}} 包络，与 REST 一致。</p>
 */
@Component
public class StompMessageRealtime implements MessageRealtime {

    private final SimpMessagingTemplate messagingTemplate;

    public StompMessageRealtime(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void pushMessage(String userId, Object message) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        messagingTemplate.convertAndSendToUser(userId, "/queue/messages", ApiResponse.ok(message));
    }

    @Override
    public void pushTyping(Collection<String> userIds, MessageViews.Typing event) {
        ApiResponse<MessageViews.Typing> envelope = ApiResponse.ok(event);
        Set<String> targets = new LinkedHashSet<>(userIds == null ? Set.of() : userIds);
        for (String userId : targets) {
            if (userId != null && !userId.isBlank()) {
                messagingTemplate.convertAndSendToUser(userId, "/queue/typing", envelope);
            }
        }
        messagingTemplate.convertAndSend(
                "/topic/conversation/" + event.conversationId() + "/typing", envelope);
    }
}
