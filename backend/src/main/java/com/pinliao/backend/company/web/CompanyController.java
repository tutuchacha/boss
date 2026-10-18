package com.pinliao.backend.company.web;

import com.pinliao.backend.auth.view.AuthViews;
import com.pinliao.backend.company.dto.CompanyRequests;
import com.pinliao.backend.company.service.CompanyService;
import com.pinliao.backend.job.view.JobViews;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 公司模块接口（契约 docs/api-endpoints.md §5.1~§5.6）。
 *
 * <p>§5.1 / §5.2 公开读（由 {@code AuthInterceptor} 按方法 + 路径放行匿名）；
 * §5.3 需 HR / COMPANY_ADMIN；§5.4~§5.6 需公司管理员（成员角色 ADMIN）。
 * 响应统一由 {@code ResponseAdvice} 包装为 {@code {code, message, data, traceId}}。</p>
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "5. 公司模块", description = "公司详情/职位、我的公司、资料编辑、成员与企业认证（§5.1~§5.6）")
public class CompanyController {

    private final CompanyService companyService;

    public CompanyController(CompanyService companyService) {
        this.companyService = companyService;
    }

    /** §5.1 公司详情（公开；不存在 40410）。 */
    @GetMapping("/companies/{companyId}")
    public AuthViews.Company detail(@PathVariable String companyId) {
        return companyService.detail(companyId);
    }

    /** §5.2 公司发布的职位（公开；可见职位游标分页）。 */
    @GetMapping("/companies/{companyId}/jobs")
    public JobViews.Page<JobViews.Summary> jobs(@PathVariable String companyId,
                                                @RequestParam(required = false) String cursor,
                                                @RequestParam(required = false) Integer limit) {
        return companyService.jobs(companyId, cursor, limit);
    }

    /** §5.3 我所在公司（HR / COMPANY_ADMIN）。 */
    @GetMapping("/company/me")
    public AuthViews.Company myCompany() {
        return companyService.myCompany();
    }

    /** §5.4 更新公司信息（公司管理员；部分字段）。 */
    @PatchMapping("/company/me")
    public AuthViews.Company update(@RequestBody CompanyRequests.Patch body) {
        return companyService.update(body);
    }

    /** §5.5 公司成员列表（公司管理员）。 */
    @GetMapping("/company/members")
    public List<AuthViews.CompanyMember> members() {
        return companyService.members();
    }

    /** §5.6 企业认证提交（公司管理员；40916 已认证 / 42210 资料校验）。 */
    @PostMapping("/company/me/verify")
    public AuthViews.Company verify(@RequestBody CompanyRequests.Verify body) {
        return companyService.verify(body);
    }
}
