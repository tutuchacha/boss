package com.pinliao.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** springdoc-openapi 基础信息（访问 /swagger-ui.html）。 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI pinliaoOpenApi() {
        return new OpenAPI().info(new Info()
                .title("聘聊 API")
                .version("v0.3")
                .description("契约：docs/api-contract.md；接口清单：docs/api-endpoints.md"));
    }
}
