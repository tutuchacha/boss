package com.pinliao.backend.recruiter.web;

import com.pinliao.backend.auth.view.AuthViews;
import com.pinliao.backend.job.service.HrJobQueryService;
import com.pinliao.backend.job.view.JobViews;
import com.pinliao.backend.recruiter.dto.RecruiterRequests;
import com.pinliao.backend.recruiter.service.HrApplicationQueryService;
import com.pinliao.backend.recruiter.service.HrProfileService;
import com.pinliao.backend.recruiter.service.TalentMatchService;
import com.pinliao.backend.recruiter.view.RecruiterViews;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
 * 招聘者接口（契约 §4.1~§4.6）。
 *
 * <p>鉴权：全部要求登录，且角色为 HR / COMPANY_ADMIN 并带企业上下文（{@code Roles.requireManager()}，
 * 越权统一 40301）。响应统一由 {@code ResponseAdvice} 包装为 {@code {code, message, data, traceId}}。</p>
 *
 * <p><b>出参口径（T8 决策）</b>：§4.3 / §4.4 直接返回前端 service 使用的超集结构（非严格契约形状），
 * 以免 T9 联调二次适配；详见 {@code docs/step3-plan.md} 的 T8 说明与各 service 类注释。</p>
 */
@RestController
@RequestMapping("/api/v1/hr")
@Tag(name = "4. 招聘者", description = "画像、我的职位、收到的投递、人才筛选（契约 §4）")
public class RecruiterController {

    private final HrProfileService hrProfileService;
    private final HrJobQueryService hrJobQueryService;
    private final HrApplicationQueryService hrApplicationQueryService;
    private final TalentMatchService talentMatchService;

    public RecruiterController(HrProfileService hrProfileService,
                               HrJobQueryService hrJobQueryService,
                               HrApplicationQueryService hrApplicationQueryService,
                               TalentMatchService talentMatchService) {
        this.hrProfileService = hrProfileService;
        this.hrJobQueryService = hrJobQueryService;
        this.hrApplicationQueryService = hrApplicationQueryService;
        this.talentMatchService = talentMatchService;
    }

    /** §4.1 获取招聘者画像。 */
    @GetMapping("/profile")
    public AuthViews.HrProfile profile() {
        return hrProfileService.profile();
    }

    /** §4.2 更新招聘者画像（部分字段；null = 不修改）。 */
    @PatchMapping("/profile")
    public AuthViews.HrProfile updateProfile(@Valid @RequestBody RecruiterRequests.HrProfilePatch patch) {
        return hrProfileService.update(patch);
    }

    /**
     * §4.3 我（所在公司）发布的职位。
     *
     * <p>{@code cursor} 为契约保留参数，演示规模未启用游标分页（前端 {@code listMyJobs} 不传）；
     * {@code q} / {@code hrId} 为契约外扩展筛选（对齐前端 mock）。</p>
     */
    @GetMapping("/jobs")
    public List<JobViews.HrRecord> myJobs(@RequestParam(required = false) String status,
                                         @RequestParam(required = false) String q,
                                         @RequestParam(required = false) String hrId,
                                         @RequestParam(required = false) String cursor,
                                         @RequestParam(required = false) Integer limit) {
        return hrJobQueryService.list(status, q, hrId, limit);
    }

    /**
     * §4.3+ 单个职位（HR 视角，含已关闭职位）。
     *
     * <p>契约未列该读取；公开详情（§6.3）只认 {@code OPEN}，而 HR 编辑页需要打开已关闭职位的内容。</p>
     */
    @GetMapping("/jobs/{jobId}")
    public JobViews.HrRecord myJob(@PathVariable String jobId) {
        return hrJobQueryService.detail(jobId);
    }

    /**
     * §4.4 我收到的投递。
     *
     * <p>{@code q} 为契约外扩展筛选（候选人姓名 / 职位名称，对齐前端 mock）；{@code cursor} 同 §4.3 保留未启用。</p>
     */
    @GetMapping("/applications")
    public List<RecruiterViews.ApplicationCard> myApplications(@RequestParam(required = false) String status,
                                                              @RequestParam(required = false) String jobId,
                                                              @RequestParam(required = false) String q,
                                                              @RequestParam(required = false) String cursor,
                                                              @RequestParam(required = false) Integer limit) {
        return hrApplicationQueryService.list(status, jobId, q, limit);
    }

    /** §4.5 候选人匹配（按技能需求筛选；query 与 tags 至少一个非空，否则 42210）。 */
    @PostMapping("/candidates/match")
    public List<RecruiterViews.CandidateMatch> matchCandidates(
            @Valid @RequestBody RecruiterRequests.CandidateMatchInput input) {
        return talentMatchService.match(input);
    }

    /** §4.6 技能标签词表。 */
    @GetMapping("/candidates/match/tags")
    public List<String> matchTags() {
        return talentMatchService.tags();
    }
}
