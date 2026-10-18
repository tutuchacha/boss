package com.pinliao.backend.job.web;

import com.pinliao.backend.job.dto.JobRequests;
import com.pinliao.backend.job.service.JobCommandService;
import com.pinliao.backend.job.service.JobQueryService;
import com.pinliao.backend.job.view.JobViews;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 职位模块接口（契约 docs/api-endpoints.md §6.1~§6.8；§6.1~§6.4 / §6.9 公开，§6.5~§6.8 需 HR 登录）。
 *
 * <p>写接口（§6.5~§6.8）仅 HR / COMPANY_ADMIN 且归属本公司；错误码 40310 无权操作、42211 配额已满、
 * 42901 发布限流。响应统一由 ResponseAdvice 包装为 {@code {code, message, data, traceId}}。</p>
 */
@RestController
@RequestMapping("/api/v1/jobs")
@Tag(name = "6. 职位模块", description = "职位浏览/搜索/详情/相似/埋点（§6.1~§6.4、§6.9）+ HR 管理（§6.5~§6.8）")
public class JobController {

    private final JobQueryService jobQueryService;
    private final JobCommandService jobCommandService;

    public JobController(JobQueryService jobQueryService, JobCommandService jobCommandService) {
        this.jobQueryService = jobQueryService;
        this.jobCommandService = jobCommandService;
    }

    /** §6.1 职位列表（公开；Cache-Control 30s；登录求职者 recommend 加权）。 */
    @GetMapping
    public ResponseEntity<JobViews.Page<JobViews.Summary>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String cat,
            @RequestParam(required = false) String sub,
            @RequestParam(required = false) String exp,
            @RequestParam(required = false) String sal,
            @RequestParam(required = false, defaultValue = "recommend") String sort,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        JobViews.Page<JobViews.Summary> page = jobQueryService.list(
                new JobQueryService.Query(q, city, cat, sub, exp, sal, sort, cursor, limit));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofSeconds(30)).cachePublic())
                .body(page);
    }

    /** §6.3 职位详情（公开；Cache-Control 60s + ETag，If-None-Match 命中返回 304）。 */
    @GetMapping("/{jobId}")
    public ResponseEntity<JobViews.Detail> detail(
            @PathVariable String jobId,
            @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {
        JobQueryService.DetailResult result = jobQueryService.detail(jobId);
        CacheControl cache = CacheControl.maxAge(Duration.ofSeconds(60)).cachePublic();
        if (result.etag().equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .cacheControl(cache)
                    .eTag(result.etag())
                    .build();
        }
        return ResponseEntity.ok()
                .cacheControl(cache)
                .eTag(result.etag())
                .body(result.detail());
    }

    /** §6.4 相似职位（公开；limit 默认 6）。 */
    @GetMapping("/{jobId}/similar")
    public List<JobViews.Summary> similar(@PathVariable String jobId,
                                          @RequestParam(required = false) Integer limit) {
        return jobQueryService.similar(jobId, limit);
    }

    /** §6.9 浏览埋点（公开；ip + jobId + 30 分钟窗口去重后 viewCount+1）。 */
    @PostMapping("/{jobId}/view")
    public Map<String, Object> view(@PathVariable String jobId, HttpServletRequest request) {
        jobQueryService.recordView(jobId, clientIp(request));
        return Map.of();
    }

    /** §6.5 发布职位（HR / COMPANY_ADMIN；42211 配额 / 42901 限流）。 */
    @PostMapping
    public JobViews.HrRecord create(@RequestBody JobRequests.Upsert body) {
        return jobCommandService.create(body);
    }

    /** §6.6 更新职位（本公司；部分字段，null 保留原值）。 */
    @PatchMapping("/{jobId}")
    public JobViews.HrRecord update(@PathVariable String jobId, @RequestBody JobRequests.Upsert body) {
        return jobCommandService.update(jobId, body);
    }

    /** §6.7 关闭职位。 */
    @PostMapping("/{jobId}/close")
    public JobViews.HrRecord close(@PathVariable String jobId) {
        return jobCommandService.close(jobId);
    }

    /** §6.7 重新开放职位。 */
    @PostMapping("/{jobId}/reopen")
    public JobViews.HrRecord reopen(@PathVariable String jobId) {
        return jobCommandService.reopen(jobId);
    }

    /** §6.8 删除职位（软删；重复删除幂等）。 */
    @DeleteMapping("/{jobId}")
    public Map<String, Object> delete(@PathVariable String jobId) {
        jobCommandService.delete(jobId);
        return Map.of();
    }

    /** 取客户端 IP：优先 X-Forwarded-For 第一跳（T9 经 Next 代理后 remoteAddr 恒为 127.0.0.1）。 */
    static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
