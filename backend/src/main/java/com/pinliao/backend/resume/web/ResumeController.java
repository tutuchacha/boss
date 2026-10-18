package com.pinliao.backend.resume.web;

import com.pinliao.backend.resume.dto.ResumeRequests;
import com.pinliao.backend.resume.service.ResumeService;
import com.pinliao.backend.resume.view.ResumeViews;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 简历接口（契约 §3.1~§3.6）。 */
@RestController
@RequestMapping("/api/v1/resumes")
@Tag(name = "3. 简历", description = "简历 CRUD、默认简历与 HR 投递可见性（契约 §3）")
public class ResumeController {

    private final ResumeService resumeService;

    public ResumeController(ResumeService resumeService) {
        this.resumeService = resumeService;
    }

    /** §3.1 我的简历列表（JOB_SEEKER）。 */
    @GetMapping
    public List<ResumeViews.Resume> list() {
        return resumeService.list();
    }

    /** §3.2 简历详情（本人 / 已投递到本公司的 HR）。 */
    @GetMapping("/{resumeId}")
    public ResumeViews.Resume detail(@PathVariable String resumeId) {
        return resumeService.detail(resumeId);
    }

    /** §3.3 创建简历（JOB_SEEKER）。 */
    @PostMapping
    public ResumeViews.Resume create(@Valid @RequestBody ResumeRequests.CreateRequest body) {
        return resumeService.create(body);
    }

    /** §3.4 更新简历（JOB_SEEKER 本人）。 */
    @PatchMapping("/{resumeId}")
    public ResumeViews.Resume update(@PathVariable String resumeId,
                                     @Valid @RequestBody ResumeRequests.UpdateRequest body) {
        return resumeService.update(resumeId, body);
    }

    /** §3.5 删除简历（软删）。 */
    @DeleteMapping("/{resumeId}")
    public Map<String, Object> delete(@PathVariable String resumeId) {
        resumeService.delete(resumeId);
        return Map.of();
    }

    /** §3.6 设为默认简历。 */
    @PostMapping("/{resumeId}/set-default")
    public ResumeViews.Resume setDefault(@PathVariable String resumeId) {
        return resumeService.setDefault(resumeId);
    }
}
