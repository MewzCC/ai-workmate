package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.ObservabilityToolPort;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public final class ObservabilityPreferencesUpdateToolHandler extends TypedWriteToolHandler<
        ObservabilityToolPort.ChartPreferenceCommand, ObservabilityToolPort.ChartPreferenceResult> {
    private static final Map<String, Set<String>> MODES = Map.of(
            "volume", Set.of("line", "area", "bar"), "risk", Set.of("mixed", "line", "bar"),
            "source", Set.of("donut", "bar"), "error", Set.of("bar", "donut"));
    private static final Map<String, Set<String>> CONTENT = Map.of(
            "volume", Set.of("HUMAN", "AGENT", "INTEGRATION"),
            "risk", Set.of("failed", "blocked"),
            "source", Set.of("HUMAN", "AGENT", "INTEGRATION"));
    private final ObservabilityToolPort port;

    public ObservabilityPreferencesUpdateToolHandler(ObservabilityToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.OBSERVABILITY_PREFERENCES_UPDATE, objectMapper);
        this.port = port;
    }

    @Override
    protected ObservabilityToolPort.ChartPreferenceCommand parseArguments(JsonNode arguments) {
        JsonNode values = arguments.get("charts");
        if (values == null || !values.isArray() || values.isEmpty() || values.size() > 12) throw invalid();
        List<ObservabilityToolPort.ChartPreference> charts = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        values.forEach(value -> {
            String id = requiredText(value, "id");
            String kind = requiredText(value, "kind");
            String title = requiredText(value, "title");
            String mode = requiredText(value, "mode");
            String size = requiredText(value, "size");
            String granularity = requiredText(value, "granularity");
            JsonNode contentNode = value.get("content");
            if (!id.matches("[a-z0-9-]{1,64}") || !ids.add(id) || !MODES.containsKey(kind)
                    || !MODES.get(kind).contains(mode) || title.length() > 40
                    || title.chars().anyMatch(Character::isISOControl)
                    || !Set.of("normal", "wide").contains(size)
                    || !Set.of("auto", "hour", "day").contains(granularity)
                    || contentNode == null || !contentNode.isArray() || contentNode.size() > 8) throw invalid();
            List<String> content = new ArrayList<>();
            contentNode.forEach(item -> {
                if (!item.isTextual()) throw invalid();
                content.add(item.asText());
            });
            if (new HashSet<>(content).size() != content.size()) throw invalid();
            if ("error".equals(kind)) {
                if (content.stream().anyMatch(code -> !code.matches("[A-Za-z0-9_.:-]{1,64}"))) throw invalid();
            } else if (content.isEmpty() || !CONTENT.get(kind).containsAll(content)) throw invalid();
            charts.add(new ObservabilityToolPort.ChartPreference(id, kind, title.trim(), mode,
                    content, size, granularity));
        });
        return new ObservabilityToolPort.ChartPreferenceCommand(charts);
    }

    private String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) throw invalid();
        return value.asText();
    }

    private BusinessException invalid() { return new BusinessException(ErrorCode.REQUEST_INVALID); }

    @Override
    protected ObservabilityToolPort.ChartPreferenceResult invoke(
            TrustedToolContext context, ObservabilityToolPort.ChartPreferenceCommand command) {
        return port.updateChartPreferences(context.actor(), command);
    }
}
