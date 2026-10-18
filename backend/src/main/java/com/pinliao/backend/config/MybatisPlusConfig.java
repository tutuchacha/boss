package com.pinliao.backend.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 插件装配（T1）：
 * <ul>
 *   <li>租户拦截器：注入策略见 {@link PinliaoTenantLineHandler}（HR 注入 company_id，求职者不注入）；</li>
 *   <li>分页插件：MySQL 方言；单页上限 200 条（契约 §4 分页约定）。</li>
 * </ul>
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor(PinliaoTenantLineHandler tenantLineHandler) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 租户拦截器须排在分页之前（官方建议：多租户 → 分页）
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(tenantLineHandler));
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(200L);
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }

    @Bean
    public PinliaoTenantLineHandler pinliaoTenantLineHandler() {
        return new PinliaoTenantLineHandler();
    }

    @Bean
    public PinliaoMetaObjectHandler pinliaoMetaObjectHandler() {
        return new PinliaoMetaObjectHandler();
    }
}
