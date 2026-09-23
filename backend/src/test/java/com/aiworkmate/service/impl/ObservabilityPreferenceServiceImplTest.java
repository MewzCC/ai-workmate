package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.dto.ObservabilityChartPreference;
import com.aiworkmate.dto.ObservabilityPreferenceRequest;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.UserSettingsService;
import com.aiworkmate.service.model.ResolvedUserAccess;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ObservabilityPreferenceServiceImplTest {
    @Mock UserAccessService accessService;
    @Mock UserSettingsService settingsService;
    private ObservabilityPreferenceServiceImpl service;

    @BeforeEach void setUp() {
        service = new ObservabilityPreferenceServiceImpl(accessService, settingsService, new ObjectMapper());
    }

    @Test void savesOrderedChartsAndReadsThemBack() {
        allow();
        List<ObservabilityChartPreference> charts = List.of(
                chart("error", "donut", List.of("TIMEOUT"), "wide"),
                chart("volume", "area", List.of("HUMAN"), "normal"),
                chart("risk", "bar", List.of("failed"), "normal"),
                chart("source", "bar", List.of("HUMAN", "AGENT"), "wide"));
        var saved = service.update(7L, new ObservabilityPreferenceRequest(charts));
        assertThat(saved.charts()).containsExactlyElementsOf(charts);
        org.mockito.ArgumentCaptor<String> value = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(settingsService).setObservabilityChartConfig(eq(7L), value.capture());
        when(settingsService.getObservabilityChartConfig(7L)).thenReturn(value.getValue());
        assertThat(service.preferences(7L).charts()).containsExactlyElementsOf(charts);
    }

    @Test void rejectsUnlistedModeAndDuplicateChart() {
        allow();
        var charts = List.of(chart("volume", "pie", List.of("HUMAN"), "normal"),
                chart("volume", "line", List.of("HUMAN"), "normal"),
                chart("source", "bar", List.of("HUMAN"), "normal"),
                chart("error", "bar", List.of(), "normal"));
        assertThatThrownBy(() -> service.update(7L, new ObservabilityPreferenceRequest(charts)))
                .isInstanceOf(BusinessException.class);
        verify(settingsService, never()).setObservabilityChartConfig(eq(7L), anyString());
    }

    @Test void deniesPermissionRevocationBeforeReadOrWrite() {
        when(accessService.resolveActiveUser(7L)).thenReturn(access(List.of("route:platform-observability")));
        assertThatThrownBy(() -> service.preferences(7L)).isInstanceOf(BusinessException.class);
        verify(settingsService, never()).getObservabilityChartConfig(7L);
    }

    @Test void corruptedStoredValueFallsBackToDefaults() {
        allow();
        when(settingsService.getObservabilityChartConfig(7L)).thenReturn("{bad json");
        assertThat(service.preferences(7L).charts()).extracting(ObservabilityChartPreference::id)
                .containsExactly("volume", "risk", "source", "error");
    }

    @Test void structurallyIncompleteSavedValueAlsoFallsBackToDefaults() {
        allow();
        when(settingsService.getObservabilityChartConfig(7L)).thenReturn("[{\"id\":\"volume\"}]");
        assertThat(service.preferences(7L).charts()).extracting(ObservabilityChartPreference::id)
                .containsExactly("volume", "risk", "source", "error");
    }

    private void allow() {
        when(accessService.resolveActiveUser(7L)).thenReturn(access(List.of(
                "route:platform-observability", "runtime-log:read")));
    }

    private ResolvedUserAccess access(List<String> permissions) {
        return new ResolvedUserAccess(7L, "user", 9L, "ADMIN", List.of("ADMIN"), permissions,
                List.of("TENANT"), 1L);
    }

    private ObservabilityChartPreference chart(String id, String mode, List<String> content, String size) {
        return new ObservabilityChartPreference(id, mode, content, size);
    }
}
