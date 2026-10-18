package com.pinliao.backend.job.service;

import com.pinliao.backend.auth.context.UserContext;
import com.pinliao.backend.auth.entity.CompanyEntity;
import com.pinliao.backend.auth.entity.UserProfileEntity;
import com.pinliao.backend.auth.mapper.CompanyMapper;
import com.pinliao.backend.auth.mapper.UserProfileMapper;
import com.pinliao.backend.common.api.ApiCodes;
import com.pinliao.backend.common.exception.BusinessException;
import com.pinliao.backend.job.JobCodes;
import com.pinliao.backend.job.entity.JobEntity;
import com.pinliao.backend.job.mapper.JobMapper;
import com.pinliao.backend.job.view.JobViews;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** T3a 职位公开查询：加权排序 / 游标分页 / 详情 / 相似 / 埋点去重。 */
class JobQueryServiceTest {

    private JobMapper jobMapper;
    private CompanyMapper companyMapper;
    private UserProfileMapper userProfileMapper;
    private StringRedisTemplate redis;
    private JobQueryService service;

    @BeforeEach
    void setUp() {
        jobMapper = mock(JobMapper.class);
        companyMapper = mock(CompanyMapper.class);
        userProfileMapper = mock(UserProfileMapper.class);
        redis = mock(StringRedisTemplate.class);
        service = new JobQueryService(jobMapper, companyMapper, userProfileMapper, redis);
    }

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    // ---------------------------------------------------------------
    // 排序
    // ---------------------------------------------------------------

    @Test
    void recommend_登录求职者_期望岗位加权且同分保持行序() {
        loginSeeker("互联网", "前端开发");

        // 行序 = published_at DESC, id ASC（daysAgo 升序）
        stubVisible(
                job("job-1", "互联网", "后端开发", 0),   // score 1
                job("job-2", "互联网", "前端开发", 1),   // score 2
                job("job-3", "财务", "会计", 2),         // score 0
                job("job-4", "互联网", "前端开发", 3));  // score 2
        JobViews.Page<JobViews.Summary> page = service.list(query("recommend", null, 100));

        assertEquals(List.of("job-2", "job-4", "job-1", "job-3"), ids(page));
        assertEquals(4, page.pagination().total());
        assertFalse(page.pagination().hasMore());
        assertNull(page.pagination().nextCursor());
    }

    @Test
    void recommend_匿名_不查画像且保持发布倒序() {
        stubVisible(job("job-1", "互联网", "后端开发", 0), job("job-2", "财务", "会计", 1));

        JobViews.Page<JobViews.Summary> page = service.list(query("recommend", null, null));

        assertEquals(List.of("job-1", "job-2"), ids(page));
        verifyNoInteractions(userProfileMapper);

        // 摘要字段形状（前端 JobSummary 契约）
        JobViews.Summary first = page.list().get(0);
        assertEquals("20-35K", first.salary());
        assertEquals(0, first.days());
        assertNotNull(first.publishedAt());
        assertEquals("co-1", first.company().id());
        assertEquals("hr-1", first.hr().id());
        assertEquals(List.of("Vue3"), first.tags());
    }

    @Test
    void latest_登录求职者也不加权() {
        loginSeeker("互联网", "前端开发");
        stubVisible(job("job-1", "财务", "会计", 0), job("job-2", "互联网", "前端开发", 1));

        JobViews.Page<JobViews.Summary> page = service.list(query("latest", null, null));

        assertEquals(List.of("job-1", "job-2"), ids(page));
        verify(userProfileMapper, never()).selectOne(any());
    }

    // ---------------------------------------------------------------
    // 分页 / 游标
    // ---------------------------------------------------------------

