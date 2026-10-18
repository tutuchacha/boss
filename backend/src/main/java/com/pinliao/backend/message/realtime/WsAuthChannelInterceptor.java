package com.pinliao.backend.message.realtime;

import com.pinliao.backend.auth.jwt.JwtService;
import com.pinliao.backend.auth.token.AuthTokenStore;
import com.pinliao.backend.common.exception.BusinessException;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * STOMP CONNECT 鉴权：优先使用握手阶段解析的 token，其次读取 CONNECT 原生 header。
 *
 * <p>这样既支持浏览器 query token，也支持非浏览器客户端在 STOMP header 携带
 * {@code Authorization} / {@code token}；失败抛 MessagingException，连接不会进入业务主题。</p>
 */
@Component
public class WsAuthChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final AuthTokenStore tokenStore;

    public WsAuthChannelInterceptor(JwtService jwtService, AuthTokenStore tokenStore) {
        this.jwtService = jwtService;
        this.tokenStore = tokenStore;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }
        if (accessor.getUser() != null) {
            return message;
        }
        Map<String, Object> attributes = accessor.getSessionAttributes();
        String userId = attributes == null ? null : (String) attributes.get(WsHandshakeInterceptor.ATTR_USER_ID);
        if (userId != null) {
            accessor.setUser(new WsPrincipal(userId,
                    (String) attributes.get(WsHandshakeInterceptor.ATTR_ROLE),
                    (String) attributes.get(WsHandshakeInterceptor.ATTR_COMPANY_ID)));
            return message;
        }

        String token = nativeToken(accessor);
        if (token == null) {
            throw new MessagingException("未登录");
        }
        try {
            JwtService.ParsedAccessToken parsed = jwtService.parse(token);
            if (tokenStore.isAccessRevoked(parsed.jti())) {
                throw new MessagingException("登录已失效，请重新登录");
            }
            accessor.setUser(new WsPrincipal(parsed.userId(), parsed.role(), parsed.companyId()));
        } catch (BusinessException e) {
            throw new MessagingException(e.getMessage(), e);
        }
        return message;
    }

    private static String nativeToken(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length()).trim();
        }
        String token = accessor.getFirstNativeHeader("token");
        return token == null || token.isBlank() ? null : token.trim();
    }
}
