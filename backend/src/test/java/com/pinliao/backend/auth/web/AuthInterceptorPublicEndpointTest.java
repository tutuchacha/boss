package com.pinliao.backend.auth.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** T3a：公开接口判定（方法 + 路径），保证「匿名放行读接口、写接口仍需鉴权」。 */
class AuthInterceptorPublicEndpointTest {

    @Test
    void 公开读接口匿名放行() {
        assertTrue(AuthInterceptor.isPublicEndpoint("GET", "/api/v1/jobs"));
        assertTrue(AuthInterceptor.isPublicEndpoint("GET", "/api/v1/jobs/job-1"));
        assertTrue(AuthInterceptor.isPublicEndpoint("GET", "/api/v1/jobs/job-1/similar"));
        assertTrue(AuthInterceptor.isPublicEndpoint("GET", "/api/v1/search/jobs"));
        assertTrue(AuthInterceptor.isPublicEndpoint("GET", "/api/v1/companies/co-1"));
        assertTrue(AuthInterceptor.isPublicEndpoint("GET", "/api/v1/companies/co-1/jobs"));
        assertTrue(AuthInterceptor.isPublicEndpoint("GET", "/api/v1/categories"));
        assertTrue(AuthInterceptor.isPublicEndpoint("GET", "/api/v1/cities"));
        assertTrue(AuthInterceptor.isPublicEndpoint("GET", "/api/v1/dict"));
        assertTrue(AuthInterceptor.isPublicEndpoint("POST", "/api/v1/jobs/job-1/view"));
    }

    @Test
    void 写接口与其它路径仍需鉴权() {
        assertFalse(AuthInterceptor.isPublicEndpoint("POST", "/api/v1/jobs"));
        assertFalse(AuthInterceptor.isPublicEndpoint("PATCH", "/api/v1/jobs/job-1"));
        assertFalse(AuthInterceptor.isPublicEndpoint("DELETE", "/api/v1/jobs/job-1"));
        assertFalse(AuthInterceptor.isPublicEndpoint("POST", "/api/v1/jobs/job-1/close"));
        assertFalse(AuthInterceptor.isPublicEndpoint("POST", "/api/v1/jobs/job-1/reopen"));
        assertFalse(AuthInterceptor.isPublicEndpoint("GET", "/api/v1/hr/jobs"));
        assertFalse(AuthInterceptor.isPublicEndpoint("GET", "/api/v1/company/me"));
        assertFalse(AuthInterceptor.isPublicEndpoint("PATCH", "/api/v1/company/me"));
        assertFalse(AuthInterceptor.isPublicEndpoint("GET", "/api/v1/company/members"));
        assertFalse(AuthInterceptor.isPublicEndpoint("POST", "/api/v1/company/me/verify"));
        assertFalse(AuthInterceptor.isPublicEndpoint("GET", "/api/v1/jobs/job-1/apply"));
        assertFalse(AuthInterceptor.isPublicEndpoint(null, "/api/v1/jobs"));
        assertFalse(AuthInterceptor.isPublicEndpoint("GET", null));
    }
}
