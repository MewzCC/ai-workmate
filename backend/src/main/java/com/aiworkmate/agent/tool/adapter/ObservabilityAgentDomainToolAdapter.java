package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.ObservabilityToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.dto.ObservabilityChartPreference;
import com.aiworkmate.dto.ObservabilityPreferenceRequest;
import com.aiworkmate.dto.ObservabilityThresholdPreference;
import com.aiworkmate.service.ObservabilityPreferenceService;
import lombok.RequiredArgsConstructor;

@LocalAgentDomainAdapter
@RequiredArgsConstructor
public final class ObservabilityAgentDomainToolAdapter implements ObservabilityToolPort {
    private final ObservabilityPreferenceService preferenceService;

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
