package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.dto.RuntimeLogStatsResponse;
import com.aiworkmate.mapper.RuntimeLogMapper;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.ResolvedUserAccess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformObservabilityServiceImplTest {
    @Mock RuntimeLogMapper mapper;
    @Mock UserAccessService accessService;
    private PlatformObservabilityServiceImpl service;

    @BeforeEach void setUp() { service = new PlatformObservabilityServiceImpl(mapper, accessService); }

    @Test void aggregatesOnlyCurrentTenantWithBoundedHourlyRange() {
        when(accessService.resolveActiveUser(7L)).thenReturn(access(List.of(
                "route:platform-observability", "runtime-log:read")));
        when(mapper.selectStats(eq(9L), eq(null), eq(null), eq(null), eq(null), eq(null), any(), any(), eq(false)))
                .thenReturn(new RuntimeLogStatsResponse(3L, 2L, 1L, 0L, 20L));
        var response = service.overview(7L, "24h");
        assertThat(response.interval()).isEqualTo("hour");
        assertThat(response.stats().total()).isEqualTo(3L);
        assertThat(response.to()).isAfter(response.from());
        verify(mapper).selectTimeline(eq(9L), any(LocalDateTime.class), any(LocalDateTime.class), eq("hour"));
    }

    @Test void rejectsUserWithoutBothPermissions() {
        when(accessService.resolveActiveUser(7L)).thenReturn(access(List.of("route:platform-observability")));
        assertThatThrownBy(() -> service.overview(7L, "7d")).isInstanceOf(BusinessException.class);
        verify(mapper, never()).selectSourceCounts(any(), any(), any());
    }

    @Test void rejectsUnsupportedRangeBeforeDatabaseAccess() {
        when(accessService.resolveActiveUser(7L)).thenReturn(access(List.of(
                "route:platform-observability", "runtime-log:read")));
        assertThatThrownBy(() -> service.overview(7L, "365d")).isInstanceOf(BusinessException.class);
        verify(mapper, never()).selectSourceCounts(any(), any(), any());
    }

    @Test void alternateTimelineIsTenantScopedAndRejectsUnboundedOrInvalidGranularity() {
        when(accessService.resolveActiveUser(7L)).thenReturn(access(List.of(
                "route:platform-observability", "runtime-log:read")));
        LocalDateTime to = LocalDateTime.now();
        LocalDateTime from = to.minusDays(7);
        service.timeline(7L, from, to, "hour");
        verify(mapper).selectTimeline(9L, from, to, "hour");
        assertThatThrownBy(() -> service.timeline(7L, from.minusDays(40), to, "hour"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.timeline(7L, from, to, "minute"))
                .isInstanceOf(BusinessException.class);
    }

    @Test void comparesAdjacentEqualWindowsWithoutCountingBoundaryTwice() {
        when(accessService.resolveActiveUser(7L)).thenReturn(access(List.of(
                "route:platform-observability", "runtime-log:read")));
        when(mapper.selectStats(eq(9L), eq(null), eq(null), eq(null), eq(null), eq(null),
                any(LocalDateTime.class), any(LocalDateTime.class), anyBoolean()))
                .thenReturn(new RuntimeLogStatsResponse(8L, 6L, 2L, 0L, 80L),
                        new RuntimeLogStatsResponse(4L, 3L, 1L, 0L, 120L));
        var response = service.comparison(7L, "24h");
        assertThat(response.current().stats().total()).isEqualTo(8L);
        assertThat(response.previous().stats().total()).isEqualTo(4L);
        assertThat(response.current().from()).isEqualTo(response.previous().to());
        assertThat(response.previous().toExclusive()).isTrue();
        assertThat(response.current().toExclusive()).isFalse();
        assertThat(java.time.Duration.between(response.previous().from(), response.previous().to()))
                .isEqualTo(java.time.Duration.between(response.current().from(), response.current().to()));
        verify(mapper, times(2)).selectStats(eq(9L), eq(null), eq(null), eq(null), eq(null), eq(null),
                any(LocalDateTime.class), any(LocalDateTime.class), anyBoolean());
    }

    @Test void comparisonRejectsUnknownRangeBeforeDatabaseAccess() {
        when(accessService.resolveActiveUser(7L)).thenReturn(access(List.of(
                "route:platform-observability", "runtime-log:read")));
        assertThatThrownBy(() -> service.comparison(7L, "90d")).isInstanceOf(BusinessException.class);
        verify(mapper, never()).selectStats(any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean());
    }

    private ResolvedUserAccess access(List<String> permissions) {
        return new ResolvedUserAccess(7L, "admin", 9L, "SYSTEM_ADMIN",
                List.of("SYSTEM_ADMIN"), permissions, List.of("TENANT"), 1L);
    }
}
