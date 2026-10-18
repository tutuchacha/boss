package com.pinliao.backend.upload.service;

import com.pinliao.backend.auth.config.AuthProperties;
import com.pinliao.backend.auth.context.UserContext;
import com.pinliao.backend.common.api.ApiCodes;
import com.pinliao.backend.common.exception.BusinessException;
import com.pinliao.backend.upload.config.UploadProperties;
import com.pinliao.backend.upload.dto.UploadRequests;
import com.pinliao.backend.upload.view.UploadViews;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文件上传服务（契约 §9 / §12.1~§12.2）。
 *
 * <p><b>演示实现</b>：用本地目录替代 MinIO/S3。§12.1 返回带 HMAC 签名与有效期的
 * {@code PUT /api/v1/uploads/local/{objectKey}}；客户端直传后调 §12.2 确认。
 * 确认结果 URL 由 {@code /uploads/**} 静态映射提供，不经过登录态拦截器。</p>
 *
 * <p>约束（契约 §9.2）：头像/Logo ≤2MB、公司图片 ≤5MB、简历附件 ≤10MB；
 * 仅允许 jpeg/png/webp（图片）与 pdf/docx（简历）。objectKey 由服务端生成，
 * 含 userId 与递增序号，确认时校验归属，避免越权确认他人文件。</p>
 */
@Service
public class UploadService {

    public static final String LOCAL_UPLOAD_PREFIX = "/api/v1/uploads/local/";
    private static final String PUBLIC_URL_PREFIX = "/uploads/";
    private static final String SEQ_KEY = "pinliao:seq:upload:%s";
    private static final Pattern OBJECT_KEY = Pattern.compile(
            "^upload-(avatar|logo|resume|company)-([A-Za-z0-9_-]+)-(\\d+)\\.(jpg|jpeg|png|webp|pdf|docx)$");
    private static final Pattern SAFE_USER = Pattern.compile("[^A-Za-z0-9_-]");

    private static final Map<String, Set<String>> TYPE_MIME = Map.of(
            "avatar", Set.of("image/jpeg", "image/png", "image/webp"),
            "logo", Set.of("image/jpeg", "image/png", "image/webp"),
            "company", Set.of("image/jpeg", "image/png", "image/webp"),
            "resume", Set.of("application/pdf",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
    private static final Map<String, String> EXT_MIME = Map.of(
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "webp", "image/webp",
            "pdf", "application/pdf",
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    private static final Map<String, Long> MAX_BYTES = Map.of(
            "avatar", 2L * 1024 * 1024,
            "logo", 2L * 1024 * 1024,
            "company", 5L * 1024 * 1024,
            "resume", 10L * 1024 * 1024);

    private final Path root;
    private final Duration urlTtl;
    private final String secret;
    private final StringRedisTemplate redis;

    public UploadService(UploadProperties properties, AuthProperties authProperties, StringRedisTemplate redis) {
        this.root = Paths.get(properties.dir()).toAbsolutePath().normalize();
        this.urlTtl = properties.urlTtl() == null ? Duration.ofMinutes(10) : properties.urlTtl();
        this.secret = authProperties.secret();
        this.redis = redis;
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建上传目录: " + root, e);
        }
    }

    /** §12.1 生成预签名上传 URL（需登录；objectKey 由服务端分配）。 */
    public UploadViews.Sign sign(UploadRequests.SignRequest req) {
        UserContext.CurrentUser user = UserContext.require();
        String type = req.type().trim().toLowerCase(Locale.ROOT);
        String contentType = normalizeContentType(req.contentType());
        String ext = req.ext().trim().toLowerCase(Locale.ROOT);
        validateTypeAndMime(type, contentType, ext);

        String userId = safeUserId(user.userId());
        Long sequence = redis.opsForValue().increment(SEQ_KEY.formatted(user.userId()));
        long seq = sequence == null ? 1L : sequence;
        String objectKey = "upload-" + type + "-" + userId + "-" + seq + "." + ext;

        long expires = Instant.now().plus(urlTtl).getEpochSecond();
        String signature = signature(objectKey, expires);
        String uploadUrl = LOCAL_UPLOAD_PREFIX + objectKey + "?expires=" + expires + "&sig=" + signature;
        return new UploadViews.Sign(uploadUrl, objectKey, Instant.ofEpochSecond(expires));
    }

    /**
     * 处理预签名 PUT 直传（匿名，签名即凭证）。
     *
     * @param objectKey   服务端生成的 objectKey
     * @param expires     过期时间（epoch 秒）
     * @param signature   HMAC 签名
     * @param contentType 请求 Content-Type
     * @param input       原始文件流
     */
    public void store(String objectKey, long expires, String signature, String contentType, InputStream input) {
        KeyInfo info = parseKey(objectKey);
        if (expires < Instant.now().getEpochSecond()) {
            throw new BusinessException(ApiCodes.FORBIDDEN, "上传链接已过期");
        }
        String expected = signature(objectKey, expires);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(ApiCodes.FORBIDDEN, "上传链接无效");
        }
        String actualMime = normalizeContentType(contentType);
        if (!EXT_MIME.get(info.ext()).equals(actualMime)) {
            throw invalid("Content-Type 与文件扩展名不匹配");
        }

        long max = MAX_BYTES.get(info.type());
        Path target = resolve(objectKey);
        Path temp = null;
        try {
            Files.createDirectories(root);
            temp = Files.createTempFile(root, ".upload-", ".tmp");
            long total = 0;
            try (OutputStream out = Files.newOutputStream(temp)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    total += read;
                    if (total > max) {
                        throw invalid("文件超过大小限制");
                    }
                    out.write(buffer, 0, read);
                }
            }
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            temp = null;
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            throw new BusinessException(ApiCodes.INTERNAL_ERROR, "文件保存失败", e);
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    // 临时文件清理失败不覆盖原始错误
                }
            }
        }
    }

    /** §12.2 确认上传完成（需登录；仅可确认本人 objectKey）。 */
    public UploadViews.Confirm confirm(UploadRequests.ConfirmRequest req) {
        UserContext.CurrentUser user = UserContext.require();
        String objectKey = req.objectKey().trim();
        KeyInfo info = parseKey(objectKey);
        if (!safeUserId(user.userId()).equals(info.userId)) {
            throw notFound();
        }
        Path file = resolve(objectKey);
        if (!Files.isRegularFile(file)) {
            throw notFound();
        }
        try {
            long size = Files.size(file);
            if (size > MAX_BYTES.get(info.type())) {
                throw invalid("文件超过大小限制");
            }
            return new UploadViews.Confirm(PUBLIC_URL_PREFIX + objectKey, EXT_MIME.get(info.ext()), size);
        } catch (IOException e) {
            throw new BusinessException(ApiCodes.INTERNAL_ERROR, "文件读取失败", e);
        }
    }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    private void validateTypeAndMime(String type, String contentType, String ext) {
        Set<String> allowedMimes = TYPE_MIME.get(type);
        if (allowedMimes == null) {
            throw invalid("不支持的上传类型");
        }
        String extMime = EXT_MIME.get(ext);
        if (extMime == null || !allowedMimes.contains(contentType) || !contentType.equals(extMime)) {
            throw invalid("文件类型与扩展名不匹配");
        }
    }

    private KeyInfo parseKey(String objectKey) {
        Matcher matcher = OBJECT_KEY.matcher(objectKey == null ? "" : objectKey);
        if (!matcher.matches()) {
            throw invalid("objectKey 不合法");
        }
        return new KeyInfo(matcher.group(1), matcher.group(2), matcher.group(3), matcher.group(4));
    }

    private Path resolve(String objectKey) {
        Path target = root.resolve(objectKey).normalize();
        if (!target.startsWith(root)) {
            throw invalid("objectKey 不合法");
        }
        return target;
    }

    private String signature(String objectKey, long expires) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal((objectKey + "|" + expires).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException("上传签名失败", e);
        }
    }

    private static String normalizeContentType(String contentType) {
        if (contentType == null) {
            return "";
        }
        String value = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        return "image/jpg".equals(value) ? "image/jpeg" : value;
    }

    private static String safeUserId(String userId) {
        return SAFE_USER.matcher(userId).replaceAll("_");
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ApiCodes.FIELD_VALIDATION, message);
    }

    private static BusinessException notFound() {
        return new BusinessException(40410, "上传对象不存在或无权确认");
    }

    private record KeyInfo(String type, String userId, String sequence, String ext) {
    }
}
