package com.pinliao.backend.resume.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pinliao.backend.application.entity.ApplicationEntity;
import com.pinliao.backend.application.entity.ResumeEntity;
import com.pinliao.backend.application.mapper.ApplicationMapper;
import com.pinliao.backend.application.mapper.ResumeMapper;
import com.pinliao.backend.auth.context.Roles;
import com.pinliao.backend.auth.context.UserContext;
import com.pinliao.backend.auth.entity.UserProfileEntity;
import com.pinliao.backend.auth.mapper.UserProfileMapper;
import com.pinliao.backend.common.api.ApiCodes;
import com.pinliao.backend.common.exception.BusinessException;
import com.pinliao.backend.resume.ResumeCodes;
import com.pinliao.backend.resume.dto.ResumeRequests;
import com.pinliao.backend.resume.view.ResumeViews;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 简历服务（契约 §3.1~§3.6）。
 *
 * <p><b>权限</b>：求职者只能读写本人简历；HR 仅可查看「已投递到本公司的简历」
 * （按 applications.resume_id + company_id 判定）。不存在 / 越权统一 40410。</p>
 *
 * <p><b>默认简历</b>：同一用户最多一条 is_default=true；创建首份自动设为默认，
 * 设置/删除默认时同步 {@code user_profiles.default_resume_id}，供投递与消息快捷操作复用。</p>
 */
@Service
public class ResumeService {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final ResumeMapper resumeMapper;
    private final UserProfileMapper userProfileMapper;
    private final ApplicationMapper applicationMapper;

    public ResumeService(ResumeMapper resumeMapper,
                         UserProfileMapper userProfileMapper,
                         ApplicationMapper applicationMapper) {
        this.resumeMapper = resumeMapper;
        this.userProfileMapper = userProfileMapper;
        this.applicationMapper = applicationMapper;
    }

    /** §3.1 我的简历列表（默认优先，更新时间倒序）。 */
    public List<ResumeViews.Resume> list() {
        UserContext.CurrentUser user = Roles.requireSeeker();
        List<ResumeEntity> rows = resumeMapper.selectList(new LambdaQueryWrapper<ResumeEntity>()
                .eq(ResumeEntity::getUserId, user.userId())
                .isNull(ResumeEntity::getDeletedAt));
        rows = new ArrayList<>(rows);
        rows.sort(Comparator.comparing((ResumeEntity r) -> !Boolean.TRUE.equals(r.getIsDefault()))
                .thenComparing((ResumeEntity r) -> epochMillis(r.getUpdatedAt()), Comparator.reverseOrder())
                .thenComparing(ResumeEntity::getId));
        return rows.stream().map(this::view).toList();
    }

    /** §3.2 简历详情（本人 / 已投递到本公司的 HR）。 */
    public ResumeViews.Resume detail(String resumeId) {
        UserContext.CurrentUser user = UserContext.require();
        ResumeEntity resume = resumeMapper.selectById(resumeId);
        if (resume == null || resume.getDeletedAt() != null) {
            throw notFound();
        }
        if (Roles.isSeeker(user)) {
            if (!user.userId().equals(resume.getUserId())) {
                throw notFound();
            }
        } else if (Roles.isManager(user)) {
            if (!hrCanView(user, resume)) {
                throw notFound();
            }
        } else {
            throw new BusinessException(ApiCodes.FORBIDDEN, "无权查看该简历");
        }
        return view(resume);
    }

    /** §3.3 创建简历（求职者）。 */
    @Transactional
    public ResumeViews.Resume create(ResumeRequests.CreateRequest req) {
        UserContext.CurrentUser user = Roles.requireSeeker();
        List<ResumeEntity> all = allRows(user.userId());
        boolean first = all.stream().noneMatch(r -> r.getDeletedAt() == null);

        LocalDateTime now = now();
        ResumeEntity resume = new ResumeEntity();
        resume.setId(nextResumeId(user.userId(), all));
        resume.setUserId(user.userId());
        resume.setName(req.name().trim());
        resume.setContent(writeContent(req.content()));
        resume.setAttachmentUrl(blankToNull(req.attachmentUrl()));
        resume.setIsDefault(first);
        resume.setCreatedAt(now);
        resume.setUpdatedAt(now);
        resumeMapper.insert(resume);
        if (first) {
            setProfileDefault(user.userId(), resume.getId(), now);
        }
        return view(resume);
    }

    /** §3.4 更新简历（求职者本人；content 整体替换）。 */
    @Transactional
    public ResumeViews.Resume update(String resumeId, ResumeRequests.UpdateRequest req) {
        UserContext.CurrentUser user = Roles.requireSeeker();
        ResumeEntity resume = requireOwn(user.userId(), resumeId);
        boolean wasDefault = Boolean.TRUE.equals(resume.getIsDefault());

        if (req.name() != null) {
            resume.setName(req.name().trim());
        }
        if (req.content() != null) {
            resume.setContent(writeContent(req.content()));
        }
        if (req.attachmentUrl() != null) {
            resume.setAttachmentUrl(blankToNull(req.attachmentUrl()));
        }
        LocalDateTime now = now();
        resume.setUpdatedAt(now);

        if (Boolean.TRUE.equals(req.isDefault())) {
            resume.setIsDefault(true);
            resumeMapper.updateById(resume);
            setDefaultInternal(resume, now);
            return view(resume);
        }
        if (Boolean.FALSE.equals(req.isDefault()) && wasDefault) {
            resume.setIsDefault(false);
            resumeMapper.updateById(resume);
            clearProfileDefault(user.userId(), now);
            return view(resume);
        }

        resumeMapper.updateById(resume);
        return view(resume);
    }

