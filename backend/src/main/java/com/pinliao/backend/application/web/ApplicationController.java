package com.pinliao.backend.application.web;

import com.pinliao.backend.application.dto.ApplicationRequests;
import com.pinliao.backend.application.service.ApplicationCommandService;
import com.pinliao.backend.application.service.ApplicationQueryService;
import com.pinliao.backend.application.view.ApplicationViews;
import com.pinliao.backend.common.api.PageViews;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 投递 / 面试接口（契约 §9.1~§9.5、§10.1~§10.5；§8.7 约面试）。
 *
 * <p>鉴权由 {@code AuthInterceptor} 统一处理；角色与归属在 service 层守卫。
 * 响应统一由 {@code ResponseAdvice} 包装为 {@code {code, message, data, traceId}}。</p>
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "9/10. 投递与面试", description = "投递状态机、面试邀请与接受/拒绝（§9/§10）+ §8.7 约面试")
public class ApplicationController {

    private final ApplicationCommandService commandService;
    private final ApplicationQueryService queryService;

    public ApplicationController(ApplicationCommandService commandService, ApplicationQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    /** §9.1 投递职位（JOB_SEEKER；40911 已投递 / 42210 简历不完整）。 */
    @PostMapping("/applications")
    public ApplicationViews.Application apply(@RequestBody ApplicationRequests.Apply body) {
        return commandService.apply(body);
    }

    /** §9.2 我的投递列表（JOB_SEEKER；cursor / limit / status）。 */
    @GetMapping("/applications")
    public PageViews.Page<ApplicationViews.WithJob> myApplications(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return queryService.listApplications(status, cursor, limit);
    }

    /** §9.3 投递详情（本人 / 本公司）。 */
    @GetMapping("/applications/{applicationId}")
    public ApplicationViews.Detail detail(@PathVariable String applicationId) {
        return queryService.detail(applicationId);
    }

    /** §9.4 撤回投递（JOB_SEEKER 本人；40913 当前状态不可撤回）。 */
    @PostMapping("/applications/{applicationId}/withdraw")
    public ApplicationViews.Application withdraw(@PathVariable String applicationId) {
        return commandService.withdraw(applicationId);
    }

    /** §9.5 HR 更新投递状态（VIEWED / INTERVIEW / OFFER / REJECTED）。 */
    @PostMapping("/applications/{applicationId}/status")
    public ApplicationViews.Application updateStatus(@PathVariable String applicationId,
                                                     @RequestBody ApplicationRequests.StatusChange body) {
        return commandService.updateStatus(applicationId, body);
    }

    /** §10.1 面试邀请列表（求职者视角）。 */
    @GetMapping("/interviews")
    public PageViews.Page<ApplicationViews.InterviewWithJob> myInterviews(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return queryService.listInterviews(status, cursor, limit);
    }

    /** §10.2 面试邀请列表（招聘者视角，本公司）。 */
    @GetMapping("/hr/interviews")
    public PageViews.Page<ApplicationViews.InterviewWithCandidate> hrInterviews(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return queryService.listHrInterviews(status, cursor, limit);
    }

    /** §10.3 面试详情（参与方）。 */
    @GetMapping("/interviews/{interviewId}")
    public ApplicationViews.Interview interview(@PathVariable String interviewId) {
        return queryService.interviewDetail(interviewId);
    }

    /** §10.4 接受面试（JOB_SEEKER 本人）。 */
    @PostMapping("/interviews/{interviewId}/accept")
    public ApplicationViews.Interview accept(@PathVariable String interviewId) {
        return commandService.accept(interviewId);
    }

    /** §10.5 拒绝面试（JOB_SEEKER 本人；reason 可选）。 */
    @PostMapping("/interviews/{interviewId}/decline")
    public ApplicationViews.Interview decline(@PathVariable String interviewId,
                                              @RequestBody(required = false) ApplicationRequests.Decline body) {
        return commandService.decline(interviewId, body == null ? new ApplicationRequests.Decline(null) : body);
    }

    /** §8.7 约面试（HR 快捷操作：创建 Interview + 投递置 INTERVIEW + 邀请消息）。 */
    @PostMapping("/conversations/{conversationId}/actions/invite-interview")
    public ApplicationViews.Message inviteInterview(@PathVariable String conversationId,
                                                    @RequestBody ApplicationRequests.Invite body) {
        return commandService.invite(conversationId, body);
    }
}
