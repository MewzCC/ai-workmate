package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.SecurityGovernanceToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SecurityGovernanceQueryToolHandlerTest {
    @Test
    void dispatchesThreeNarrowQueriesWithTrustedActor() throws Exception {
        var mapper = new ObjectMapper(); var port = mock(SecurityGovernanceToolPort.class);
        var context = new TrustedToolContext(1, 2, 3, 4, 0, "trace");
        when(port.accessOverview(any(), any())).thenReturn(new SecurityGovernanceToolPort.AccessOverview(List.of(),0,0,0,0,0));
        when(port.dataScopes(any(), any())).thenReturn(new SecurityGovernanceToolPort.DataScopeOverview(List.of(),0,0));
        when(port.aiPolicies(any(), any())).thenReturn(new SecurityGovernanceToolPort.AiPolicyOverview(new SecurityGovernanceToolPort.RuntimeSwitches(false,false,false,false),new SecurityGovernanceToolPort.TenantSwitches(false,false),List.of(),List.of()));
        var handlers = List.of(new AccessGovernanceQueryToolHandler(port, mapper), new DataPermissionQueryToolHandler(port, mapper), new AiPermissionQueryToolHandler(port, mapper));
        assertThat(handlers).extracting(ToolHandler::toolCode).containsExactly(ToolCode.ACCESS_GOVERNANCE_QUERY.code(), ToolCode.DATA_PERMISSION_QUERY.code(), ToolCode.AI_PERMISSION_QUERY.code());
        handlers.forEach(handler -> handler.execute(context, mapper.createObjectNode()));
        var actor = new ToolActorContext(1,2,3,4,0,"trace");
        verify(port).accessOverview(actor, new SecurityGovernanceToolPort.AccessQuery(null));
        verify(port).dataScopes(actor, new SecurityGovernanceToolPort.DataScopeQuery(null,null));
        verify(port).aiPolicies(actor, new SecurityGovernanceToolPort.AiPolicyQuery(null,null,null));
    }
}
