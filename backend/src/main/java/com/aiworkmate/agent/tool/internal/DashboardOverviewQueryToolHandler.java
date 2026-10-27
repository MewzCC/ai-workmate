package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.DashboardToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public final class DashboardOverviewQueryToolHandler
        extends TypedReadToolHandler<DashboardToolPort.OverviewQuery, DashboardToolPort.OverviewResult> {
    private final DashboardToolPort port;

    public DashboardOverviewQueryToolHandler(DashboardToolPort port, ObjectMapper mapper) {
        super(ToolCode.DASHBOARD_OVERVIEW_QUERY, mapper);
        this.port = port;
    }

    @Override
    protected DashboardToolPort.OverviewQuery parseArguments(JsonNode arguments) {
        Integer days = BoundedToolArguments.optionalInt(arguments, "days", 1, 30);
        return new DashboardToolPort.OverviewQuery(days == null ? 7 : days);
    }

    @Override
    protected DashboardToolPort.OverviewResult invoke(TrustedToolContext context,
                                                     DashboardToolPort.OverviewQuery query) {
        return port.overview(context.actor(), query);
    }
}
