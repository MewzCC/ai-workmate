package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.dto.DashboardOverviewResponse;
import com.aiworkmate.mapper.DashboardMapper;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.ResolvedUserAccess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceImplTest {
    @Mock DashboardMapper mapper;
    @Mock UserAccessService accessService;
    private DashboardServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DashboardServiceImpl(accessService, mapper);
    }

    @Test
    void aggregatesOnlyAuthenticatedUserAndTenant() {
        when(accessService.resolveActiveUser(7L)).thenReturn(access(List.of("dashboard:read")));
        when(mapper.countPendingTodos(9L, 7L)).thenReturn(3L);
        when(mapper.countOverdueTodos(9L, 7L)).thenReturn(1L);
        when(mapper.countMyApplications(9L, 7L)).thenReturn(5L);
        when(mapper.countUnreadMessages(9L, 7L)).thenReturn(2L);
        when(mapper.selectPendingTodos(9L, 7L)).thenReturn(List.of());
        when(mapper.selectTrends(9L, 7L, 7)).thenReturn(List.of(
                new DashboardOverviewResponse.TrendPoint(LocalDate.now(), 1, 0)));
        when(mapper.selectDistribution(9L, 7L, 7)).thenReturn(List.of());
        when(mapper.selectRecentActivities(9L, 7L, 7)).thenReturn(List.of());

        DashboardOverviewResponse response = service.overview(7L, 7);

        assertThat(response.metrics()).extracting(DashboardOverviewResponse.Metric::value)
                .containsExactly(3L, 1L, 5L, 2L);
        assertThat(response.days()).isEqualTo(7);
        verify(mapper).selectPendingTodos(9L, 7L);
        verify(mapper).selectTrends(9L, 7L, 7);
    }

    @Test
    void deniesUsersWithoutDashboardPermissionBeforeQueryingData() {
        when(accessService.resolveActiveUser(7L)).thenReturn(access(List.of("route:dashboard")));

        assertThatThrownBy(() -> service.overview(7L, 7)).isInstanceOf(BusinessException.class);

        verify(mapper, never()).countPendingTodos(9L, 7L);
    }

    @Test
    void rejectsInvalidDateRangeBeforeResolvingAccess() {
        assertThatThrownBy(() -> service.overview(7L, 31)).isInstanceOf(BusinessException.class);
        verify(accessService, never()).resolveActiveUser(7L);
    }

    private ResolvedUserAccess access(List<String> permissions) {
        return new ResolvedUserAccess(7L, "employee", 9L, "EMPLOYEE",
                List.of("EMPLOYEE"), permissions, List.of("SELF"), 1L);
    }
}
