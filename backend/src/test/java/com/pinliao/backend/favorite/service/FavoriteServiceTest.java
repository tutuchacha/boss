package com.pinliao.backend.favorite.service;

import com.pinliao.backend.auth.context.UserContext;
import com.pinliao.backend.common.api.PageViews;
import com.pinliao.backend.common.exception.BusinessException;
import com.pinliao.backend.favorite.dto.FavoriteRequests;
import com.pinliao.backend.favorite.entity.FavoriteEntity;
import com.pinliao.backend.favorite.mapper.FavoriteMapper;
import com.pinliao.backend.favorite.view.FavoriteViews;
import com.pinliao.backend.job.entity.JobEntity;
import com.pinliao.backend.job.mapper.JobMapper;
import com.pinliao.backend.job.service.JobQueryService;
import com.pinliao.backend.user.UserCodes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** T7：收藏模块（§11.1~§11.3）单元测试。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FavoriteServiceTest {

    @Mock
    private FavoriteMapper favoriteMapper;
    @Mock
    private JobMapper jobMapper;
    @Mock
    private JobQueryService jobQueryService;

    private FavoriteService service;

    @BeforeEach
    void setUp() {
        service = new FavoriteService(favoriteMapper, jobMapper, jobQueryService);
        UserContext.set(new UserContext.CurrentUser("u-seeker-1", "JOB_SEEKER", null,
                "jti", Instant.now().plus(Duration.ofHours(2))));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void addRequiresVisibleJobAndInsertsDeterministicId() {
        JobEntity job = new JobEntity();
        job.setId("job-1");
        when(jobMapper.selectVisibleById("job-1")).thenReturn(job);
        when(favoriteMapper.selectOne(any())).thenReturn(null);

        FavoriteViews.Favorite result = service.add(new FavoriteRequests.AddRequest("job-1"));
        assertEquals("fav-u-seeker-1-job-1", result.id());
        verify(favoriteMapper).insert(any(FavoriteEntity.class));
    }

    @Test
    void addRejectsDuplicateFavorite() {
        when(jobMapper.selectVisibleById("job-1")).thenReturn(new JobEntity());
        when(favoriteMapper.selectOne(any())).thenReturn(new FavoriteEntity());

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.add(new FavoriteRequests.AddRequest("job-1")));
        assertEquals(UserCodes.FAVORITE_EXISTS, e.getCode());
    }

    @Test
    void addRejectsInvisibleJob() {
        when(jobMapper.selectVisibleById("job-closed")).thenReturn(null);
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.add(new FavoriteRequests.AddRequest("job-closed")));
        assertEquals(UserCodes.NOT_FOUND, e.getCode());
    }

    @Test
    void removeIsIdempotent() {
        service.remove("job-1");
        verify(favoriteMapper).delete(any());
    }

    @Test
    void listReturnsCursorPaginationAndTotal() {
        FavoriteEntity f1 = favorite("fav-1", "job-1", "2026-09-10T10:00:00");
        FavoriteEntity f2 = favorite("fav-2", "job-2", "2026-09-09T10:00:00");
        FavoriteEntity f3 = favorite("fav-3", "job-3", "2026-09-08T10:00:00");
        when(favoriteMapper.selectList(any())).thenReturn(List.of(f1, f2, f3));
        when(jobMapper.selectById(any())).thenReturn(new JobEntity());

        PageViews.Page<FavoriteViews.WithJob> first = service.list(null, 2);
        assertEquals(2, first.list().size());
        assertEquals(3, first.pagination().total());
        assertNotNull(first.pagination().nextCursor());

        PageViews.Page<FavoriteViews.WithJob> second = service.list(first.pagination().nextCursor(), 2);
        assertEquals(1, second.list().size());
        assertEquals("fav-3", second.list().get(0).id());
        assertEquals(false, second.pagination().hasMore());
    }

    // ------------------------------------------------------------------

    private static FavoriteEntity favorite(String id, String jobId, String createdAt) {
        FavoriteEntity f = new FavoriteEntity();
        f.setId(id);
        f.setUserId("u-seeker-1");
        f.setJobId(jobId);
        f.setCreatedAt(LocalDateTime.parse(createdAt));
        return f;
    }
}
