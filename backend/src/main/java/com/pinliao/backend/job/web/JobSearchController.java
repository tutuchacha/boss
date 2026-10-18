package com.pinliao.backend.job.web;

import com.pinliao.backend.job.service.JobQueryService;
import com.pinliao.backend.job.view.JobViews;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * 职位搜索（契约 §6.2：独立路径，便于后期切到 ES）。
 *
 * <p>首发与 §6.1 共享实现（{@link JobQueryService#list}）；后期独立 ES 集群时此路径切走，前端无感。</p>
 */
@RestController
@RequestMapping("/api/v1/search")
@Tag(name = "6. 职位模块", description = "职位浏览/搜索/详情/相似/埋点（契约 §6；HR 管理见 §6.5~§6.8）")
public class JobSearchController {

    private final JobQueryService jobQueryService;

    public JobSearchController(JobQueryService jobQueryService) {
        this.jobQueryService = jobQueryService;
    }

    /** §6.2 职位搜索（公开；查询参数同 §6.1）。 */
    @GetMapping("/jobs")
    public ResponseEntity<JobViews.Page<JobViews.Summary>> search(
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
}
