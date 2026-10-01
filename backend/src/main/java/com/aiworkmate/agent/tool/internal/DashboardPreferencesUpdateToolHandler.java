package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.DashboardToolPort;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public final class DashboardPreferencesUpdateToolHandler
        extends TypedWriteToolHandler<DashboardToolPort.PreferenceUpdateCommand,
        DashboardToolPort.PreferenceUpdateResult> {
    private static final Set<String> ALLOWED_METRICS = Set.of(
            "PENDING_TODOS", "OVERDUE_TODOS", "MY_APPLICATIONS", "UNREAD_MESSAGES");
    private final DashboardToolPort port;

    public DashboardPreferencesUpdateToolHandler(DashboardToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.DASHBOARD_PREFERENCES_UPDATE, objectMapper);
        this.port = port;
    }

    @Override
    protected DashboardToolPort.PreferenceUpdateCommand parseArguments(JsonNode arguments) {
        JsonNode values = arguments.get("metricCodes");
        if (values == null || !values.isArray() || values.isEmpty() || values.size() > 4) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID);
        }
        List<String> metrics = new ArrayList<>();
        values.forEach(value -> {
            if (!value.isTextual() || !ALLOWED_METRICS.contains(value.asText())) {
                throw new BusinessException(ErrorCode.REQUEST_INVALID);
            }
            metrics.add(value.asText());
        });
        if (new LinkedHashSet<>(metrics).size() != metrics.size()) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID);
        }
        return new DashboardToolPort.PreferenceUpdateCommand(metrics);
    }

    @Override
    protected DashboardToolPort.PreferenceUpdateResult invoke(
            TrustedToolContext context, DashboardToolPort.PreferenceUpdateCommand command) {
        return port.updatePreferences(context.actor(), command);
    }
}
