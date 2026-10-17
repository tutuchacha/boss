package com.pinliao.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 聘聊后端入口（Step 3）。
 *
 * <p>契约：docs/api-contract.md（包络 / 错误码 / 时间格式）与 docs/api-endpoints.md（接口清单）。
 * 本地开发：先跑 backend/scripts/dev-up.ps1 启动 MySQL(3307) / Redis(6379)，再 spring-boot:run。</p>
 */
@SpringBootApplication
public class PinliaoBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(PinliaoBackendApplication.class, args);
    }
}
