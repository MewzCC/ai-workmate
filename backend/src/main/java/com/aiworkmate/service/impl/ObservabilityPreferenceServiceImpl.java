package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.dto.ObservabilityChartPreference;
import com.aiworkmate.dto.ObservabilityPreferenceRequest;
import com.aiworkmate.dto.ObservabilityPreferenceResponse;
import com.aiworkmate.dto.ObservabilityThresholdPreference;
import com.aiworkmate.service.ObservabilityPreferenceService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.UserSettingsService;
import com.aiworkmate.service.model.ResolvedUserAccess;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ObservabilityPreferenceServiceImpl implements ObservabilityPreferenceService {
    private static final Map<String, Set<String>> MODES = Map.of(
            "volume", Set.of("line", "area", "bar"),
            "risk", Set.of("mixed", "line", "bar"),
            "source", Set.of("donut", "bar"),
            "error", Set.of("bar", "donut"));
    private static final Map<String, Set<String>> CONTENT = Map.of(
            "volume", Set.of("HUMAN", "AGENT", "INTEGRATION"),
            "risk", Set.of("failed", "blocked"),
            "source", Set.of("HUMAN", "AGENT", "INTEGRATION"));
    private static final List<ObservabilityChartPreference> DEFAULTS = List.of(
            new ObservabilityChartPreference("volume", "volume", "", "line", List.of("HUMAN", "AGENT", "INTEGRATION"), "normal", "auto"),
            new ObservabilityChartPreference("risk", "risk", "", "mixed", List.of("failed", "blocked"), "normal", "auto"),
            new ObservabilityChartPreference("source", "source", "", "donut", List.of("HUMAN", "AGENT", "INTEGRATION"), "normal", "auto"),
            new ObservabilityChartPreference("error", "error", "", "bar", List.of(), "normal", "auto"));

    private final UserAccessService accessService;
    private final UserSettingsService settingsService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public ObservabilityPreferenceResponse preferences(Long userId) {
        requireAccess(userId);
        String value = settingsService.getObservabilityChartConfig(userId);
        if (value == null || value.isBlank()) return new ObservabilityPreferenceResponse(DEFAULTS);
        try {
            List<ObservabilityChartPreference> saved = objectMapper.readValue(value, new TypeReference<>() {});
            return new ObservabilityPreferenceResponse(validate(saved));
        } catch (JsonProcessingException | IllegalArgumentException | BusinessException ignored) {
            return new ObservabilityPreferenceResponse(DEFAULTS);
        }
    }

    @Override
    @Transactional
    public ObservabilityPreferenceResponse update(Long userId, ObservabilityPreferenceRequest request) {
        requireAccess(userId);
        List<ObservabilityChartPreference> charts = validate(request.charts());
        try {
            settingsService.setObservabilityChartConfig(userId, objectMapper.writeValueAsString(charts));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize observability chart preference", exception);
        }
        return new ObservabilityPreferenceResponse(charts);
    }

    @Override
    @Transactional(readOnly = true)
    public ObservabilityThresholdPreference thresholds(Long userId) {
        requireAccess(userId);
        String value = settingsService.getObservabilityThresholdConfig(userId);
        if (value == null || value.isBlank()) return emptyThresholds();
        try {
            ObservabilityThresholdPreference saved = objectMapper.readValue(value, ObservabilityThresholdPreference.class);
            return validateThresholds(saved);
        } catch (JsonProcessingException | BusinessException ignored) {
            return emptyThresholds();
        }
    }

    @Override
    @Transactional
    public ObservabilityThresholdPreference updateThresholds(Long userId, ObservabilityThresholdPreference request) {
        requireAccess(userId);
        ObservabilityThresholdPreference validated = validateThresholds(request);
        try {
            settingsService.setObservabilityThresholdConfig(userId, objectMapper.writeValueAsString(validated));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize observability visual threshold preference", exception);
        }
        return validated;
    }

    private ObservabilityThresholdPreference emptyThresholds() {
        return new ObservabilityThresholdPreference(null, null, null);
    }

    private ObservabilityThresholdPreference validateThresholds(ObservabilityThresholdPreference thresholds) {
        if (thresholds == null || !bounded(thresholds.failedCount(), 1_000_000)
                || !bounded(thresholds.blockedCount(), 1_000_000)
                || !bounded(thresholds.p95DurationMs(), 600_000)) throw invalid();
        return thresholds;
    }

    private boolean bounded(Integer value, int maximum) {
        return value == null || value >= 1 && value <= maximum;
    }

    private void requireAccess(Long userId) {
        ResolvedUserAccess actor = accessService.resolveActiveUser(userId);
        if (actor == null) throw new BusinessException(ErrorCode.AUTH_REQUIRED);
        if (!actor.permissions().contains("route:platform-observability")
                || !actor.permissions().contains("runtime-log:read")) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        }
    }

    private List<ObservabilityChartPreference> validate(List<ObservabilityChartPreference> charts) {
        if (charts == null || charts.isEmpty() || charts.size() > 12) throw invalid();
        Set<String> ids = new HashSet<>();
        java.util.ArrayList<ObservabilityChartPreference> normalized = new java.util.ArrayList<>(charts.size());
        for (ObservabilityChartPreference chart : charts) {
            String kind = chart == null ? null : chart.kind() == null && chart.id() != null && MODES.containsKey(chart.id())
                    ? chart.id() : chart.kind();
            if (chart == null || chart.id() == null || chart.mode() == null || chart.size() == null
                    || !chart.id().matches("[a-z0-9-]{1,64}") || kind == null || !MODES.containsKey(kind)
                    || !ids.add(chart.id()) || !MODES.get(kind).contains(chart.mode())
                    || !Set.of("normal", "wide").contains(chart.size()) || chart.content() == null) throw invalid();
            String title = chart.title() == null ? "" : chart.title().trim();
            String granularity = chart.granularity() == null ? "auto" : chart.granularity();
            if (title.length() > 40 || title.chars().anyMatch(Character::isISOControl)
                    || !Set.of("auto", "hour", "day").contains(granularity)) throw invalid();
            List<String> selected = chart.content();
            if (selected.size() > 8 || selected.stream().anyMatch(value -> value == null)
                    || new HashSet<>(selected).size() != selected.size()) throw invalid();
            if ("error".equals(kind)) {
                if (selected.stream().anyMatch(code -> code == null || !code.matches("[A-Za-z0-9_.:-]{1,64}"))) throw invalid();
            } else if (selected.isEmpty() || !CONTENT.get(kind).containsAll(selected)) throw invalid();
            normalized.add(new ObservabilityChartPreference(chart.id(), kind, title, chart.mode(),
                    List.copyOf(selected), chart.size(), granularity));
        }
        return List.copyOf(normalized);
    }

    private BusinessException invalid() {
        return new BusinessException(ErrorCode.REQUEST_INVALID);
    }
}