    @Test
    void 游标分页_逐页取完不重不漏() {
        stubVisible(
                job("job-1", "互联网", "前端开发", 0),
                job("job-2", "互联网", "后端开发", 1),
                job("job-3", "财务", "会计", 2),
                job("job-4", "设计", "UI/UX设计", 3),
                job("job-5", "运营", "用户运营", 4));

        JobViews.Page<JobViews.Summary> p1 = service.list(query("recommend", null, 2));
        assertEquals(List.of("job-1", "job-2"), ids(p1));
        assertTrue(p1.pagination().hasMore());
        assertNotNull(p1.pagination().nextCursor());
        assertEquals(5, p1.pagination().total());

        JobViews.Page<JobViews.Summary> p2 = service.list(query("recommend", p1.pagination().nextCursor(), 2));
        assertEquals(List.of("job-3", "job-4"), ids(p2));
        assertTrue(p2.pagination().hasMore());

        JobViews.Page<JobViews.Summary> p3 = service.list(query("recommend", p2.pagination().nextCursor(), 2));
        assertEquals(List.of("job-5"), ids(p3));
        assertFalse(p3.pagination().hasMore());
        assertNull(p3.pagination().nextCursor());
    }

    @Test
    void 游标分页_加权模式下第二页继续按得分排序() {
        loginSeeker("互联网", "前端开发");
        stubVisible(
                job("job-1", "互联网", "前端开发", 0),  // score 2
                job("job-2", "互联网", "前端开发", 1),  // score 2
                job("job-3", "互联网", "后端开发", 2),  // score 1
                job("job-4", "财务", "会计", 3));       // score 0

        JobViews.Page<JobViews.Summary> p1 = service.list(query("recommend", null, 2));
        assertEquals(List.of("job-1", "job-2"), ids(p1));

        JobViews.Page<JobViews.Summary> p2 = service.list(query("recommend", p1.pagination().nextCursor(), 2));
        assertEquals(List.of("job-3", "job-4"), ids(p2));
    }

    @Test
    void 游标编解码往返() {
        JobEntity job = job("job-7", "互联网", "前端开发", 1);

        JobQueryService.Cursor cursor = JobQueryService.decodeCursor(JobQueryService.encodeCursor(job, 2));

        assertEquals(2, cursor.score());
        assertEquals("job-7", cursor.id());
        assertEquals(job.getPublishedAt().toInstant(ZoneOffset.UTC).toEpochMilli(), cursor.publishedMillis());
    }

    @Test
    void 非法游标返回40001() {
        stubVisible(job("job-1", "互联网", "前端开发", 0));

        BusinessException illegal = assertThrows(BusinessException.class,
                () -> service.list(query("recommend", "!!not-base64!!", null)));
        assertEquals(ApiCodes.PARAM_INVALID, illegal.getCode());

        String notJson = Base64.getUrlEncoder().withoutPadding().encodeToString("nope".getBytes());
        BusinessException broken = assertThrows(BusinessException.class,
                () -> service.list(query("recommend", notJson, null)));
        assertEquals(ApiCodes.PARAM_INVALID, broken.getCode());
    }

    @Test
    void limit_默认20_上限100() {
        assertEquals(20, JobQueryService.clampLimit(null));
        assertEquals(100, JobQueryService.clampLimit(1000));
        assertEquals(1, JobQueryService.clampLimit(0));
        assertEquals(50, JobQueryService.clampLimit(50));
    }

    // ---------------------------------------------------------------
    // 详情 / 相似 / 埋点
    // ---------------------------------------------------------------

    @Test
    void 详情_公司与相似职位_不可见职位40410() {
        JobEntity job = job("job-1", "互联网", "前端开发", 2);
        when(jobMapper.selectVisibleById("job-1")).thenReturn(job);
        when(jobMapper.selectSimilar("job-1", "前端开发", "互联网", 6))
                .thenReturn(List.of(job("job-9", "互联网", "后端开发", 1)));
        when(companyMapper.selectByIds(any())).thenReturn(List.of(company()));

        JobQueryService.DetailResult result = service.detail("job-1");
        JobViews.Detail detail = result.detail();

        assertEquals("20-35K", detail.salary());
        assertEquals("云杉科技", detail.company().name());
        assertEquals("co-1", detail.companyId());
        assertEquals("hr-1", detail.hrId());
        assertEquals(2, detail.days());
        assertEquals(List.of("五险一金"), detail.welfare());
        assertNotNull(detail.responsibilities());
        assertEquals(1, detail.similarJobs().size());
        assertEquals("job-9", detail.similarJobs().get(0).id());
        assertTrue(result.etag().contains("job-1"));

        when(jobMapper.selectVisibleById("missing")).thenReturn(null);
        BusinessException notFound = assertThrows(BusinessException.class, () -> service.detail("missing"));
        assertEquals(JobCodes.JOB_NOT_FOUND, notFound.getCode());
    }

