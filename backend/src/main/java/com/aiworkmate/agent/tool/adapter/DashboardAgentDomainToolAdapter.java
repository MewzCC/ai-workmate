package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.DashboardToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.dto.DashboardPreferenceRequest;
import com.aiworkmate.service.DashboardService;
import lombok.RequiredArgsConstructor;

@LocalAgentDomainAdapter
@RequiredArgsConstructor
public final class DashboardAgentDomainToolAdapter implements DashboardToolPort {
    private final DashboardService dashboardService;

    @Override
    public OverviewResult overview(ToolActorContext context, OverviewQuery query) {
        var response = dashboardService.overview(context.userId(), query.days());
        // Only aggregate fields cross the Agent boundary; no names, titles or audit summaries.
        return new OverviewResult(response.generatedAt().toString(), response.days(),
                response.metrics().stream().limit(4).map(m -> new Metric(m.code(), m.value())).toList(),
                response.trends().stream().limit(30)
                        .map(t -> new Trend(t.date().toString(), t.submitted(), t.completed())).toList(),
                response.businessDistribution().stream().limit(16)
                        .map(d -> new Distribution(d.businessType(), d.value())).toList(),
                response.businessDistribution().size() > 16);
    }

    @Override
    public PreferenceUpdateResult updatePreferences(
            ToolActorContext context, PreferenceUpdateCommand command) {
        var response = dashboardService.updatePreferences(context.userId(),
                new DashboardPreferenceRequest(command.metricCodes()));
        return new PreferenceUpdateResult(response.metricCodes(), response.availableMetricCodes());
    }
}
