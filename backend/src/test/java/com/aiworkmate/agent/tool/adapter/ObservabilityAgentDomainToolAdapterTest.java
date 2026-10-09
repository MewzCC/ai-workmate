package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.ObservabilityToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.dto.ObservabilityChartPreference;
import com.aiworkmate.dto.ObservabilityPreferenceRequest;
import com.aiworkmate.dto.ObservabilityPreferenceResponse;
import com.aiworkmate.dto.ObservabilityThresholdPreference;
import com.aiworkmate.service.ObservabilityPreferenceService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ObservabilityAgentDomainToolAdapterTest {
    @Test
    void mergesAllSourcesWithoutLosingAnyOfTheThirtyDayTimeline() {
        var domain = mock(com.aiworkmate.service.PlatformObservabilityService.class);
        var actor = new ToolActorContext(9L, 7L, 20L, 30L, 1, "trace");
        var to = java.time.LocalDateTime.of(2026, 10, 9, 12, 0);
        var timeline = java.util.stream.IntStream.range(0, 31).boxed().flatMap(i ->
                java.util.stream.Stream.of(
                        new com.aiworkmate.dto.PlatformObservabilityResponse.TimelinePoint(to.minusDays(i), "HUMAN", 2L, 1L, 0L),
                        new com.aiworkmate.dto.PlatformObservabilityResponse.TimelinePoint(to.minusDays(i), "AGENT", 3L, 0L, 1L)))
                .toList();
        when(domain.overview(7L, "30d")).thenReturn(new com.aiworkmate.dto.PlatformObservabilityResponse(
                to.minusDays(30), to, "day", new com.aiworkmate.dto.RuntimeLogStatsResponse(155L, 93L, 31L, 31L, 20L),
                100L, timeline,
                List.of(new com.aiworkmate.dto.PlatformObservabilityResponse.CategoryCount("HUMAN", 62L)),
                java.util.stream.IntStream.range(0, 9).mapToObj(i ->
                        new com.aiworkmate.dto.PlatformObservabilityResponse.CategoryCount("ERROR_" + i, 1L)).toList()));

        var result = new ObservabilityAgentDomainToolAdapter(mock(ObservabilityPreferenceService.class), domain)
                .overview(actor, new ObservabilityToolPort.OverviewQuery("30d"));
        assertThat(result.timeline()).hasSize(31).allSatisfy(point -> {
            assertThat(point.total()).isEqualTo(5);
            assertThat(point.failed()).isEqualTo(1);
            assertThat(point.blocked()).isEqualTo(1);
        });
        assertThat(result.timeline().stream().mapToLong(ObservabilityToolPort.Timeline::total).sum()).isEqualTo(155);
        assertThat(result.timeline().get(0).bucket()).isEqualTo(to.minusDays(30).toString());
        assertThat(result.errorCodes()).hasSize(8);
        assertThat(result.categoriesTruncated()).isTrue();
        verify(domain).overview(7L, "30d");
    }

    @Test
    void unexpectedUnboundedTimelineFailsRatherThanReturningAnIncompleteSuccess() {
        var domain = mock(com.aiworkmate.service.PlatformObservabilityService.class);
        var actor = new ToolActorContext(9L, 7L, 20L, 30L, 1, "trace");
        var to = java.time.LocalDateTime.of(2026, 10, 9, 12, 0);
        when(domain.overview(7L, "30d")).thenReturn(new com.aiworkmate.dto.PlatformObservabilityResponse(
                to.minusDays(30), to, "day", new com.aiworkmate.dto.RuntimeLogStatsResponse(32L, 32L, 0L, 0L, 10L),
                10L, java.util.stream.IntStream.range(0, 32).mapToObj(i ->
                new com.aiworkmate.dto.PlatformObservabilityResponse.TimelinePoint(to.minusDays(i), "AGENT", 1L, 0L, 0L)).toList(),
                List.of(), List.of()));
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                new ObservabilityAgentDomainToolAdapter(mock(ObservabilityPreferenceService.class), domain)
                        .overview(actor, new ObservabilityToolPort.OverviewQuery("30d")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void delegatesChartsAndThresholdsToLivePermissionCheckedService() {
        ObservabilityPreferenceService service = mock(ObservabilityPreferenceService.class);
        var adapter = new ObservabilityAgentDomainToolAdapter(service,
                mock(com.aiworkmate.service.PlatformObservabilityService.class));
        var actor = new ToolActorContext(9L, 7L, 20L, 30L, 1, "trace");
        var chart = new ObservabilityToolPort.ChartPreference("volume", "volume", "", "line",
                List.of("HUMAN"), "normal", "auto");
        var chartCommand = new ObservabilityToolPort.ChartPreferenceCommand(List.of(chart));
        var chartRequest = new ObservabilityPreferenceRequest(List.of(
                new ObservabilityChartPreference("volume", "volume", "", "line",
                        List.of("HUMAN"), "normal", "auto")));
        when(service.update(7L, chartRequest)).thenReturn(new ObservabilityPreferenceResponse(chartRequest.charts()));
        var thresholdCommand = new ObservabilityToolPort.ThresholdPreferenceCommand(5, null, 1500);
        var thresholdRequest = new ObservabilityThresholdPreference(5, null, 1500);
        when(service.updateThresholds(7L, thresholdRequest)).thenReturn(thresholdRequest);

        assertThat(adapter.updateChartPreferences(actor, chartCommand).charts()).containsExactly(chart);
        assertThat(adapter.updateThresholds(actor, thresholdCommand).failedCount()).isEqualTo(5);
        verify(service).update(7L, chartRequest);
        verify(service).updateThresholds(7L, thresholdRequest);
    }
}
