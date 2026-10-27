package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.ObservabilityToolPort;
import com.aiworkmate.common.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ObservabilityPreferencesUpdateToolHandlerTest {
    @Test
    void delegatesOnlyBoundedBuiltInChartsForTheGatewayActor() throws Exception {
        ObservabilityToolPort port = mock(ObservabilityToolPort.class);
        ObjectMapper mapper = new ObjectMapper();
        var context = new TrustedToolContext(9L, 7L, 10L, 20L, 1, "trace");
        var chart = new ObservabilityToolPort.ChartPreference("volume", "volume", "Agent 流量",
                "area", java.util.List.of("AGENT"), "wide", "hour");
        var command = new ObservabilityToolPort.ChartPreferenceCommand(java.util.List.of(chart));
        var result = new ObservabilityToolPort.ChartPreferenceResult(command.charts());
        when(port.updateChartPreferences(context.actor(), command)).thenReturn(result);

        var output = new ObservabilityPreferencesUpdateToolHandler(port, mapper).execute(context,
                mapper.readTree("""
                        {"charts":[{"id":"volume","kind":"volume","title":"Agent 流量",
                        "mode":"area","content":["AGENT"],"size":"wide","granularity":"hour"}]}
                        """));

        assertThat(output.path("charts").get(0).path("mode").asText()).isEqualTo("area");
        verify(port).updateChartPreferences(context.actor(), command);
    }

    @Test
    void rejectsKindModeMismatchBeforeCallingDomain() throws Exception {
        var handler = new ObservabilityPreferencesUpdateToolHandler(
                mock(ObservabilityToolPort.class), new ObjectMapper());
        assertThatThrownBy(() -> handler.execute(new TrustedToolContext(9L, 7L, 10L, 20L, 1, "trace"),
                new ObjectMapper().readTree("""
                        {"charts":[{"id":"volume","kind":"volume","title":"","mode":"donut",
                        "content":["HUMAN"],"size":"normal","granularity":"auto"}]}
                        """))).isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("REQUEST_INVALID");
    }
}
