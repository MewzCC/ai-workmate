package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.OperationalGovernanceToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class OperationalGovernanceQueryToolHandlerTest {
    @Test
    void dispatchesFourNarrowQueriesWithGatewayActor() throws Exception {
        var mapper = new ObjectMapper().findAndRegisterModules();
        var port = mock(OperationalGovernanceToolPort.class);
        var context = new TrustedToolContext(1L, 2L, 3L, 4L, 0, "trace");
        when(port.auditRecords(any(), any())).thenReturn(new OperationalGovernanceToolPort.AuditPage(List.of(), 0, 1, 20));
        when(port.tenantConfiguration(any())).thenReturn(mock(OperationalGovernanceToolPort.TenantConfiguration.class));
        when(port.dictionaries(any(), any())).thenReturn(new OperationalGovernanceToolPort.DictionaryOverview(List.of(), false));
        when(port.systemCapabilities(any())).thenReturn(new OperationalGovernanceToolPort.SystemCapabilities(Instant.EPOCH, List.of()));
        var handlers = List.of(new AuditQueryToolHandler(port, mapper),
                new TenantConfigurationQueryToolHandler(port, mapper), new DictionaryQueryToolHandler(port, mapper),
                new SystemCapabilityQueryToolHandler(port, mapper));

        assertThat(handlers).extracting(ToolHandler::toolCode).containsExactly(
                ToolCode.AUDIT_QUERY.code(), ToolCode.TENANT_CONFIGURATION_QUERY.code(),
                ToolCode.DICTIONARY_QUERY.code(), ToolCode.SYSTEM_CAPABILITY_QUERY.code());
        handlers.forEach(handler -> handler.execute(context, mapper.createObjectNode()));

        var actor = new ToolActorContext(1L, 2L, 3L, 4L, 0, "trace");
        verify(port).auditRecords(actor, new OperationalGovernanceToolPort.AuditQuery(null, null, null, null, null, 1, 20));
        verify(port).tenantConfiguration(actor);
        verify(port).dictionaries(actor, new OperationalGovernanceToolPort.DictionaryQuery(null, null));
        verify(port).systemCapabilities(actor);
    }
}
