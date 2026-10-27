package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.gateway.ToolSchemaValidator;
import com.aiworkmate.agent.registry.AgentDashboardReadToolDefinitions;
import com.aiworkmate.agent.tool.port.DashboardToolPort;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DashboardOverviewQueryToolHandlerTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final DashboardToolPort port = mock(DashboardToolPort.class);
    private final TrustedToolContext context = new TrustedToolContext(9L, 7L, 20L, 30L, 1, "trace");

    @Test
    void usesTrustedActorAndDefaultWeekAndProducesSchemaValidAggregates() throws Exception {
        var query = new DashboardToolPort.OverviewQuery(7);
        when(port.overview(context.actor(), query)).thenReturn(new DashboardToolPort.OverviewResult(
                "2026-10-09T10:00:00+08:00", 7,
                List.of(new DashboardToolPort.Metric("PENDING_TODOS", 3)),
                List.of(new DashboardToolPort.Trend("2026-10-09", 2, 1)),
                List.of(new DashboardToolPort.Distribution("LEAVE_APPLICATION", 2)), false));
        var output = new DashboardOverviewQueryToolHandler(port, mapper).execute(context, mapper.readTree("{}"));
        assertThat(output.path("metrics").get(0).path("value").asLong()).isEqualTo(3);
        assertThat(new ToolSchemaValidator().valid(mapper.readTree(
                AgentDashboardReadToolDefinitions.OUTPUT_SCHEMA), output)).isTrue();
        verify(port).overview(context.actor(), query);
    }

    @Test
    void invalidRangeOrTypeNeverReachesDomain() throws Exception {
        var handler = new DashboardOverviewQueryToolHandler(port, mapper);
        for (String json : new String[]{"{\"days\":0}", "{\"days\":31}", "{\"days\":1.5}", "{\"days\":\"7\"}"}) {
            assertThatThrownBy(() -> handler.execute(context, mapper.readTree(json)))
                    .isInstanceOf(BusinessException.class);
        }
        verifyNoInteractions(port);
    }

    @Test
    void permissionFailurePropagatesWithoutFabricatingData() throws Exception {
        var denied = new BusinessException(ErrorCode.PERMISSION_DENIED);
        when(port.overview(context.actor(), new DashboardToolPort.OverviewQuery(30))).thenThrow(denied);
        assertThatThrownBy(() -> new DashboardOverviewQueryToolHandler(port, mapper)
                .execute(context, mapper.readTree("{\"days\":30}"))).isSameAs(denied);
        verify(port).overview(context.actor(), new DashboardToolPort.OverviewQuery(30));
        verifyNoMoreInteractions(port);
    }
}
