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
    void delegatesChartsAndThresholdsToLivePermissionCheckedService() {
        ObservabilityPreferenceService service = mock(ObservabilityPreferenceService.class);
        var adapter = new ObservabilityAgentDomainToolAdapter(service);
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
