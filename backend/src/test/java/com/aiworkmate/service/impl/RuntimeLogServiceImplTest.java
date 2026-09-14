package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.dto.RuntimeLogDetailResponse;
import com.aiworkmate.dto.RuntimeLogRecordResponse;
import com.aiworkmate.dto.RuntimeLogStatsResponse;
import com.aiworkmate.mapper.RuntimeLogMapper;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.ResolvedUserAccess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuntimeLogServiceImplTest {
    @Mock RuntimeLogMapper mapper;
    @Mock UserAccessService accessService;
    private RuntimeLogServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RuntimeLogServiceImpl(mapper, accessService);
    }

    @Test
    void queriesOnlyCurrentTenantWithinDefaultSevenDayWindow() {
        when(accessService.resolveActiveUser(7L)).thenReturn(access(
                List.of("route:runtime-logs", "runtime-log:read")));
        when(mapper.selectStats(eq(9L), eq("AGENT"), eq("FAILED"), eq("trace-1"), any(), any()))
                .thenReturn(new RuntimeLogStatsResponse(1L, 0L, 1L, 0L, 42L));
        when(mapper.selectPage(eq(9L), eq("AGENT"), eq("FAILED"), eq("trace-1"),
                any(), any(), eq(100), eq(100)))
                .thenReturn(List.of(new RuntimeLogRecordResponse(
                        "AGENT", 4L, "TASK-1", "todo.query", "FAILED", "ALLOW",
                        null, 42L, "operator", "trace-1", "TIMEOUT",
                        LocalDateTime.now(), LocalDateTime.now())));

        var result = service.query(7L, "agent", "failed", " trace-1 ",
                null, null, 2, 500);

        assertThat(result.total()).isOne();
        assertThat(result.page()).isEqualTo(2);
        assertThat(result.size()).isEqualTo(100);
        assertThat(Duration.between(result.from(), result.to()).toDays()).isEqualTo(7);
    }

    @Test
    void rejectsRangesLongerThanThirtyOneDaysBeforeQueryingDatabase() {
        when(accessService.resolveActiveUser(7L)).thenReturn(access(
                List.of("route:runtime-logs", "runtime-log:read")));
        LocalDateTime to = LocalDateTime.now();

        assertThatThrownBy(() -> service.query(7L, null, null, null,
                to.minusDays(32), to, 1, 20)).isInstanceOf(BusinessException.class);

        verify(mapper, never()).selectStats(any(), any(), any(), any(), any(), any());
    }

    @Test
    void requiresBothRouteAndRuntimeReadPermissions() {
        when(accessService.resolveActiveUser(7L)).thenReturn(access(List.of("route:runtime-logs")));

        assertThatThrownBy(() -> service.query(7L, null, null, null,
                null, null, 1, 20)).isInstanceOf(BusinessException.class);

        verify(mapper, never()).selectPage(any(), any(), any(), any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void detailLookupCarriesTenantAndRejectsMissingRecord() {
        when(accessService.resolveActiveUser(7L)).thenReturn(access(
                List.of("route:runtime-logs", "runtime-log:read")));
        when(mapper.selectDetail(9L, "INTEGRATION", 88L)).thenReturn(null);

        assertThatThrownBy(() -> service.detail(7L, "integration", 88L))
                .isInstanceOf(BusinessException.class);

        verify(mapper).selectDetail(9L, "INTEGRATION", 88L);
    }

    private ResolvedUserAccess access(List<String> permissions) {
        return new ResolvedUserAccess(7L, "admin", 9L, "SYSTEM_ADMIN",
                List.of("SYSTEM_ADMIN"), permissions, List.of("TENANT"), 1L);
    }
}
