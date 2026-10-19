package com.pinliao.backend.recruiter.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pinliao.backend.auth.context.Roles;
import com.pinliao.backend.auth.context.UserContext;
import com.pinliao.backend.auth.entity.HrProfileEntity;
import com.pinliao.backend.auth.mapper.HrProfileMapper;
import com.pinliao.backend.auth.view.AuthViews;
import com.pinliao.backend.common.exception.BusinessException;
import com.pinliao.backend.recruiter.RecruiterCodes;
import com.pinliao.backend.recruiter.dto.RecruiterRequests;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * §4.1 / §4.2 招聘者画像（HR / COMPANY_ADMIN；归属本人）。
 *
 * <p><b>为什么显式按 user_id 查询</b>：{@code hr_profiles} 带 {@code company_id}，但不在
 * {@code PinliaoTenantLineHandler} 的租户注入表集合内（该表按 user_id 唯一，见 V1 的
 * {@code uk_hr_profiles_user}）；因此不能用 company_id 兜底，必须显式以登录用户的 user_id 定位，
 * 避免跨用户读到他人档案。</p>
 *
 * <p>语义：GET 档案缺失时 40410（不泄露存在性）；PATCH 为部分更新，null = 不修改，
 * 空字符串视为「清空」仅对可选字段生效（name / title 不允许清空，42201 由请求体校验兜底）。</p>
 */
@Service
public class HrProfileService {

    private final HrProfileMapper hrProfileMapper;

    public HrProfileService(HrProfileMapper hrProfileMapper) {
        this.hrProfileMapper = hrProfileMapper;
    }

    /** §4.1 获取招聘者画像（HR / COMPANY_ADMIN）。 */
    public AuthViews.HrProfile profile() {
        UserContext.CurrentUser user = Roles.requireManager();
        return AuthViews.HrProfile.from(requireProfile(user.userId()));
    }

    /**
     * §4.2 更新招聘者画像（部分字段）。
     *
     * @param patch name / title / avatarUrl / intro，null = 不修改
     */
    @Transactional
    public AuthViews.HrProfile update(RecruiterRequests.HrProfilePatch patch) {
        UserContext.CurrentUser user = Roles.requireManager();
        HrProfileEntity profile = requireProfile(user.userId());

        if (patch.name() != null) {
            profile.setName(patch.name().trim());
        }
        if (patch.title() != null) {
            profile.setTitle(patch.title().trim());
        }
        if (patch.avatarUrl() != null) {
            profile.setAvatarUrl(trimToNull(patch.avatarUrl()));
        }
        if (patch.intro() != null) {
            profile.setIntro(trimToNull(patch.intro()));
        }

        // updated_at 由 PinliaoMetaObjectHandler 自动填充
        hrProfileMapper.updateById(profile);
        return AuthViews.HrProfile.from(profile);
    }

    private HrProfileEntity requireProfile(String userId) {
        HrProfileEntity profile = hrProfileMapper.selectOne(Wrappers.<HrProfileEntity>lambdaQuery()
                .eq(HrProfileEntity::getUserId, userId)
                .last("LIMIT 1"));
        if (profile == null) {
            throw new BusinessException(RecruiterCodes.NOT_FOUND, "招聘者档案不存在");
        }
        return profile;
    }

    private static String trimToNull(String value) {
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
