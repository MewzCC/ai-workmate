package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.dto.ObservabilityChartPreference;
import com.aiworkmate.dto.ObservabilityExportRequest;
import com.aiworkmate.dto.ObservabilityPreferenceResponse;
import com.aiworkmate.dto.PlatformObservabilityResponse;
import com.aiworkmate.dto.RuntimeLogStatsResponse;
import com.aiworkmate.service.BusinessAuditService;
import com.aiworkmate.service.ObservabilityPreferenceService;
import com.aiworkmate.service.PlatformObservabilityService;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ObservabilityExportServiceImplTest {
    @Mock UserAccessService accessService;
    @Mock PlatformObservabilityService observabilityService;
    @Mock ObservabilityPreferenceService preferenceService;
    @Mock BusinessAuditService auditService;
    private ObservabilityExportServiceImpl service;

    @BeforeEach void setUp() {
        service = new ObservabilityExportServiceImpl(accessService, observabilityService,
                preferenceService, auditService);
    }

    @Test void exportsOnlySavedVisibleSeriesAndAuditsTenant() {
        when(accessService.resolveActiveUser(7L)).thenReturn(access(true));
        when(preferenceService.preferences(7L)).thenReturn(new ObservabilityPreferenceResponse(List.of(
                new ObservabilityChartPreference("source", "source", "", "bar", List.of("HUMAN"), "normal", "auto"))));
        when(observabilityService.overview(7L, "7d")).thenReturn(overview());

        var response = service.export(7L, new ObservabilityExportRequest("7d", "source"));
        assertThat(response.rowCount()).isEqualTo(1);
        assertThat(response.content()).contains("\"HUMAN\",\"\",\"2\"");
        assertThat(response.content()).doesNotContain("AGENT");
        verify(auditService).record(9L, 7L, "PLATFORM_OBSERVABILITY", "source",
                "EXPORT", "SUCCESS", "range=7d,kind=source,rows=1");
    }

    @Test void rejectsMissingExportPermissionBeforeReadingData() {
        when(accessService.resolveActiveUser(7L)).thenReturn(access(false));
        assertThatThrownBy(() -> service.export(7L, new ObservabilityExportRequest("7d", "source")))
                .isInstanceOf(BusinessException.class);
        verify(observabilityService, never()).overview(any(), any());
    }

    @Test void rejectsUnknownChartAndUnboundedRange() {
        when(accessService.resolveActiveUser(7L)).thenReturn(access(true));
        assertThatThrownBy(() -> service.export(7L, new ObservabilityExportRequest("365d", "source")))
                .isInstanceOf(BusinessException.class);
        when(preferenceService.preferences(7L)).thenReturn(new ObservabilityPreferenceResponse(List.of(
                new ObservabilityChartPreference("source", "source", "", "bar", List.of("HUMAN"), "normal", "auto"))));
        assertThatThrownBy(() -> service.export(7L, new ObservabilityExportRequest("7d", "other")))
                .isInstanceOf(BusinessException.class);
        verify(observabilityService, never()).overview(any(), any());
    }

    @Test void escapesFormulaLikeErrorCodesInCsv() {
        when(accessService.resolveActiveUser(7L)).thenReturn(access(true));
        when(preferenceService.preferences(7L)).thenReturn(new ObservabilityPreferenceResponse(List.of(
                new ObservabilityChartPreference("error", "error", "", "bar", List.of(), "normal", "auto"))));
        var unsafe = new PlatformObservabilityResponse(overview().from(), overview().to(), "day", overview().stats(),
                null, List.of(), List.of(), List.of(new PlatformObservabilityResponse.CategoryCount("=SUM(1)", 1L)));
        when(observabilityService.overview(7L, "7d")).thenReturn(unsafe);
        assertThat(service.export(7L, new ObservabilityExportRequest("7d", "error")).content())
                .contains("\"'=SUM(1)\"");
    }

    private PlatformObservabilityResponse overview() {
        return new PlatformObservabilityResponse(LocalDateTime.parse("2026-09-16T00:00:00"),
                LocalDateTime.parse("2026-09-23T00:00:00"), "day",
                new RuntimeLogStatsResponse(3L, 2L, 1L, 0L, 10L), null, List.of(),
                List.of(new PlatformObservabilityResponse.CategoryCount("HUMAN", 2L),
                        new PlatformObservabilityResponse.CategoryCount("AGENT", 1L)), List.of());
    }

    private ResolvedUserAccess access(boolean export) {
        List<String> permissions = export
                ? List.of("route:platform-observability", "runtime-log:read", "data:export")
                : List.of("route:platform-observability", "runtime-log:read");
        return new ResolvedUserAccess(7L, "admin", 9L, "SYSTEM_ADMIN",
                List.of("SYSTEM_ADMIN"), permissions, List.of("TENANT"), 1L);
    }
}
