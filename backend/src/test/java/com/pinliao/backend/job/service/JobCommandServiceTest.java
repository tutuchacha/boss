package com.pinliao.backend.job.service;

import com.pinliao.backend.auth.context.UserContext;
import com.pinliao.backend.auth.entity.CompanyEntity;
import com.pinliao.backend.auth.entity.HrProfileEntity;
import com.pinliao.backend.auth.mapper.CompanyMapper;
import com.pinliao.backend.auth.mapper.HrProfileMapper;
import com.pinliao.backend.common.exception.BusinessException;
import com.pinliao.backend.job.dto.JobRequests;
import com.pinliao.backend.job.entity.JobEntity;
import com.pinliao.backend.job.mapper.JobMapper;
import com.pinliao.backend.job.view.JobViews;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T3b：职位写服务（§6.5~§6.8）单元测试。
 *
 * <p>语义基线 = mock {@code src/api/mock/hrJobs.ts}：非本公司 / 不存在 / 已软删 → 40310；
 * 新建 OPEN + job-new-{seq} + 统计清零；更新保留发布者 / 发布时间 / 统计。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JobCommandServiceTest {

    @Mock
    private JobMapper jobMapper;
    @Mock
    private CompanyMapper companyMapper;
    @Mock
    private HrProfileMapper hrProfileMapper;
    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOps;

    private JobCommandService service;

    @BeforeEach
    void setUp() {
        service = new JobCommandService(jobMapper, companyMapper, hrProfileMapper, redis);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString())).thenReturn(1L);
        UserContext.set(new UserContext.CurrentUser("u-hr-1", "HR", "co-acgiw7", "jti",
                Instant.now().plusSeconds(3600)));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    // ------------------------------------------------------------------
    // §6.5 发布
    // ------------------------------------------------------------------

    @Test
    void 发布职位_成功_递增id与发布态() {
        when(jobMapper.nextNewJobSeq()).thenReturn(7L);
        when(jobMapper.selectTenantMaxJobs("co-acgiw7")).thenReturn(null);
        when(jobMapper.selectCount(any())).thenReturn(5L);
        HrProfileEntity profile = new HrProfileEntity();
        profile.setId("hr-x2270n");
        profile.setUserId("u-hr-1");
        profile.setName("陈女士");
        profile.setTitle("技术招聘经理");
        when(hrProfileMapper.selectOne(any())).thenReturn(profile);
        CompanyEntity company = new CompanyEntity();
        company.setId("co-acgiw7");
        company.setName("云杉科技");
        company.setStage("C轮");
        company.setSize("100-499人");
        when(companyMapper.selectById("co-acgiw7")).thenReturn(company);

        JobViews.HrRecord record = service.create(validInput());

        assertEquals("job-new-7", record.id());
        assertEquals("OPEN", record.status());
        assertEquals("co-acgiw7", record.companyId());
        assertEquals("云杉科技", record.company().name());
        assertEquals("hr-x2270n", record.hr().id());
        assertEquals("陈女士", record.hr().name());
        assertEquals("20-35K", record.salary());
        assertNotNull(record.publishedAt());
        assertEquals(0, record.viewCount());
        assertEquals(0, record.applyCount());
        assertFalse(record.deleted());

        ArgumentCaptor<JobEntity> captor = ArgumentCaptor.forClass(JobEntity.class);
        verify(jobMapper).insert(captor.capture());
        JobEntity inserted = captor.getValue();
        assertEquals("job-new-7", inserted.getId());
        assertEquals("co-acgiw7", inserted.getCompanyId());
        assertEquals("OPEN", inserted.getStatus());
        assertEquals(0, inserted.getViewCount());
        assertEquals(0, inserted.getApplyCount());
        assertEquals(2, inserted.getTags().size());
    }

    @Test
    void 发布职位_求职者被拒40301() {
        UserContext.set(new UserContext.CurrentUser("u-seeker-1", "JOB_SEEKER", null, "jti",
                Instant.now().plusSeconds(3600)));

        BusinessException e = assertThrows(BusinessException.class, () -> service.create(validInput()));
        assertEquals(40301, e.getCode());
    }

    @Test
    void 发布职位_缺少企业上下文被拒40301() {
        UserContext.set(new UserContext.CurrentUser("u-hr-1", "HR", null, "jti",
                Instant.now().plusSeconds(3600)));

        BusinessException e = assertThrows(BusinessException.class, () -> service.create(validInput()));
        assertEquals(40301, e.getCode());
    }

    @Test
    void 发布职位_配额已满42211() {
        when(jobMapper.selectTenantMaxJobs("co-acgiw7")).thenReturn(3);
        when(jobMapper.selectCount(any())).thenReturn(3L);

        BusinessException e = assertThrows(BusinessException.class, () -> service.create(validInput()));
        assertEquals(42211, e.getCode());
    }

    @Test
    void 发布职位_超过每分钟限流42901() {
        when(valueOps.increment(anyString())).thenReturn(11L);

        BusinessException e = assertThrows(BusinessException.class, () -> service.create(validInput()));
        assertEquals(42901, e.getCode());
    }

    @Test
    void 发布职位_标题非法42201() {
        JobRequests.Upsert input = new JobRequests.Upsert("A", 20, 35, "上海", null,
                "3-5年", "本科", "互联网", "前端开发", List.of(), null, null, List.of());

        BusinessException e = assertThrows(BusinessException.class, () -> service.create(input));
        assertEquals(42201, e.getCode());
    }

    @Test
    void 发布职位_薪资倒挂42201() {
        JobRequests.Upsert input = new JobRequests.Upsert("前端工程师", 40, 30, "上海", null,
                "3-5年", "本科", "互联网", "前端开发", List.of(), null, null, List.of());

        BusinessException e = assertThrows(BusinessException.class, () -> service.create(input));
        assertEquals(42201, e.getCode());
    }

    @Test
    void 发布职位_标签超过8个42201() {
        JobRequests.Upsert input = new JobRequests.Upsert("前端工程师", 20, 35, "上海", null,
                "3-5年", "本科", "互联网", "前端开发",
                List.of("1", "2", "3", "4", "5", "6", "7", "8", "9"), null, null, List.of());

        BusinessException e = assertThrows(BusinessException.class, () -> service.create(input));
        assertEquals(42201, e.getCode());
    }

    // ------------------------------------------------------------------
    // §6.6 更新
    // ------------------------------------------------------------------

    @Test
    void 更新职位_部分字段生效且保留统计与发布时间() {
        JobEntity existing = existingJob();
        when(jobMapper.selectById("job-1")).thenReturn(existing);

        JobRequests.Upsert patch = new JobRequests.Upsert("高级前端工程师", 25, 40, null, null,
                null, null, null, null, null, null, null, null);

        JobViews.HrRecord record = service.update("job-1", patch);

        assertEquals("高级前端工程师", record.title());
        assertEquals("25-40K", record.salary());
        assertEquals("上海", record.city());
        assertEquals("徐汇区", record.area());
        assertEquals(existing.getPublishedAt().toInstant(ZoneOffset.UTC), record.publishedAt());
        assertEquals(12, record.viewCount());
        assertEquals(3, record.applyCount());
        assertEquals("hr-x2270n", record.hr().id());
        verify(jobMapper).updateById(existing);
    }

    @Test
    void 更新职位_非本公司40310() {
        JobEntity other = existingJob();
        other.setCompanyId("co-other");
        when(jobMapper.selectById("job-1")).thenReturn(other);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.update("job-1", new JobRequests.Upsert("标题", null, null, null, null,
                        null, null, null, null, null, null, null, null)));
        assertEquals(40310, e.getCode());
    }

    @Test
    void 更新职位_不存在40310() {
        when(jobMapper.selectById("job-404")).thenReturn(null);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.update("job-404", new JobRequests.Upsert("标题", null, null, null, null,
                        null, null, null, null, null, null, null, null)));
        assertEquals(40310, e.getCode());
    }

    @Test
    void 更新职位_已软删40310() {
        JobEntity deleted = existingJob();
        deleted.setDeletedAt(LocalDateTime.now(ZoneOffset.UTC));
        when(jobMapper.selectById("job-1")).thenReturn(deleted);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.update("job-1", new JobRequests.Upsert("标题", null, null, null, null,
                        null, null, null, null, null, null, null, null)));
        assertEquals(40310, e.getCode());
    }

    // ------------------------------------------------------------------
    // §6.7 关闭 / 重开、§6.8 软删
    // ------------------------------------------------------------------

    @Test
    void 关闭与重新开放职位() {
        JobEntity job = existingJob();
        when(jobMapper.selectById("job-1")).thenReturn(job);

        assertEquals("CLOSED", service.close("job-1").status());
        assertEquals("OPEN", service.reopen("job-1").status());
    }

    @Test
    void 删除职位_置软删且重复删除幂等() {
        JobEntity job = existingJob();
        when(jobMapper.selectById("job-1")).thenReturn(job);

        service.delete("job-1");
        assertNotNull(job.getDeletedAt());
        verify(jobMapper, times(1)).updateById(job);

        service.delete("job-1");
        verify(jobMapper, times(1)).updateById(job);
    }

    @Test
    void 删除职位_不存在或非本公司40310() {
        when(jobMapper.selectById("job-404")).thenReturn(null);
        BusinessException missing = assertThrows(BusinessException.class, () -> service.delete("job-404"));
        assertEquals(40310, missing.getCode());

        JobEntity other = existingJob();
        other.setCompanyId("co-other");
        when(jobMapper.selectById("job-1")).thenReturn(other);
        BusinessException foreign = assertThrows(BusinessException.class, () -> service.delete("job-1"));
        assertEquals(40310, foreign.getCode());
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    private static JobRequests.Upsert validInput() {
        return new JobRequests.Upsert("前端工程师", 20, 35, "上海", "浦东新区", "3-5年", "本科",
                "互联网", "前端开发", List.of("React", "TypeScript"),
                "负责业务系统前端开发。", "熟悉 React 与 TypeScript。", List.of("五险一金", "带薪年假"));
    }

    private static JobEntity existingJob() {
        JobEntity job = new JobEntity();
        job.setId("job-1");
        job.setCompanyId("co-acgiw7");
        job.setHrId("hr-x2270n");
        job.setHrName("陈女士");
        job.setHrTitle("技术招聘经理");
        job.setTitle("前端开发工程师");
        job.setSalaryMin(20);
        job.setSalaryMax(35);
        job.setCity("上海");
        job.setArea("徐汇区");
        job.setExp("3-5年");
        job.setEdu("本科");
        job.setCat("互联网");
        job.setSub("前端开发");
        job.setTags(List.of("React"));
        job.setWelfare(List.of("五险一金"));
        job.setStatus(JobCommandService.STATUS_OPEN);
        job.setPublishedAt(LocalDateTime.now(ZoneOffset.UTC).minusDays(3));
        job.setViewCount(12);
        job.setApplyCount(3);
        return job;
    }
}
