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
    public PreferenceUpdateResult updatePreferences(
            ToolActorContext context, PreferenceUpdateCommand command) {
        var response = dashboardService.updatePreferences(context.userId(),
                new DashboardPreferenceRequest(command.metricCodes()));
        return new PreferenceUpdateResult(response.metricCodes(), response.availableMetricCodes());
    }
}
