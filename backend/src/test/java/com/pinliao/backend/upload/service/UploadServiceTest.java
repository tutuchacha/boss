package com.pinliao.backend.upload.service;

import com.pinliao.backend.auth.config.AuthProperties;
import com.pinliao.backend.auth.context.UserContext;
import com.pinliao.backend.common.exception.BusinessException;
import com.pinliao.backend.upload.config.UploadProperties;
import com.pinliao.backend.upload.dto.UploadRequests;
import com.pinliao.backend.upload.view.UploadViews;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/** T7：本地预签名上传（§12.1~§12.2）单元测试。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UploadServiceTest {

    @TempDir
    Path tempDir;

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOps;

    private UploadService service;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString())).thenReturn(1L);
        AuthProperties auth = new AuthProperties(
                "0123456789abcdef0123456789abcdef",
                Duration.ofHours(2),
                Duration.ofDays(30),
                new AuthProperties.Sms("123456", Duration.ofMinutes(5), Duration.ofSeconds(60)));
        service = new UploadService(new UploadProperties(tempDir.toString(), Duration.ofMinutes(10)), auth, redis);
        UserContext.set(new UserContext.CurrentUser("u-seeker-1", "JOB_SEEKER", null,
                "jti", Instant.now().plus(Duration.ofHours(2))));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void signReturnsSignedLocalUrlAndObjectKey() {
        UploadViews.Sign sign = service.sign(new UploadRequests.SignRequest("avatar", "image/png", "png"));
        assertEquals("upload-avatar-u-seeker-1-1.png", sign.objectKey());
        assertTrue(sign.uploadUrl().startsWith("/api/v1/uploads/local/" + sign.objectKey() + "?"));
        assertTrue(sign.uploadUrl().contains("expires="));
        assertTrue(sign.uploadUrl().contains("sig="));
        assertTrue(sign.expiresAt().isAfter(Instant.now()));
    }

    @Test
    void signRejectsTypeAndMimeMismatch() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.sign(new UploadRequests.SignRequest("avatar", "application/pdf", "pdf")));
        assertEquals(42201, e.getCode());
    }

    @Test
    void signRejectsExtAndMimeMismatch() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.sign(new UploadRequests.SignRequest("avatar", "image/png", "jpg")));
        assertEquals(42201, e.getCode());
    }

    @Test
    void storeAndConfirmRoundTrip() {
        UploadViews.Sign sign = service.sign(new UploadRequests.SignRequest("avatar", "image/png", "png"));
        UploadTarget target = parse(sign.uploadUrl());
        byte[] bytes = new byte[]{1, 2, 3, 4};
        service.store(target.key(), target.expires(), target.signature(), "image/png",
                new ByteArrayInputStream(bytes));

        UploadViews.Confirm confirm = service.confirm(new UploadRequests.ConfirmRequest(sign.objectKey()));
        assertEquals("/uploads/" + sign.objectKey(), confirm.url());
        assertEquals("image/png", confirm.contentType());
        assertEquals(4, confirm.size());
    }

    @Test
    void storeRejectsBadSignature() {
        UploadViews.Sign sign = service.sign(new UploadRequests.SignRequest("avatar", "image/png", "png"));
        UploadTarget target = parse(sign.uploadUrl());
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.store(target.key(), target.expires(), "bad-signature", "image/png",
                        new ByteArrayInputStream(new byte[]{1})));
        assertEquals(40301, e.getCode());
    }

    @Test
    void storeRejectsContentTypeMismatch() {
        UploadViews.Sign sign = service.sign(new UploadRequests.SignRequest("avatar", "image/png", "png"));
        UploadTarget target = parse(sign.uploadUrl());
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.store(target.key(), target.expires(), target.signature(), "image/jpeg",
                        new ByteArrayInputStream(new byte[]{1})));
        assertEquals(42201, e.getCode());
    }

    @Test
    void storeRejectsOversizedFile() {
        UploadViews.Sign sign = service.sign(new UploadRequests.SignRequest("avatar", "image/png", "png"));
        UploadTarget target = parse(sign.uploadUrl());
        byte[] tooBig = new byte[2 * 1024 * 1024 + 1];
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.store(target.key(), target.expires(), target.signature(), "image/png",
                        new ByteArrayInputStream(tooBig)));
        assertEquals(42201, e.getCode());
    }

    @Test
    void confirmRejectsAnotherUsersObjectKey() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.confirm(new UploadRequests.ConfirmRequest("upload-avatar-u-other-1.png")));
        assertEquals(40410, e.getCode());
    }

    @Test
    void confirmRejectsMissingObject() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.confirm(new UploadRequests.ConfirmRequest("upload-avatar-u-seeker-1-99.png")));
        assertEquals(40410, e.getCode());
    }

    // ------------------------------------------------------------------

    private static UploadTarget parse(String uploadUrl) {
        int queryStart = uploadUrl.indexOf('?');
        String path = uploadUrl.substring(0, queryStart);
        String key = path.substring(path.lastIndexOf('/') + 1);
        String query = uploadUrl.substring(queryStart + 1);
        long expires = 0;
        String sig = null;
        for (String part : query.split("&")) {
            int eq = part.indexOf('=');
            String name = part.substring(0, eq);
            String value = part.substring(eq + 1);
            if ("expires".equals(name)) {
                expires = Long.parseLong(value);
            } else if ("sig".equals(name)) {
                sig = value;
            }
        }
        return new UploadTarget(key, expires, sig);
    }

    private record UploadTarget(String key, long expires, String signature) {
    }
}
