package com.pinliao.backend.company.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pinliao.backend.auth.context.Roles;
import com.pinliao.backend.auth.context.UserContext;
import com.pinliao.backend.auth.entity.CompanyEntity;
import com.pinliao.backend.auth.entity.CompanyMemberEntity;
import com.pinliao.backend.auth.entity.HrProfileEntity;
import com.pinliao.backend.auth.entity.UserEntity;
import com.pinliao.backend.auth.mapper.CompanyMapper;
import com.pinliao.backend.auth.mapper.CompanyMemberMapper;
import com.pinliao.backend.auth.mapper.HrProfileMapper;
import com.pinliao.backend.auth.mapper.UserMapper;
import com.pinliao.backend.auth.view.AuthViews;
import com.pinliao.backend.common.api.ApiCodes;
import com.pinliao.backend.common.exception.BusinessException;
import com.pinliao.backend.company.CompanyCodes;
import com.pinliao.backend.company.dto.CompanyRequests;
import com.pinliao.backend.job.service.JobQueryService;
import com.pinliao.backend.job.view.JobViews;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * 公司模块（契约 §5.1~§5.6）。
 *
 * <p><b>权限口径</b>：§5.3 允许 HR / COMPANY_ADMIN 读取本公司；§5.4~§5.6 要求公司管理员。
 * 演示账号 {@code 13800000002} 的 user.role 是 HR，但 company_members.role 是 ADMIN，
 * 因此这里的「管理员」以 company_members.role=ADMIN 为准（与 mock {@code MOCK_COMPANY_MEMBER}
 * 及注册自动建 ADMIN 成员的语义一致）；user.role=COMPANY_ADMIN 视为平台级管理员直接放行。</p>
 *
 * <p><b>企业认证</b>：提交后置 {@code verified=false / status=PENDING}，约 1.2 秒后由
 * 后台调度器自动通过（{@code verified=true / status=ACTIVE}），前端轮询 §5.3 即可看到结果。
 * 提交的四项认证资料只做校验，不单列持久化表（契约未暴露提交记录，数据模型也没有对应表；
 * 审核状态复用 companies.verified / status，与 mock 的展示语义一致）。</p>
 *
 * <p><b>改名同步</b>：职位视图的公司名称来自 companies 表实时装配，不存冗余快照；
 * 因此 §5.4 改名后，§5.1/§5.2/§6.x 的职位公司名会立即同步，无需额外刷 jobs 行。</p>
 */
@Service
public class CompanyService {

    private static final Logger log = LoggerFactory.getLogger(CompanyService.class);
    private static final String MEMBER_ADMIN = "ADMIN";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final Pattern LICENSE_NO = Pattern.compile("^[0-9A-Z]{18}$");
    private static final Pattern CONTACT_PHONE = Pattern.compile("^1\\d{10}$");

    private final CompanyMapper companyMapper;
    private final CompanyMemberMapper companyMemberMapper;
    private final UserMapper userMapper;
    private final HrProfileMapper hrProfileMapper;
    private final JobQueryService jobQueryService;
    private final long verifyDelayMs;
    private final ScheduledExecutorService verifyScheduler;

