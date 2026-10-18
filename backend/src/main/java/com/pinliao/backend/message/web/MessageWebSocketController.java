package com.pinliao.backend.message.web;

import com.pinliao.backend.auth.context.UserContext;
import com.pinliao.backend.common.api.ApiCodes;
import com.pinliao.backend.common.api.ApiResponse;
import com.pinliao.backend.common.exception.BusinessException;
import com.pinliao.backend.message.dto.MessageRequests;
import com.pinliao.backend.message.realtime.WsPrincipal;
import com.pinliao.backend.message.service.ConversationCommandService;
import com.pinliao.backend.message.view.MessageViews;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * STOMP 发送主题（契约 §10.3）。
 *
 * <p>入站帧直接携带 §8.4 的请求体，复用 REST 同一套校验 / 权限 / 限流 / 自动回复逻辑；
 * 新消息由 service 主动推送到对方 {@code /user/queue/messages}，本控制器不回显，
 * 避免前端收到重复消息。错误统一推 {@code /user/queue/errors}。</p>
 */
@Controller
public class MessageWebSocketController {

    private final ConversationCommandService commandService;

    public MessageWebSocketController(ConversationCommandService commandService) {
        this.commandService = commandService;
    }

    @MessageMapping("/conversation/{conversationId}/send")
    public void send(@DestinationVariable String conversationId, MessageRequests.Send body,
                     Principal principal) {
        commandService.sendAs(actor(principal), conversationId, body);
    }

    @MessageMapping("/conversation/{conversationId}/typing")
    public void typing(@DestinationVariable String conversationId, MessageRequests.Typing body,
                       Principal principal) {
        commandService.typingAs(actor(principal), conversationId, body);
    }

    @MessageMapping("/conversation/{conversationId}/read")
    public MessageViews.ReadResult read(@DestinationVariable String conversationId, Principal principal) {
        return commandService.markReadAs(actor(principal), conversationId);
    }

    @MessageExceptionHandler(BusinessException.class)
    @SendToUser("/queue/errors")
    public ApiResponse<Void> handleBusiness(BusinessException e) {
        return ApiResponse.fail(e.getCode(), e.getMessage());
    }

    @MessageExceptionHandler(Exception.class)
    @SendToUser("/queue/errors")
    public ApiResponse<Void> handleUnexpected(Exception e) {
        return ApiResponse.fail(ApiCodes.INTERNAL_ERROR, "服务器内部错误");
    }

    private static UserContext.CurrentUser actor(Principal principal) {
        if (principal instanceof WsPrincipal ws) {
            return new UserContext.CurrentUser(ws.userId(), ws.role(), ws.companyId(), "ws", null);
        }
        if (principal == null) {
            throw new BusinessException(ApiCodes.UNAUTHORIZED, "请先登录");
        }
        return new UserContext.CurrentUser(principal.getName(), null, null, "ws", null);
    }
}
