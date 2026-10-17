package com.pinliao.backend.auth.view;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.pinliao.backend.auth.entity.CompanyEntity;
import com.pinliao.backend.auth.entity.CompanyMemberEntity;
import com.pinliao.backend.auth.entity.HrProfileEntity;
import com.pinliao.backend.auth.entity.UserEntity;
import com.pinliao.backend.auth.entity.UserProfileEntity;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 对外视图（对齐 src/types/api.ts 与 mock 数据形状；null 字段不序列化）。
 *
 * <p>时间输出 ISO-8601 UTC（如 {@code 2026-09-21T10:00:00Z}），与 mock 的 {@code Date.toISOString()} 一致。</p>
 */
public final class AuthViews {

    private AuthViews() {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record User(String id, String phone, String email, String role, String status,
                       Instant lastLoginAt, Instant createdAt, Instant updatedAt) {

        public static User from(UserEntity u) {
            return new User(u.getId(), u.getPhone(), u.getEmail(), u.getRole(), u.getStatus(),
                    toInstant(u.getLastLoginAt()), toInstant(u.getCreatedAt()), toInstant(u.getUpdatedAt()));
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record UserProfile(String id, String userId, String name, String avatarUrl, String gender,
                              LocalDate birthDate, Integer workYears, String edu,
                              String expectCat, String expectSub, String expectCity,
                              Integer expectSalaryMin, Integer expectSalaryMax,
                              String seekingStatus, String defaultResumeId) {

        public static UserProfile from(UserProfileEntity p) {
            return new UserProfile(p.getId(), p.getUserId(), p.getName(), p.getAvatarUrl(), p.getGender(),
                    p.getBirthDate(), p.getWorkYears(), p.getEdu(),
                    p.getExpectCat(), p.getExpectSub(), p.getExpectCity(),
                    p.getExpectSalaryMin(), p.getExpectSalaryMax(),
                    p.getSeekingStatus(), p.getDefaultResumeId());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record HrProfile(String id, String userId, String companyId, String name, String title,
                            String avatarUrl, String intro) {

        public static HrProfile from(HrProfileEntity p) {
            return new HrProfile(p.getId(), p.getUserId(), p.getCompanyId(), p.getName(), p.getTitle(),
                    p.getAvatarUrl(), p.getIntro());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Company(String id, String name, String logoUrl, String intro, String industry,
                          String stage, String size, String website, Boolean verified, String status,
                          String customDomain, String themeColor) {

        public static Company from(CompanyEntity c) {
            return new Company(c.getId(), c.getName(), c.getLogoUrl(), c.getIntro(), c.getIndustry(),
                    c.getStage(), c.getSize(), c.getWebsite(), c.getVerified(), c.getStatus(),
                    c.getCustomDomain(), c.getThemeColor());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CompanyMember(String id, String userId, String companyId, String role, Instant joinedAt,
                                User user, HrProfile hrProfile) {

        public static CompanyMember from(CompanyMemberEntity m, User user, HrProfile hrProfile) {
            return new CompanyMember(m.getId(), m.getUserId(), m.getCompanyId(), m.getRole(),
                    toInstant(m.getJoinedAt()), user, hrProfile);
        }
    }

    private static Instant toInstant(LocalDateTime time) {
        return time == null ? null : time.toInstant(ZoneOffset.UTC);
    }
}
