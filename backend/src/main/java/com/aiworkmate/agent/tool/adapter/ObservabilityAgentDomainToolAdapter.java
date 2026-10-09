package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.ObservabilityToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.dto.ObservabilityChartPreference;
import com.aiworkmate.dto.ObservabilityPreferenceRequest;
import com.aiworkmate.dto.ObservabilityThresholdPreference;
import com.aiworkmate.service.ObservabilityPreferenceService;
import com.aiworkmate.service.PlatformObservabilityService;
import com.aiworkmate.dto.PlatformObservabilityResponse;
import lombok.RequiredArgsConstructor;

@LocalAgentDomainAdapter
@RequiredArgsConstructor
public final class ObservabilityAgentDomainToolAdapter implements ObservabilityToolPort {
    private final ObservabilityPreferenceService preferenceService;
    private final PlatformObservabilityService observabilityService;

    @Override
    public OverviewResult overview(ToolActorContext context, OverviewQuery query) {
        var response = observabilityService.overview(context.userId(), query.range());
        var buckets = new java.util.TreeMap<java.time.LocalDateTime, Timeline>();
        for (var point : response.timeline()) {
            var value = new Timeline(point.bucket().toString(), point.total(), point.failed(), point.blocked());
            buckets.merge(point.bucket(), value, (left, right) -> new Timeline(left.bucket(),
                    Math.addExact(left.total(), right.total()), Math.addExact(left.failed(), right.failed()),
                    Math.addExact(left.blocked(), right.blocked())));
        }
        // A 30-day rolling interval spans at most 31 calendar days; never silently cut trend data.
        if (buckets.size() > 31) throw new IllegalStateException("Observability bucket limit exceeded");
        var stats = response.stats();
        return new OverviewResult(response.from().toString(), response.to().toString(), response.interval(),
                new Stats(stats.total(), stats.succeeded(), stats.failed(), stats.blocked(), stats.averageDurationMs()),
                response.p95DurationMs(), java.util.List.copyOf(buckets.values()),
                response.sources().stream().limit(8).map(this::category).toList(),
                response.errorCodes().stream().limit(8).map(this::category).toList(),
                response.sources().size() > 8 || response.errorCodes().size() > 8);
    }

    private Category category(PlatformObservabilityResponse.CategoryCount value) {
        return new Category(value.code(), value.total());
    }

    @Override
    public ChartPreferenceResult updateChartPreferences(
            ToolActorContext context, ChartPreferenceCommand command) {
        var response = preferenceService.update(context.userId(), new ObservabilityPreferenceRequest(
                command.charts().stream().map(this::toDto).toList()));
        return new ChartPreferenceResult(response.charts().stream().map(this::toPort).toList());
    }

    @Override
    public ThresholdPreferenceResult updateThresholds(
            ToolActorContext context, ThresholdPreferenceCommand command) {
        var response = preferenceService.updateThresholds(context.userId(),
                new ObservabilityThresholdPreference(command.failedCount(), command.blockedCount(),
                        command.p95DurationMs()));
        return new ThresholdPreferenceResult(response.failedCount(), response.blockedCount(),
                response.p95DurationMs());
    }

    private ObservabilityChartPreference toDto(ChartPreference chart) {
        return new ObservabilityChartPreference(chart.id(), chart.kind(), chart.title(), chart.mode(),
                chart.content(), chart.size(), chart.granularity());
    }

    private ChartPreference toPort(ObservabilityChartPreference chart) {
        return new ChartPreference(chart.id(), chart.kind(), chart.title(), chart.mode(), chart.content(),
                chart.size(), chart.granularity());
    }
}
