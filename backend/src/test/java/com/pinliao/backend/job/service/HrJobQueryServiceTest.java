package com.pinliao.backend.job.service;

import com.pinliao.backend.auth.context.UserContext;
import com.pinliao.backend.auth.entity.CompanyEntity;
import com.pinliao.backend.auth.mapper.CompanyMapper;
import com.pinliao.backend.common.exception.BusinessException;
import com.pinliao.backend.job.entity.JobEntity;
import com.pinliao.backend.job.mapper.JobMapper;
import com.pinliao.backend.job.view.JobViews;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * T8：§4.3 招聘者职位查询单元测试。
 *
 * <p>重点覆盖：HR 视角可见 CLOSED（与公开浏览口径的区别）、status / q / hrId / limit 筛选、
 * 角色守卫（40301）与详情缺失（40410）。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HrJobQueryServiceTest {

    @Mock
    private JobMapper jobMapper;
    @Mock
    private CompanyMapper companyMapper;

    private HrJobQueryService service;

    @BeforeEach
    void setUp() {
        service = new HrJobQueryService(jobMapper, companyMapper);
        manager();
        CompanyEntity company = new CompanyEntity();
        company.setId("co-1");
        company.setName("云杉科技");
        when(companyMapper.selectById(anyString())).thenReturn(company);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void 我发布的职位_含已关闭且按状态与关键字筛选() {
        when(jobMapper.selectByCompanyAnyStatus("co-1")).thenReturn(new ArrayList<>(List.of(
                job("job-1", "前端工程师", "OPEN", "hr-1"),
                job("job-2", "后端工程师", "CLOSED", "hr-1"),
                job("job-3", "测试工程师", "CLOSED", "hr-2"))));

        List<JobViews.HrRecord> all = service.list(null, null, null, null);
        assertEquals(3, all.size(), "HR 视角必须看到 CLOSED 职位");

        List<JobViews.HrRecord> closed = service.list("CLOSED", null, null, null);
        assertEquals(List.of("job-2", "job-3"), closed.stream().map(JobViews.HrRecord::id).toList());

        List<JobViews.HrRecord> mine = service.list(null, null, "hr-2", null);
        assertEquals(List.of("job-3"), mine.stream().map(JobViews.HrRecord::id).toList());

        List<JobViews.HrRecord> searched = service.list(null, "后端", null, null);
        assertEquals(List.of("job-2"), searched.stream().map(JobViews.HrRecord::id).toList());

        assertEquals(1, service.list(null, null, null, 1).size(), "limit 作为上限保护");
    }

    @Test
    void 我发布的职位_状态非法报42201非招聘者报40301() {
        BusinessException invalid = assertThrows(BusinessException.class,
                () -> service.list("ARCHIVED", null, null, null));
        assertEquals(42201, invalid.getCode());

        seeker();
        BusinessException forbidden = assertThrows(BusinessException.class,
                () -> service.list(null, null, null, null));
        assertEquals(40301, forbidden.getCode());
    }

    @Test
    void 单个职位_HR可见已关闭_缺失报40410() {
        JobEntity closed = job("job-2", "后端工程师", "CLOSED", "hr-1");
        when(jobMapper.selectForCompany("job-2", "co-1")).thenReturn(closed);
        JobViews.HrRecord record = service.detail("job-2");
        assertEquals("CLOSED", record.status());
        assertEquals("co-1", record.companyId());
        assertTrue(record.salary().endsWith("K"));

        when(jobMapper.selectForCompany("job-404", "co-1")).thenReturn(null);
        BusinessException notFound = assertThrows(BusinessException.class, () -> service.detail("job-404"));
        assertEquals(40410, notFound.getCode());
    }

    // ------------------------------------------------------------------

    private static JobEntity job(String id, String title, String status, String hrId) {
        JobEntity job = new JobEntity();
        job.setId(id);
        job.setCompanyId("co-1");
        job.setHrId(hrId);
        job.setHrName("陈女士");
        job.setHrTitle("HRBP");
        job.setTitle(title);
        job.setSalaryMin(20);
        job.setSalaryMax(30);
        job.setCity("上海");
        job.setExp("3-5年");
        job.setEdu("本科");
        job.setCat("技术");
        job.setSub("前端开发");
        job.setTags(List.of("React"));
        job.setWelfare(List.of("五险一金"));
        job.setStatus(status);
        job.setViewCount(1);
        job.setApplyCount(2);
        job.setPublishedAt(LocalDateTime.now(ZoneOffset.UTC).minusDays(3));
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