    @Test
    void 相似职位_limit钳制与404() {
        JobEntity job = job("job-1", "互联网", "前端开发", 0);
        when(jobMapper.selectVisibleById("job-1")).thenReturn(job);
        when(jobMapper.selectSimilar(eq("job-1"), eq("前端开发"), eq("互联网"), anyInt()))
                .thenReturn(List.of(job("job-9", "互联网", "后端开发", 1)));

        assertEquals(1, service.similar("job-1", 3).size());
        assertEquals(1, service.similar("job-1", null).size());
        verify(jobMapper).selectSimilar("job-1", "前端开发", "互联网", 3);
        verify(jobMapper).selectSimilar("job-1", "前端开发", "互联网", 6);

        when(jobMapper.selectVisibleById("missing")).thenReturn(null);
        BusinessException notFound = assertThrows(BusinessException.class, () -> service.similar("missing", null));
        assertEquals(JobCodes.JOB_NOT_FOUND, notFound.getCode());
    }

    @Test
    void 浏览埋点_窗口内首个请求累加_重复不累加() {
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true, false);

        service.recordView("job-1", "1.2.3.4");
        service.recordView("job-1", "1.2.3.4");

        verify(jobMapper, times(1)).incrementViewCount("job-1");
        verify(valueOps, times(2))
                .setIfAbsent(eq("pinliao:job:view:job-1:1.2.3.4"), eq("1"), any(Duration.class));
    }

    @Test
    void 浏览埋点_Redis返回空时不累加且无IP不报错() {
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(null);

        service.recordView("job-1", null);

        verify(jobMapper, never()).incrementViewCount(any());
    }

    // ---------------------------------------------------------------
    // 工具
    // ---------------------------------------------------------------

    private static JobQueryService.Query query(String sort, String cursor, Integer limit) {
        return new JobQueryService.Query(null, null, null, null, null, null, sort, cursor, limit);
    }

    private void stubVisible(JobEntity... rows) {
        when(jobMapper.selectVisible(any(), any(), any(), any(), anyInt(), anyInt(), any()))
                .thenReturn(List.of(rows));
    }

    private void loginSeeker(String expectCat, String expectSub) {
        UserContext.set(new UserContext.CurrentUser("u-1", "JOB_SEEKER", null, "jti-1", Instant.now()));
        UserProfileEntity profile = new UserProfileEntity();
        profile.setExpectCat(expectCat);
        profile.setExpectSub(expectSub);
        when(userProfileMapper.selectOne(any())).thenReturn(profile);
    }

    private static List<String> ids(JobViews.Page<JobViews.Summary> page) {
        return page.list().stream().map(JobViews.Summary::id).toList();
    }

    private static JobEntity job(String id, String cat, String sub, long daysAgo) {
        JobEntity job = new JobEntity();
        job.setId(id);
        job.setTitle("职位-" + id);
        job.setCompanyId("co-1");
        job.setHrId("hr-1");
        job.setHrName("陈女士");
        job.setHrTitle("技术招聘经理");
        job.setSalaryMin(20);
        job.setSalaryMax(35);
        job.setCity("上海");
        job.setArea("浦东新区");
        job.setExp("3-5年");
        job.setEdu("本科");
        job.setCat(cat);
        job.setSub(sub);
        job.setTags(List.of("Vue3"));
        job.setWelfare(List.of("五险一金"));
        job.setStatus("OPEN");
        job.setResponsibilities("负责相关系统设计、开发与迭代维护；");
        job.setRequirements("本科及以上学历；");
        job.setPublishedAt(LocalDateTime.now(ZoneOffset.UTC).minusDays(daysAgo));
        job.setViewCount(100);
        job.setApplyCount(5);
        job.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        return job;
    }

    private static CompanyEntity company() {
        CompanyEntity company = new CompanyEntity();
        company.setId("co-1");
        company.setName("云杉科技");
        company.setStage("B轮");
        company.setSize("500-999人");
        return company;
    }
}
