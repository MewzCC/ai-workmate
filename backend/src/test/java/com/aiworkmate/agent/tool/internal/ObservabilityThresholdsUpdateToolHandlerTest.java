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

class ObservabilityThresholdsUpdateToolHandlerTest {
    @Test
    void preservesNullablePersonalVisualThresholds() throws Exception {
        ObservabilityToolPort port = mock(ObservabilityToolPort.class);
        ObjectMapper mapper = new ObjectMapper();
        var context = new TrustedToolContext(9L, 7L, 10L, 20L, 1, "trace");
        var command = new ObservabilityToolPort.ThresholdPreferenceCommand(5, null, 1500);
        var result = new ObservabilityToolPort.ThresholdPreferenceResult(5, null, 1500);
        when(port.updateThresholds(context.actor(), command)).thenReturn(result);

        var output = new ObservabilityThresholdsUpdateToolHandler(port, mapper).execute(context,
                mapper.readTree("{\"failedCount\":5,\"blockedCount\":null,\"p95DurationMs\":1500}"));

        assertThat(output.path("failedCount").asInt()).isEqualTo(5);
        assertThat(output.path("blockedCount").isNull()).isTrue();
        verify(port).updateThresholds(context.actor(), command);
    }

    @Test
    void rejectsOutOfRangeThresholdBeforeCallingDomain() throws Exception {
        var handler = new ObservabilityThresholdsUpdateToolHandler(
                mock(ObservabilityToolPort.class), new ObjectMapper());
        assertThatThrownBy(() -> handler.execute(new TrustedToolContext(9L, 7L, 10L, 20L, 1, "trace"),
                new ObjectMapper().readTree("{\"p95DurationMs\":600001}")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("REQUEST_INVALID");
    }
}