    /** §3.5 删除简历（软删；删除默认时自动把最近更新的剩余简历设为默认）。 */
    @Transactional
    public void delete(String resumeId) {
        UserContext.CurrentUser user = Roles.requireSeeker();
        ResumeEntity resume = requireOwn(user.userId(), resumeId);
        boolean wasDefault = Boolean.TRUE.equals(resume.getIsDefault());
        LocalDateTime now = now();
        resume.setDeletedAt(now);
        resume.setUpdatedAt(now);
        resumeMapper.updateById(resume);

        if (!wasDefault) {
            return;
        }
        List<ResumeEntity> remaining = resumeMapper.selectList(new LambdaQueryWrapper<ResumeEntity>()
                .eq(ResumeEntity::getUserId, user.userId())
                .isNull(ResumeEntity::getDeletedAt)
                .orderByDesc(ResumeEntity::getUpdatedAt)
                .orderByAsc(ResumeEntity::getId));
        if (remaining.isEmpty()) {
            clearProfileDefault(user.userId(), now);
        } else {
            setDefaultInternal(remaining.get(0), now);
        }
    }

    /** §3.6 设为默认简历（求职者本人）。 */
    @Transactional
    public ResumeViews.Resume setDefault(String resumeId) {
        UserContext.CurrentUser user = Roles.requireSeeker();
        ResumeEntity resume = requireOwn(user.userId(), resumeId);
        setDefaultInternal(resume, now());
        return view(resume);
    }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    private ResumeEntity requireOwn(String userId, String resumeId) {
        ResumeEntity resume = resumeMapper.selectById(resumeId);
        if (resume == null || resume.getDeletedAt() != null || !userId.equals(resume.getUserId())) {
            throw notFound();
        }
        return resume;
    }

    private boolean hrCanView(UserContext.CurrentUser user, ResumeEntity resume) {
        if (user.companyId() == null || user.companyId().isBlank()) {
            return false;
        }
        Long count = applicationMapper.selectCount(new LambdaQueryWrapper<ApplicationEntity>()
                .eq(ApplicationEntity::getResumeId, resume.getId())
                .eq(ApplicationEntity::getCompanyId, user.companyId())
                .isNull(ApplicationEntity::getDeletedAt));
        return count != null && count > 0;
    }

    private void setDefaultInternal(ResumeEntity target, LocalDateTime now) {
        // 先清掉该用户全部非删除简历的默认标记，再设置目标，保证唯一性
        resumeMapper.update(null, Wrappers.<ResumeEntity>lambdaUpdate()
                .eq(ResumeEntity::getUserId, target.getUserId())
                .isNull(ResumeEntity::getDeletedAt)
                .set(ResumeEntity::getIsDefault, false)
                .set(ResumeEntity::getUpdatedAt, now));
        target.setIsDefault(true);
        target.setUpdatedAt(now);
        resumeMapper.updateById(target);
        setProfileDefault(target.getUserId(), target.getId(), now);
    }

    private void setProfileDefault(String userId, String resumeId, LocalDateTime now) {
        userProfileMapper.update(null, Wrappers.<UserProfileEntity>lambdaUpdate()
                .eq(UserProfileEntity::getUserId, userId)
                .set(UserProfileEntity::getDefaultResumeId, resumeId)
                .set(UserProfileEntity::getUpdatedAt, now));
    }

    private void clearProfileDefault(String userId, LocalDateTime now) {
        userProfileMapper.update(null, Wrappers.<UserProfileEntity>lambdaUpdate()
                .eq(UserProfileEntity::getUserId, userId)
                .set(UserProfileEntity::getDefaultResumeId, null)
                .set(UserProfileEntity::getUpdatedAt, now));
    }

    private List<ResumeEntity> allRows(String userId) {
        return resumeMapper.selectList(new LambdaQueryWrapper<ResumeEntity>()
                .eq(ResumeEntity::getUserId, userId));
    }

    /** 递增派生 id：resume-{userId}-{max+1}；软删行也参与取最大，避免复用。 */
    static String nextResumeId(String userId, List<ResumeEntity> rows) {
        String prefix = "resume-" + userId + "-";
        int max = 0;
        for (ResumeEntity row : rows) {
            String id = row.getId();
            if (id == null || !id.startsWith(prefix)) {
                continue;
            }
            String suffix = id.substring(prefix.length());
            if (suffix.chars().allMatch(Character::isDigit)) {
                try {
                    max = Math.max(max, Integer.parseInt(suffix));
                } catch (NumberFormatException ignored) {
                    // 超长数字按非本模块 id 处理
                }
            }
        }
        return prefix + (max + 1);
    }

    private ResumeViews.Resume view(ResumeEntity resume) {
        return ResumeViews.Resume.from(resume, parseContent(resume.getContent()));
    }

    private static String writeContent(ResumeRequests.ResumeContent content) {
        try {
            return JSON.writeValueAsString(content.normalized());
        } catch (Exception e) {
            throw new BusinessException(ResumeCodes.INVALID, "简历内容格式不正确", e);
        }
    }

    private static JsonNode parseContent(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        try {
            return JSON.readTree(content);
        } catch (Exception e) {
            return null;
        }
    }

    private static BusinessException notFound() {
        return new BusinessException(ResumeCodes.NOT_FOUND, "简历不存在或无权查看");
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static long epochMillis(LocalDateTime time) {
        return time == null ? 0 : time.toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
