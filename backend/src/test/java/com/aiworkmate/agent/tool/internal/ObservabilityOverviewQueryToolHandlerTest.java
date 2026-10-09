package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.gateway.ToolSchemaValidator;
import com.aiworkmate.agent.registry.AgentObservabilityReadToolDefinitions;
import com.aiworkmate.agent.tool.port.ObservabilityToolPort;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ObservabilityOverviewQueryToolHandlerTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ObservabilityToolPort port = mock(ObservabilityToolPort.class);
    private final TrustedToolContext context = new TrustedToolContext(9L, 7L, 20L, 30L, 1, "trace");

    @Test void defaultWeekUsesTrustedContextAndReturnsSchemaValidEmptyData() throws Exception {
        var query = new ObservabilityToolPort.OverviewQuery("7d");
        when(port.overview(context.actor(), query)).thenReturn(new ObservabilityToolPort.OverviewResult(
                "2026-10-02T10:00:00", "2026-10-09T10:00:00", "day",
                new ObservabilityToolPort.Stats(0, 0, 0, 0, null), null, List.of(), List.of(), List.of(), false));
        var output = new ObservabilityOverviewQueryToolHandler(port, mapper).execute(context, mapper.readTree("{}"));
        assertThat(new ToolSchemaValidator().valid(mapper.readTree(
                AgentObservabilityReadToolDefinitions.OUTPUT_SCHEMA), output)).isTrue();
        verify(port).overview(context.actor(), query);
    }
    @Test void invalidRangeTypesOrNullNeverReachDomain() throws Exception {
        var handler = new ObservabilityOverviewQueryToolHandler(port, mapper);
        for (String json : new String[]{"{\"range\":\"365d\"}", "{\"range\":7}", "{\"range\":null}", "{\"range\":\"\"}"}) {
            assertThatThrownBy(() -> handler.execute(context, mapper.readTree(json))).isInstanceOf(BusinessException.class);
        }
        verifyNoInteractions(port);
    }
    @Test void permissionFailureHasNoFallbackOrSecondCall() throws Exception {
        var query = new ObservabilityToolPort.OverviewQuery("24h");
        var denied = new BusinessException(ErrorCode.PERMISSION_DENIED);
        when(port.overview(context.actor(), query)).thenThrow(denied);
        assertThatThrownBy(() -> new ObservabilityOverviewQueryToolHandler(port, mapper)
                .execute(context, mapper.readTree("{\"range\":\"24h\"}"))).isSameAs(denied);
        verify(port).overview(context.actor(), query);
        verifyNoMoreInteractions(port);
    }
}
