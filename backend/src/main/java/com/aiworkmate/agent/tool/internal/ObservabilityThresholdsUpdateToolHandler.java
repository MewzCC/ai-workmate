package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.ObservabilityToolPort;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public final class ObservabilityThresholdsUpdateToolHandler extends TypedWriteToolHandler<
        ObservabilityToolPort.ThresholdPreferenceCommand, ObservabilityToolPort.ThresholdPreferenceResult> {
    private final ObservabilityToolPort port;

    public ObservabilityThresholdsUpdateToolHandler(ObservabilityToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.OBSERVABILITY_THRESHOLDS_UPDATE, objectMapper);
        this.port = port;
    }

    @Override
    protected ObservabilityToolPort.ThresholdPreferenceCommand parseArguments(JsonNode arguments) {
        return new ObservabilityToolPort.ThresholdPreferenceCommand(
                bounded(arguments, "failedCount", 1_000_000),
                bounded(arguments, "blockedCount", 1_000_000),
                bounded(arguments, "p95DurationMs", 600_000));
    }

    private Integer bounded(JsonNode arguments, String field, int maximum) {
        JsonNode value = arguments.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isIntegralNumber() || !value.canConvertToInt()
                || value.intValue() < 1 || value.intValue() > maximum) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID);
        }
        return value.intValue();
    }

    @Override
    protected ObservabilityToolPort.ThresholdPreferenceResult invoke(
            TrustedToolContext context, ObservabilityToolPort.ThresholdPreferenceCommand command) {
        return port.updateThresholds(context.actor(), command);
    }
}
