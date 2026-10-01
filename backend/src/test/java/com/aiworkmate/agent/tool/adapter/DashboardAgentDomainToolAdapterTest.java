package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.DashboardToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.dto.DashboardPreferenceRequest;
import com.aiworkmate.dto.DashboardPreferenceResponse;
import com.aiworkmate.service.DashboardService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DashboardAgentDomainToolAdapterTest {
    @Test
    void delegatesToTheExistingLivePermissionCheckedDomainService() {
        DashboardService service = mock(DashboardService.class);
        var adapter = new DashboardAgentDomainToolAdapter(service);
        var actor = new ToolActorContext(9L, 7L, 20L, 30L, 1, "trace");
        var command = new DashboardToolPort.PreferenceUpdateCommand(
                List.of("MY_APPLICATIONS", "PENDING_TODOS"));
        var request = new DashboardPreferenceRequest(command.metricCodes());
        when(service.updatePreferences(7L, request)).thenReturn(new DashboardPreferenceResponse(
                command.metricCodes(), List.of("PENDING_TODOS", "MY_APPLICATIONS")));

        var result = adapter.updatePreferences(actor, command);

        assertThat(result.metricCodes()).containsExactly("MY_APPLICATIONS", "PENDING_TODOS");
        verify(service).updatePreferences(7L, request);
    }
}
