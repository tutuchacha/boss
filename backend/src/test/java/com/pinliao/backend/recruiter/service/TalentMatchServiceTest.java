package com.pinliao.backend.recruiter.service;

import com.pinliao.backend.application.entity.ApplicationEntity;
import com.pinliao.backend.application.entity.ResumeEntity;
import com.pinliao.backend.application.mapper.ApplicationMapper;
import com.pinliao.backend.application.mapper.ResumeMapper;
import com.pinliao.backend.auth.context.UserContext;
import com.pinliao.backend.auth.entity.UserProfileEntity;
import com.pinliao.backend.common.exception.BusinessException;
import com.pinliao.backend.job.entity.JobEntity;
import com.pinliao.backend.job.mapper.JobMapper;
import com.pinliao.backend.recruiter.dto.RecruiterRequests;
import com.pinliao.backend.recruiter.view.RecruiterViews;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * T8：§4.5 / §4.6 人才筛选（关键词匹配）单元测试。
 *
 * <p>本测试是「移植保真度」的护栏：关键词提取（词典长词优先 / 英文技术词 / 中文去停用词）、
 * 权重（技能 3 > 文本 2）、证据文案（技能 / 求职意向 / 个人简介 / 工作经历 · X / 项目经历 · X）、
 * 证据上限与排序规则都必须与 {@code src/api/mock/talentMatch.ts} 一致——T9 切换到真实后端后
 * 前端展示与 e2e 断言（如「技术栈」落在个人简介片段、「项目经历」出现在证据里）才不变。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TalentMatchServiceTest {

    private static final String REACT_SUMMARY =
            "3 年前端开发经验，熟悉 React 技术栈与前端工程化建设，关注性能优化与用户体验。";

    @Mock
    private ApplicationMapper applicationMapper;
    @Mock
    private ResumeMapper resumeMapper;
    @Mock
    private JobMapper jobMapper;
    @Mock
    private CandidateSummaryService candidateSummaryService;

    private TalentMatchService service;

    @BeforeEach
    void setUp() {
        service = new TalentMatchService(applicationMapper, resumeMapper, jobMapper, candidateSummaryService);
        manager();
        when(jobMapper.selectById(anyString())).thenAnswer(invocation -> job(invocation.getArgument(0)));
        when(candidateSummaryService.profileOf(anyString())).thenAnswer(invocation -> profile());
        when(candidateSummaryService.summarise(anyString(), any())).thenAnswer(invocation -> {
            String userId = invocation.getArgument(0);
            return new RecruiterViews.Candidate(userId, name(userId), expect(userId), "上海", 3, "本科", 85);
        });
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    // ------------------------------------------------------------------
    // §4.5 匹配
    // ------------------------------------------------------------------

    @Test
    void 匹配_技能与文本命中_证据文案与权重对齐mock() {
        givenApplications(app("app-1", "u-cand-1", "job-1", resume("resume-1", "u-cand-1")));

        List<RecruiterViews.CandidateMatch> results =
                service.match(new RecruiterRequests.CandidateMatchInput("React", List.of()));

        assertEquals(1, results.size());
        RecruiterViews.CandidateMatch match = results.get(0);
        assertEquals("app-1", match.applicationId());
        assertEquals("前端工程师", match.jobTitle());
        assertEquals(List.of("React"), match.hitTokens());
        assertEquals(3, match.score(), "技能精确命中权重 3");
        assertEquals(List.of("技能", "个人简介", "项目经历 · 招聘管理系统前端重构"),
                match.evidences().stream().map(RecruiterViews.Evidence::field).toList());
        RecruiterViews.Evidence summaryEvidence = match.evidences().get(1);
        assertTrue(summaryEvidence.snippet().contains("技术栈"), "e2e 依赖：技术栈出现在个人简介片段里");
        assertEquals("React", match.evidences().get(0).snippet(), "技能证据直接取技能项原文");
    }

    @Test
    void 匹配_文本命中权重2_优先于未命中候选人() {
        givenApplications(
                app("app-1", "u-cand-1", "job-1", resume("resume-1", "u-cand-1")),
                app("app-3", "u-cand-3", "job-3", resume("resume-3", "u-cand-3")));

        List<RecruiterViews.CandidateMatch> results =
                service.match(new RecruiterRequests.CandidateMatchInput("前端 测试", List.of()));

        assertEquals(2, results.size());
        assertEquals("u-cand-3", results.get(0).candidate().userId(), "技能命中（3 分）排在文本命中（2 分）之前");
        assertEquals(3, results.get(0).score());
        assertEquals("u-cand-1", results.get(1).candidate().userId());
        assertEquals(2, results.get(1).score());
    }

    @Test
    void 匹配_未命中返回空列表() {
        givenApplications(app("app-1", "u-cand-1", "job-1", resume("resume-1", "u-cand-1")));
        assertEquals(List.of(), service.match(new RecruiterRequests.CandidateMatchInput("COBOL", List.of())));
    }

    @Test
    void 匹配_仅按标签筛选同样生效() {
        givenApplications(app("app-3", "u-cand-3", "job-3", resume("resume-3", "u-cand-3")));
        List<RecruiterViews.CandidateMatch> results =
                service.match(new RecruiterRequests.CandidateMatchInput(null, List.of("自动化测试")));
        assertEquals(1, results.size());
        assertEquals(List.of("自动化测试"), results.get(0).hitTokens());
        assertEquals(3, results.get(0).score());
    }

    @Test
    void 匹配_同一候选人多条投递取最近一条() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).withNano(0);
        givenApplications(
                app("app-old", "u-cand-1", "job-1", resume("resume-old", "u-cand-1"), now.minusDays(3)),
                app("app-new", "u-cand-1", "job-9", resume("resume-new", "u-cand-1"), now.minusHours(1)));

        List<RecruiterViews.CandidateMatch> results =
                service.match(new RecruiterRequests.CandidateMatchInput("React", List.of()));

        assertEquals(1, results.size());
        assertEquals("app-new", results.get(0).applicationId());
        assertEquals("job-9-职位", results.get(0).jobTitle(), "取最近一条投递的职位");
    }

    @Test
    void 匹配_缺简历内容的候选人被跳过() {
        givenApplications(
                app("app-1", "u-cand-1", "job-1", resume("resume-1", "u-cand-1")),
                app("app-4", "u-cand-4", "job-4", null));
        List<RecruiterViews.CandidateMatch> results =
                service.match(new RecruiterRequests.CandidateMatchInput("React", List.of()));
        assertEquals(List.of("u-cand-1"), results.stream().map(m -> m.candidate().userId()).toList());
    }

    @Test
    void 匹配_条件为空报42210_纯停用词返回空列表() {
        BusinessException empty = assertThrows(BusinessException.class,
                () -> service.match(new RecruiterRequests.CandidateMatchInput("  ", List.of())));
        assertEquals(42210, empty.getCode());
        assertThrows(BusinessException.class, () -> service.match(null));

        givenApplications(app("app-1", "u-cand-1", "job-1", resume("resume-1", "u-cand-1")));
        assertEquals(List.of(), service.match(new RecruiterRequests.CandidateMatchInput("熟悉 具备 经验", List.of())),
                "条件非空但提不出关键词时返回空结果（对齐 mock），不报错");
    }

    @Test
    void 匹配_非招聘者报40301() {
        seeker();
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.match(new RecruiterRequests.CandidateMatchInput("React", List.of())));
        assertEquals(40301, e.getCode());
    }

    // ------------------------------------------------------------------
    // §4.6 词表
    // ------------------------------------------------------------------

    @Test
    void 词表_20条且含常用标签() {
        List<String> tags = service.tags();
        assertEquals(20, tags.size());
        assertTrue(tags.contains("前端") && tags.contains("自动化测试") && tags.contains("Figma"));
    }

    // ------------------------------------------------------------------
    // 关键词提取 / 片段（纯函数）
    // ------------------------------------------------------------------

    @Test
    void 关键词提取_词典长词优先_英文技术词_中文去停用词_去重() {
        assertEquals(List.of("TypeScript", "React"),
                TalentMatchService.extractTokens("熟悉 React 和 TypeScript", List.of()));
        assertEquals(List.of("自动化测试"),
                TalentMatchService.extractTokens("自动化测试", List.of()), "长词先命中，子词不重复计数");
        assertEquals(List.of("Node.js", "Go", "C++"),
                TalentMatchService.extractTokens("Node.js / Go / C++", List.of()));
        assertEquals(List.of("React"),
                TalentMatchService.extractTokens("React react REACT", List.of()), "大小写不敏感去重");
        assertEquals(List.of("Kubernetes", "Docker", "高并发"),
                TalentMatchService.extractTokens("熟悉 Kubernetes 与 Docker，具备高并发经验", List.of()));
        assertEquals(List.of(), TalentMatchService.extractTokens("熟悉 掌握 了解", List.of()));
    }

    @Test
    void 片段_取上下文并加省略号() {
        String text = "一二三四五六七八九十甲乙" + "React"
                + "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        String snippet = TalentMatchService.snippetAround(text, "React");
        assertTrue(snippet.startsWith("…") && snippet.endsWith("…"));
        assertTrue(snippet.contains("React"));
        assertTrue(TalentMatchService.snippetAround("短文本 React", "React").startsWith("短文本"));
        assertFalse(TalentMatchService.snippetAround("无命中", "React").contains("…"), "未命中时原样返回");
    }

    // ------------------------------------------------------------------
    // 夹具
    // ------------------------------------------------------------------

    private void givenApplications(ApplicationEntity... apps) {
        when(applicationMapper.selectList(any())).thenReturn(new ArrayList<>(List.of(apps)));
    }

    private static ApplicationEntity app(String id, String userId, String jobId, ResumeEntity resume) {
        return app(id, userId, jobId, resume, LocalDateTime.now(ZoneOffset.UTC).withNano(0).minusHours(2));
    }

    private static ApplicationEntity app(String id, String userId, String jobId, ResumeEntity resume,
                                         LocalDateTime updatedAt) {
        ApplicationEntity app = new ApplicationEntity();
        app.setId(id);
        app.setUserId(userId);
        app.setJobId(jobId);
        app.setCompanyId("co-1");
        app.setResumeId(resume == null ? "resume-missing" : resume.getId());
        app.setStatus("SUBMITTED");
        app.setCreatedAt(updatedAt.minusHours(1));
        app.setUpdatedAt(updatedAt);
        return app;
    }

    /** 简历内容按 user_id 生成；resume == null 时不注册（模拟简历缺失）。 */
    private ResumeEntity resume(String resumeId, String userId) {
        ResumeEntity resume = new ResumeEntity();
        resume.setId(resumeId);
        resume.setUserId(userId);
        resume.setName("默认简历");
        resume.setContent(contentOf(userId));
        resume.setIsDefault(true);
        return register(resume);
    }

    /** 注册到 resumeMapper：selectById 命中即返回，未注册的返回 null。 */
    private final Map<String, ResumeEntity> resumes = new HashMap<>();

    private ResumeEntity register(ResumeEntity resume) {
        resumes.put(resume.getId(), resume);
        when(resumeMapper.selectById(anyString())).thenAnswer(invocation ->
                resumes.get(invocation.getArgument(0)));
        return resume;
    }

    private static String contentOf(String userId) {
        String skills = switch (userId) {
            case "u-cand-3" -> "[\"自动化测试\",\"Selenium\",\"回归测试\"]";
            case "u-cand-2" -> "[\"Java\",\"Spring Boot\",\"MySQL\"]";
            default -> "[\"React\",\"TypeScript\",\"Vue\"]";
        };
        String summary = switch (userId) {
            case "u-cand-3" -> "专注自动化测试与质量保障，负责回归测试平台建设。";
            case "u-cand-2" -> "6 年后端开发经验，主攻 Java 服务端与高并发架构设计。";
            default -> REACT_SUMMARY;
        };
        String experience = switch (userId) {
            case "u-cand-3" -> "{\"company\":\"某科技\",\"title\":\"测试开发工程师\",\"description\":\"搭建自动化回归测试平台，覆盖核心链路。\"}";
            case "u-cand-2" -> "{\"company\":\"某电商\",\"title\":\"高级后端工程师\",\"description\":\"负责订单与营销核心链路，引入缓存与消息队列削峰。\"}";
            default -> "{\"company\":\"某互联网科技有限公司\",\"title\":\"前端开发工程师\",\"description\":\"负责 B 端管理后台与微信小程序开发，主导前端工程化改造。\"}";
        };
        String project = switch (userId) {
            case "u-cand-3" -> "{\"name\":\"自动化回归测试平台\",\"role\":\"测试负责人\",\"description\":\"搭建自动化回归测试平台，覆盖核心链路。\"}";
            case "u-cand-2" -> "{\"name\":\"秒杀系统高并发改造\",\"role\":\"后端负责人\",\"description\":\"引入缓存与消息队列削峰，QPS 大幅提升。\"}";
            default -> "{\"name\":\"招聘管理系统前端重构\",\"role\":\"前端负责人\",\"description\":\"基于 React + TypeScript + Vite 重构招聘管理后台。\"}";
        };
        return "{\"basics\":{\"name\":\"候选人\",\"city\":\"上海\",\"summary\":\"" + summary + "\"},"
                + "\"educations\":[],"
                + "\"experiences\":[" + experience + "],"
                + "\"projects\":[" + project + "],"
                + "\"skills\":" + skills + "}";
    }

    private static String name(String userId) {
        return switch (userId) {
            case "u-cand-1" -> "林小满";
            case "u-cand-2" -> "周叙白";
            case "u-cand-3" -> "沈禾";
            default -> "候选人";
        };
    }

    private static String expect(String userId) {
        return switch (userId) {
            case "u-cand-1" -> "前端开发";
            case "u-cand-2" -> "后端开发";
            case "u-cand-3" -> "测试";
            default -> "—";
        };
    }

    private static UserProfileEntity profile() {
        UserProfileEntity profile = new UserProfileEntity();
        profile.setUserId("u-cand-1");
        profile.setName("林小满");
        profile.setExpectSub("前端开发");
        profile.setExpectCity("上海");
        profile.setWorkYears(3);
        profile.setEdu("本科");
        return profile;
    }

    private static JobEntity job(String id) {
        JobEntity job = new JobEntity();
        job.setId(id);
        job.setCompanyId("co-1");
        job.setTitle("job-1".equals(id) ? "前端工程师" : id + "-职位");
        return job;
    }

    private static void manager() {
        UserContext.set(new UserContext.CurrentUser("u-hr-1", "HR", "co-1", "jti",
                Instant.now().plusSeconds(3600)));
    }

    private static void seeker() {
        UserContext.set(new UserContext.CurrentUser("u-seeker-1", "JOB_SEEKER", null, "jti",
                Instant.now().plusSeconds(3600)));
    }
}
