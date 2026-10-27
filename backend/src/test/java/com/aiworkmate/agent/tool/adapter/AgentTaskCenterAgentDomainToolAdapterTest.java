package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.AgentTaskCenterToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.dto.AgentTaskDetailResponse;
import com.aiworkmate.service.AgentTaskCommandService;
import com.aiworkmate.service.AgentTaskQueryService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AgentTaskCenterAgentDomainToolAdapterTest {
    private final AgentTaskQueryService queryService = mock(AgentTaskQueryService.class);
    private final AgentTaskCommandService commandService = mock(AgentTaskCommandService.class);
    private final AgentTaskCenterAgentDomainToolAdapter adapter =
            new AgentTaskCenterAgentDomainToolAdapter(queryService, commandService);
    private final ToolActorContext actor = new ToolActorContext(9, 7, 10, 20, 1, "trace");

    @Test
    void delegatesCancellationToLiveDomainCommandService() {
        var response = mock(AgentTaskDetailResponse.class);
        var updatedAt = LocalDateTime.of(2026, 10, 1, 19, 45);
        when(response.taskId()).thenReturn("agt-target");
        when(response.status()).thenReturn("CANCELLED");
        when(response.updatedAt()).thenReturn(updatedAt);
        when(commandService.cancel(7L, "agt-target")).thenReturn(response);

        assertThat(adapter.cancel(actor, new AgentTaskCenterToolPort.CancelCommand("agt-target")))
                .isEqualTo(new AgentTaskCenterToolPort.CancelResult("agt-target", "CANCELLED", updatedAt));
        verify(commandService).cancel(7L, "agt-target");
        verifyNoMoreInteractions(commandService);
        verifyNoInteractions(queryService);
    }
}
