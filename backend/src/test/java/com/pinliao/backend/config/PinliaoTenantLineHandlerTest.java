package com.pinliao.backend.config;

import com.pinliao.backend.auth.context.UserContext;
import com.pinliao.backend.common.exception.BusinessException;
import net.sf.jsqlparser.expression.StringValue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** T1 租户注入策略：HR 注入 / 求职者与未登录不注入 / 全局表永不注入。 */
class PinliaoTenantLineHandlerTest {

    private final PinliaoTenantLineHandler handler = new PinliaoTenantLineHandler();

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    private void loginHr(String companyId) {
        UserContext.set(new UserContext.CurrentUser("u-hr-1", "HR", companyId, "jti-1", Instant.now()));
    }

    private void loginSeeker() {
        UserContext.set(new UserContext.CurrentUser("u-seeker-1", "JOB_SEEKER", null, "jti-2", Instant.now()));
    }

    @Test
    void 未登录时_所有表均不注入() {
        assertTrue(handler.ignoreTable("jobs"));
        assertTrue(handler.ignoreTable("applications"));
        assertTrue(handler.ignoreTable("users"));
    }

    @Test
    void 求职者_跨租户视角_不注入() {
        loginSeeker();
        assertTrue(handler.ignoreTable("jobs"));
        assertTrue(handler.ignoreTable("applications"));
        assertTrue(handler.ignoreTable("conversations"));
    }

    @Test
    void HR_租户表注入_全局表忽略() {
        loginHr("co-acgiw7");
        assertFalse(handler.ignoreTable("jobs"));
        assertFalse(handler.ignoreTable("conversations"));
        assertFalse(handler.ignoreTable("messages"));
        assertFalse(handler.ignoreTable("applications"));
        assertFalse(handler.ignoreTable("interviews"));
        // 全局表 / 共享表：永不注入（求职者侧跨租户数据）
        assertTrue(handler.ignoreTable("users"));
        assertTrue(handler.ignoreTable("companies"));
        assertTrue(handler.ignoreTable("resumes"));
        assertTrue(handler.ignoreTable("favorites"));
        assertTrue(handler.ignoreTable("categories"));
    }

    @Test
    void 表名大小写不敏感() {
        loginHr("co-acgiw7");
        assertFalse(handler.ignoreTable("JOBS"));
    }

    @Test
    void HR_租户id取companyId_列名为company_id() {
        loginHr("co-acgiw7");
        assertEquals("company_id", handler.getTenantIdColumn());
        StringValue value = (StringValue) handler.getTenantId();
        assertEquals("co-acgiw7", value.getValue());
    }

    @Test
    void 缺少企业上下文访问租户表_抛403兜底() {
        UserContext.set(new UserContext.CurrentUser("u-x", "HR", null, "jti-3", Instant.now()));
        BusinessException ex = assertThrows(BusinessException.class, handler::getTenantId);
        assertEquals(40301, ex.getCode());
    }
}
