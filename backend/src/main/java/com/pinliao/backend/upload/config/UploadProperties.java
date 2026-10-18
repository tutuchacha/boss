package com.pinliao.backend.upload.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 文件上传配置（application.yml 的 pinliao.upload.*）。
 *
 * @param dir    本地对象目录（演示替代 OSS；默认 target/uploads，已随 target/ 忽略）
 * @param urlTtl 预签名上传 URL 有效期（默认 10 分钟）
 */
@ConfigurationProperties(prefix = "pinliao.upload")
public record UploadProperties(String dir, Duration urlTtl) {
}
