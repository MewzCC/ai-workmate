package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.dto.DashboardOverviewResponse;
import com.aiworkmate.dto.DashboardExportRequest;
import com.aiworkmate.dto.DashboardExportResponse;
import com.aiworkmate.mapper.DashboardMapper;
import com.aiworkmate.service.BusinessAuditService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.ResolvedUserAccess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
    @Mock BusinessAuditService auditService;
    private DashboardServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DashboardServiceImpl(accessService, mapper, auditService);
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

    @Test
    void exportsAtMostScopedRowsAndNeutralizesSpreadsheetFormulas() {
        when(accessService.resolveActiveUser(7L)).thenReturn(access(List.of("dashboard:read", "data:export")));
        LocalDate from = LocalDate.of(2026, 9, 6);
        LocalDate to = LocalDate.of(2026, 9, 12);
        when(mapper.selectExportRows(9L, 7L, from.atStartOfDay(), to.plusDays(1).atStartOfDay(), "risk"))
                .thenReturn(List.of(new DashboardMapper.DashboardExportRow(
                        91L, "=SUM(A1:A2)", "+external", "LEAVE_APPLICATION",
                        LocalDateTime.of(2026, 9, 12, 9, 0), null, "PENDING", false)));

        DashboardExportResponse response = service.export(7L, new DashboardExportRequest(from, to, " risk "));

        assertThat(response.rowCount()).isEqualTo(1);
        assertThat(response.content()).startsWith("\uFEFFtaskId");
        assertThat(response.content()).contains("\"'=SUM(A1:A2)\"", "\"'+external\"");
        verify(auditService).record(9L, 7L, "DASHBOARD", "overview", "EXPORT", "SUCCESS",
                "rows=1,from=2026-09-06,to=2026-09-12");
    }

    @Test
    void deniesExportWithoutLivePermissionBeforeQueryingRows() {
        when(accessService.resolveActiveUser(7L)).thenReturn(access(List.of("dashboard:read")));
        DashboardExportRequest request = new DashboardExportRequest(LocalDate.now(), LocalDate.now(), null);
        assertThatThrownBy(() -> service.export(7L, request)).isInstanceOf(BusinessException.class);
        verify(mapper, never()).selectExportRows(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private ResolvedUserAccess access(List<String> permissions) {
        return new ResolvedUserAccess(7L, "employee", 9L, "EMPLOYEE",
                List.of("EMPLOYEE"), permissions, List.of("SELF"), 1L);
    }
}
