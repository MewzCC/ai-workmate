package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.OperationalGovernanceToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public final class TenantConfigurationQueryToolHandler extends TypedReadToolHandler<Void, OperationalGovernanceToolPort.TenantConfiguration> {
    private final OperationalGovernanceToolPort port;
    public TenantConfigurationQueryToolHandler(OperationalGovernanceToolPort port, ObjectMapper mapper) { super(ToolCode.TENANT_CONFIGURATION_QUERY, mapper); this.port = port; }
    @Override protected Void parseArguments(JsonNode arguments) { return null; }
    @Override protected OperationalGovernanceToolPort.TenantConfiguration invoke(TrustedToolContext c, Void ignored) { return port.tenantConfiguration(c.actor()); }
}
