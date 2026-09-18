package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.AgentTaskCenterToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.*;

class AgentTaskMineQueryToolHandlerTest {
    @Test
    void forwardsOnlyBoundedFiltersAndGatewayIdentity() {
        var mapper = new ObjectMapper().findAndRegisterModules();
        var port = mock(AgentTaskCenterToolPort.class);
        when(port.mine(any(), any())).thenReturn(new AgentTaskCenterToolPort.TaskPage(List.of(), 0, 1, 20));
        var context = new TrustedToolContext(1L, 2L, 3L, 4L, 0, "trace");

        new AgentTaskMineQueryToolHandler(port, mapper).execute(context, mapper.createObjectNode());

        verify(port).mine(context.actor(), new AgentTaskCenterToolPort.TaskQuery(null, null, null, 1, 20));
    }
}