    public CompanyService(CompanyMapper companyMapper,
                          CompanyMemberMapper companyMemberMapper,
                          UserMapper userMapper,
                          HrProfileMapper hrProfileMapper,
                          JobQueryService jobQueryService,
                          @Value("${pinliao.company.verify-delay-ms:1200}") long verifyDelayMs) {
        this.companyMapper = companyMapper;
        this.companyMemberMapper = companyMemberMapper;
        this.userMapper = userMapper;
        this.hrProfileMapper = hrProfileMapper;
        this.jobQueryService = jobQueryService;
        this.verifyDelayMs = verifyDelayMs;
        this.verifyScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "company-verify-review");
            thread.setDaemon(true);
            return thread;
        });
    }

    // ------------------------------------------------------------------
    // §5.1 / §5.2 公开读
    // ------------------------------------------------------------------

    /** §5.1 公司详情（公开；不存在 → 40410）。 */
    public AuthViews.Company detail(String companyId) {
        return AuthViews.Company.from(requireCompany(companyId));
    }

    /** §5.2 公司发布的职位（公开；仅 OPEN 且未删除，游标分页）。 */
    public JobViews.Page<JobViews.Summary> jobs(String companyId, String cursor, Integer limit) {
        requireCompany(companyId);
        return jobQueryService.listByCompany(companyId, cursor, limit);
    }

    // ------------------------------------------------------------------
    // §5.3 我所在公司
    // ------------------------------------------------------------------

    /** §5.3 我所在公司（HR / COMPANY_ADMIN；companyId 取自登录上下文）。 */
    public AuthViews.Company myCompany() {
        UserContext.CurrentUser user = Roles.requireManager();
        return AuthViews.Company.from(requireCompany(user.companyId()));
    }

    // ------------------------------------------------------------------
    // §5.4 更新公司信息
    // ------------------------------------------------------------------

    /** §5.4 更新公司信息（公司管理员；部分字段，null 保留原值）。 */
    public AuthViews.Company update(CompanyRequests.Patch patch) {
        UserContext.CurrentUser user = requireCompanyAdmin();
        CompanyEntity company = requireCompany(user.companyId());
        if (patch == null) {
            throw invalid("请提供要更新的公司资料");
        }

        boolean changed = false;
        if (patch.name() != null) {
            String name = patch.name().trim();
            if (name.length() < 2 || name.length() > 30) {
                throw invalid("公司名称需为 2-30 个字");
            }
            if (!name.equals(company.getName())) {
                company.setName(name);
                changed = true;
            }
        }
        if (patch.logoUrl() != null) {
            company.setLogoUrl(requireMaxLength(patch.logoUrl(), 512, "公司 Logo 地址"));
            changed = true;
        }
        if (patch.intro() != null) {
            company.setIntro(requireMaxLength(patch.intro(), 500, "公司简介"));
            changed = true;
        }
        if (patch.industry() != null) {
            company.setIndustry(requireMaxLength(patch.industry(), 64, "所属行业"));
            changed = true;
        }
        if (patch.stage() != null) {
            company.setStage(requireMaxLength(patch.stage(), 32, "融资阶段"));
            changed = true;
        }
        if (patch.size() != null) {
            company.setSize(requireMaxLength(patch.size(), 32, "公司规模"));
            changed = true;
        }
        if (patch.website() != null) {
            company.setWebsite(requireMaxLength(patch.website(), 256, "公司官网"));
            changed = true;
        }

        if (changed) {
            int rows = companyMapper.updateById(company);
            if (rows == 0) {
                throw new BusinessException(CompanyCodes.COMPANY_NOT_FOUND, "公司不存在");
            }
        }
        return AuthViews.Company.from(company);
    }

    // ------------------------------------------------------------------
    // §5.5 公司成员
    // ------------------------------------------------------------------

    /** §5.5 公司成员列表（公司管理员；返回本公司全部成员）。 */
    public List<AuthViews.CompanyMember> members() {
        UserContext.CurrentUser user = requireCompanyAdmin();
        String companyId = user.companyId();
        List<CompanyMemberEntity> members = companyMemberMapper.selectList(
                new LambdaQueryWrapper<CompanyMemberEntity>()
                        .eq(CompanyMemberEntity::getCompanyId, companyId)
                        .orderByAsc(CompanyMemberEntity::getJoinedAt)
                        .orderByAsc(CompanyMemberEntity::getId));
        if (members.isEmpty()) {
            return List.of();
        }

        List<String> userIds = members.stream().map(CompanyMemberEntity::getUserId).distinct().toList();
        Map<String, UserEntity> users = new HashMap<>();
        for (UserEntity entity : userMapper.selectBatchIds(userIds)) {
            users.put(entity.getId(), entity);
        }
        Map<String, HrProfileEntity> profiles = new HashMap<>();
        for (HrProfileEntity profile : hrProfileMapper.selectList(
                new LambdaQueryWrapper<HrProfileEntity>()
                        .eq(HrProfileEntity::getCompanyId, companyId)
                        .in(HrProfileEntity::getUserId, userIds))) {
            profiles.put(profile.getUserId(), profile);
        }

        List<AuthViews.CompanyMember> result = new ArrayList<>(members.size());
        for (CompanyMemberEntity member : members) {
            UserEntity entity = users.get(member.getUserId());
            AuthViews.User userView = entity == null ? null : AuthViews.User.from(entity);
            HrProfileEntity profile = profiles.get(member.getUserId());
            AuthViews.HrProfile profileView = profile == null ? null : AuthViews.HrProfile.from(profile);
            result.add(AuthViews.CompanyMember.from(member, userView, profileView));
        }
        return result;
    }

    // ------------------------------------------------------------------
    // §5.6 企业认证
    // ------------------------------------------------------------------

    /**
     * §5.6 企业认证提交。
     *
     * <p>已认证 → 40916；字段不合法 → 42210；成功后 PENDING，并在
     * {@code pinliao.company.verify-delay-ms}（默认 1200ms）后自动通过。</p>
     */
    public AuthViews.Company verify(CompanyRequests.Verify input) {
        UserContext.CurrentUser user = requireCompanyAdmin();
        CompanyEntity company = requireCompany(user.companyId());
        if (Boolean.TRUE.equals(company.getVerified())) {
            throw new BusinessException(CompanyCodes.ALREADY_VERIFIED, "该公司已完成企业认证，无需重复提交");
        }
        if (input == null) {
            throw invalid("请填写企业认证资料");
        }

        String companyName = requireText(input.companyName(), "请填写完整的企业名称");
        if (companyName.length() < 2) {
            throw invalid("请填写完整的企业名称");
        }
        if (companyName.length() > 128) {
            throw invalid("企业名称不超过 128 个字");
        }
        String licenseNo = requireText(input.licenseNo(), "统一社会信用代码需为 18 位大写字母或数字")
                .toUpperCase(Locale.ROOT);
        if (!LICENSE_NO.matcher(licenseNo).matches()) {
            throw invalid("统一社会信用代码需为 18 位大写字母或数字");
        }
        String contactName = requireText(input.contactName(), "请填写联系人姓名");
        if (contactName.length() > 64) {
            throw invalid("联系人姓名不超过 64 个字");
        }
        String contactPhone = requireText(input.contactPhone(), "请填写正确的联系电话");
        if (!CONTACT_PHONE.matcher(contactPhone).matches()) {
            throw invalid("请填写正确的联系电话");
        }

        company.setVerified(false);
        company.setStatus(STATUS_PENDING);
        int rows = companyMapper.updateById(company);
        if (rows == 0) {
            throw new BusinessException(CompanyCodes.COMPANY_NOT_FOUND, "公司不存在");
        }
        scheduleAutoApproval(company.getId());
        return AuthViews.Company.from(company);
    }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    /**
     * 公司管理员校验：HR / COMPANY_ADMIN 且带企业上下文；HR 还须是 company_members.role=ADMIN。
     *
     * <p>演示 HR 账号的用户角色是 HR、成员角色是 ADMIN，因此不能只看 token 里的 role。</p>
     */
    private UserContext.CurrentUser requireCompanyAdmin() {
        UserContext.CurrentUser user = Roles.requireManager();
        if (!Roles.COMPANY_ADMIN.equals(user.role())) {
            CompanyMemberEntity member = companyMemberMapper.selectOne(
                    new LambdaQueryWrapper<CompanyMemberEntity>()
                            .eq(CompanyMemberEntity::getUserId, user.userId())
                            .eq(CompanyMemberEntity::getCompanyId, user.companyId())
                            .eq(CompanyMemberEntity::getRole, MEMBER_ADMIN)
                            .last("LIMIT 1"));
            if (member == null || !MEMBER_ADMIN.equals(member.getRole())) {
                throw new BusinessException(ApiCodes.FORBIDDEN, "仅企业管理员可执行该操作");
            }
        }
        return user;
    }

    private CompanyEntity requireCompany(String companyId) {
        CompanyEntity company = companyId == null || companyId.isBlank()
                ? null
                : companyMapper.selectById(companyId);
        if (company == null) {
            throw new BusinessException(CompanyCodes.COMPANY_NOT_FOUND, "公司不存在");
        }
        return company;
    }

    private void scheduleAutoApproval(String companyId) {
        verifyScheduler.schedule(() -> autoApprove(companyId), verifyDelayMs, TimeUnit.MILLISECONDS);
    }

    /** 模拟人工审核通过：只处理仍处于 PENDING 的公司，避免覆盖期间的禁用/删除状态。 */
    private void autoApprove(String companyId) {
        try {
            CompanyEntity company = companyMapper.selectById(companyId);
            if (company == null
                    || Boolean.TRUE.equals(company.getVerified())
                    || !STATUS_PENDING.equals(company.getStatus())) {
                return;
            }
            company.setVerified(true);
            company.setStatus(STATUS_ACTIVE);
            companyMapper.updateById(company);
        } catch (RuntimeException e) {
            log.warn("企业认证自动通过失败 companyId={}", companyId, e);
        }
    }

    private static String requireText(String value, String message) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            throw invalid(message);
        }
        return trimmed;
    }

    private static String requireMaxLength(String value, int max, String label) {
        if (value.length() > max) {
            throw invalid(label + "不超过 " + max + " 个字符");
        }
        return value;
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(CompanyCodes.PROFILE_INVALID, message);
    }

    @PreDestroy
    public void shutdown() {
        verifyScheduler.shutdownNow();
    }
}
