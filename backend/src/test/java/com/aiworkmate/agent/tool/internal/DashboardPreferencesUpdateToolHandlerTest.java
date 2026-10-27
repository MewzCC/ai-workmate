package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.DashboardToolPort;
import com.aiworkmate.common.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DashboardPreferencesUpdateToolHandlerTest {
    @Test
    void preservesTheRequestedMetricOrderForTheGatewayActor() throws Exception {
        DashboardToolPort port = mock(DashboardToolPort.class);
        ObjectMapper mapper = new ObjectMapper();
        var context = new TrustedToolContext(1L, 7L, 10L, 30L, 1, "trace");
        var command = new DashboardToolPort.PreferenceUpdateCommand(
                List.of("UNREAD_MESSAGES", "PENDING_TODOS"));
        var result = new DashboardToolPort.PreferenceUpdateResult(command.metricCodes(),
                List.of("PENDING_TODOS", "OVERDUE_TODOS", "MY_APPLICATIONS", "UNREAD_MESSAGES"));
        when(port.updatePreferences(context.actor(), command)).thenReturn(result);

        var output = new DashboardPreferencesUpdateToolHandler(port, mapper).execute(context,
                mapper.readTree("""
                        {"metricCodes":["UNREAD_MESSAGES","PENDING_TODOS"]}
                        """));

        assertThat(output.path("metricCodes").get(0).asText()).isEqualTo("UNREAD_MESSAGES");
        verify(port).updatePreferences(context.actor(), command);
    }

    @Test
    void rejectsDuplicateMetricsBeforeCallingTheDomain() throws Exception {
        DashboardToolPort port = mock(DashboardToolPort.class);
        ObjectMapper mapper = new ObjectMapper();

        assertThatThrownBy(() -> new DashboardPreferencesUpdateToolHandler(port, mapper).execute(
                new TrustedToolContext(1L, 7L, 10L, 30L, 1, "trace"), mapper.readTree("""
                        {"metricCodes":["PENDING_TODOS","PENDING_TODOS"]}
                        """))).isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("REQUEST_INVALID");
    }
}
