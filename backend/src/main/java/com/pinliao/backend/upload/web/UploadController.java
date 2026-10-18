package com.pinliao.backend.upload.web;

import com.pinliao.backend.common.api.ApiResponse;
import com.pinliao.backend.upload.dto.UploadRequests;
import com.pinliao.backend.upload.service.UploadService;
import com.pinliao.backend.upload.view.UploadViews;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * 文件上传接口（契约 §12.1~§12.2；本地版预签名直传）。
 *
 * <p>{@code PUT /local/{objectKey}} 是 {@code uploadUrl} 指向的匿名直传端点，
 * 由 HMAC 签名与 expires 保护；不读取登录态，因此由 WebAuthConfig 排除。
 * 上传成功后文件通过 {@code /uploads/**} 静态映射访问。</p>
 */
@RestController
@RequestMapping("/api/v1/uploads")
@Tag(name = "12. 文件上传", description = "预签名 URL、本地直传与确认（契约 §12）")
public class UploadController {

    private final UploadService uploadService;

    public UploadController(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    /** §12.1 获取预签名上传 URL（需登录）。 */
    @PostMapping("/sign")
    public UploadViews.Sign sign(@Valid @RequestBody UploadRequests.SignRequest body) {
        return uploadService.sign(body);
    }

    /** §12.2 确认上传完成（需登录；校验对象存在与归属）。 */
    @PostMapping("/confirm")
    public UploadViews.Confirm confirm(@Valid @RequestBody UploadRequests.ConfirmRequest body) {
        return uploadService.confirm(body);
    }

    /** 预签名直传端点（匿名；签名 + 有效期保护）。 */
    @PutMapping(value = "/local/{objectKey}", consumes = MediaType.ALL_VALUE)
    public ApiResponse<Void> upload(@PathVariable String objectKey,
                                    @RequestParam long expires,
                                    @RequestParam String sig,
                                    @RequestHeader(value = HttpHeaders.CONTENT_TYPE, required = false) String contentType,
                                    HttpServletRequest request) throws IOException {
        uploadService.store(objectKey, expires, sig, contentType, request.getInputStream());
        return ApiResponse.ok();
    }
}
