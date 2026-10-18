package com.pinliao.backend.message.realtime;

import com.pinliao.backend.auth.jwt.JwtService;
import com.pinliao.backend.auth.token.AuthTokenStore;
import com.pinliao.backend.common.exception.BusinessException;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * WebSocket 握手鉴权（契约 §10.1：Authorization header 或 query 携带 accessToken）。
 *
 * <p>浏览器原生 WebSocket 不能自定义 header，因此 query（{@code ?token=...}）是主路径；
 * 非浏览器客户端也可用 {@code Authorization: Bearer ...}。无 token 时先放行握手，
 * 由 {@link WsAuthChannelInterceptor} 在 STOMP CONNECT 阶段拒绝（方便返回 STOMP ERROR）。</p>
 */
@Component
public class WsHandshakeInterceptor implements HandshakeInterceptor {

    public static final String ATTR_USER_ID = "pinliao.ws.userId";
    public static final String ATTR_ROLE = "pinliao.ws.role";
    public static final String ATTR_COMPANY_ID = "pinliao.ws.companyId";
    public static final String ATTR_JTI = "pinliao.ws.jti";

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final AuthTokenStore tokenStore;

    public WsHandshakeInterceptor(JwtService jwtService, AuthTokenStore tokenStore) {
        this.jwtService = jwtService;
        this.tokenStore = tokenStore;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = tokenFromQuery(request);
        if (token == null) {
            token = tokenFromHeader(request);
        }
        if (token == null) {
            return true;
        }
        try {
            put(attributes, token);
            return true;
        } catch (BusinessException e) {
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }

    /** 把 token 解析结果写入握手属性；供 CONNECT 阶段的 ChannelInterceptor 复用。 */
    void put(Map<String, Object> attributes, String token) {
        JwtService.ParsedAccessToken parsed = jwtService.parse(token);
        if (tokenStore.isAccessRevoked(parsed.jti())) {
            throw new BusinessException(com.pinliao.backend.common.api.ApiCodes.TOKEN_EXPIRED,
                    "登录已失效，请重新登录");
        }
        attributes.put(ATTR_USER_ID, parsed.userId());
        attributes.put(ATTR_ROLE, parsed.role());
        if (parsed.companyId() != null) {
            attributes.put(ATTR_COMPANY_ID, parsed.companyId());
        }
        attributes.put(ATTR_JTI, parsed.jti());
    }

    static String tokenFromQuery(ServerHttpRequest request) {
        String token = UriComponentsBuilder.fromUri(request.getURI()).build()
                .getQueryParams().getFirst("token");
        if (token == null || token.isBlank()) {
            token = UriComponentsBuilder.fromUri(request.getURI()).build()
                    .getQueryParams().getFirst("access_token");
        }
        return token == null || token.isBlank() ? null : token.trim();
    }

    static String tokenFromHeader(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst("Authorization");
        return header != null && header.startsWith(BEARER_PREFIX)
                ? header.substring(BEARER_PREFIX.length()).trim()
                : null;
    }
}
