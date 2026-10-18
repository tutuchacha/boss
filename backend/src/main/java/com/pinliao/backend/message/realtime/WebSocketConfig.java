package com.pinliao.backend.message.realtime;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * 聊天 WebSocket 配置（契约 §10）。
 *
 * <p>端点 {@code /ws}（沙箱环境由网关通过 {@code ?XTransformPort=} 转发）；STOMP 订阅前缀
 * {@code /topic}、{@code /queue}，用户队列前缀 {@code /user}，发送前缀 {@code /app}。</p>
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WsHandshakeInterceptor handshakeInterceptor;
    private final WsAuthChannelInterceptor authChannelInterceptor;

    public WebSocketConfig(WsHandshakeInterceptor handshakeInterceptor,
                           WsAuthChannelInterceptor authChannelInterceptor) {
        this.handshakeInterceptor = handshakeInterceptor;
        this.authChannelInterceptor = authChannelInterceptor;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .addInterceptors(handshakeInterceptor);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authChannelInterceptor);
    }
}
