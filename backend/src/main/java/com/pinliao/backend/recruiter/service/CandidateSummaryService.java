package com.pinliao.backend.recruiter.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pinliao.backend.application.entity.ResumeEntity;
import com.pinliao.backend.application.mapper.ResumeMapper;
import com.pinliao.backend.auth.entity.UserProfileEntity;
import com.pinliao.backend.auth.mapper.UserProfileMapper;
import com.pinliao.backend.recruiter.view.RecruiterViews;
import org.springframework.stereotype.Service;

/**
 * 候选人摘要装配（§4.4 投递列表 / §4.5 匹配结果共用）。
 *
 * <p>口径与 T6 {@code ConversationQueryService.candidateOf} 保持一致（同一批候选人在「沟通」与
 * 「候选人管理 / 人才筛选」里的姓名与画像必须自洽）：缺画像时给占位文案，字段缺失给
 * {@code —} / {@code 0}；简历完善度沿用「存在可用简历 = 85，缺失 = 0」的演示算法
 * （数据模型暂无该列，后续可细化）。</p>
 */
@Service
public class CandidateSummaryService {

    private static final int COMPLETENESS_WITH_RESUME = 85;

    private final UserProfileMapper userProfileMapper;
    private final ResumeMapper resumeMapper;

    public CandidateSummaryService(UserProfileMapper userProfileMapper, ResumeMapper resumeMapper) {
        this.userProfileMapper = userProfileMapper;
        this.resumeMapper = resumeMapper;
    }

    /** 由 user_id 装配候选人摘要（画像缺失时返回占位摘要，不抛错）。 */
    public RecruiterViews.Candidate summarise(String userId) {
        return summarise(userId, profileOf(userId));
    }

    /** 已持有画像时避免重复查询。 */
    public RecruiterViews.Candidate summarise(String userId, UserProfileEntity profile) {
        if (profile == null) {
            return new RecruiterViews.Candidate(userId, "候选人", "—", "—", 0, "—", 0);
        }
        return new RecruiterViews.Candidate(userId,
                profile.getName() == null ? "候选人" : profile.getName(),
                profile.getExpectSub() == null ? "—" : profile.getExpectSub(),
                profile.getExpectCity() == null ? "—" : profile.getExpectCity(),
                profile.getWorkYears() == null ? 0 : profile.getWorkYears(),
                profile.getEdu() == null ? "—" : profile.getEdu(),
                resumeCompleteness(userId, profile.getDefaultResumeId()));
    }

    /** 按 user_id 查画像（user_profiles 对 user_id 唯一）。 */
    public UserProfileEntity profileOf(String userId) {
        return userProfileMapper.selectOne(Wrappers.<UserProfileEntity>lambdaQuery()
                .eq(UserProfileEntity::getUserId, userId)
                .last("LIMIT 1"));
    }

    /**
     * 简历完善度：当前数据模型没有该列，演示阶段以「存在可用简历」给 85 分，缺简历给 0
     * （与 T6 / mock 求职者 85 的展示基线一致）。
     */
    public int resumeCompleteness(String userId, String defaultResumeId) {
        if (defaultResumeId != null && !defaultResumeId.isBlank()) {
            ResumeEntity resume = resumeMapper.selectById(defaultResumeId);
            if (resume != null && resume.getDeletedAt() == null) {
                return COMPLETENESS_WITH_RESUME;
            }
        }
        Long count = resumeMapper.selectCount(Wrappers.<ResumeEntity>lambdaQuery()
                .eq(ResumeEntity::getUserId, userId)
                .isNull(ResumeEntity::getDeletedAt));
        return count != null && count > 0 ? COMPLETENESS_WITH_RESUME : 0;
    }
}
