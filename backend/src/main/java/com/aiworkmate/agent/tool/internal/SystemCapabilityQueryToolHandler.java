package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.OperationalGovernanceToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public final class SystemCapabilityQueryToolHandler extends TypedReadToolHandler<Void, OperationalGovernanceToolPort.SystemCapabilities> {
    private final OperationalGovernanceToolPort port;
    public SystemCapabilityQueryToolHandler(OperationalGovernanceToolPort port, ObjectMapper mapper) { super(ToolCode.SYSTEM_CAPABILITY_QUERY, mapper); this.port = port; }
    @Override protected Void parseArguments(JsonNode arguments) { return null; }
    @Override protected OperationalGovernanceToolPort.SystemCapabilities invoke(TrustedToolContext c, Void ignored) { return port.systemCapabilities(c.actor()); }
}
