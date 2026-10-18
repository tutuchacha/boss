package com.pinliao.backend.message.realtime;

import java.security.Principal;

/** WebSocket 登录态主体（Principal.getName() = userId，供 /user/queue 路由）。 */
public record WsPrincipal(String userId, String role, String companyId) implements Principal {

    @Override
    public String getName() {
        return userId;
    }
}
