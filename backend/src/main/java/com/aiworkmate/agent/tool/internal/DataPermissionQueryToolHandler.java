package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.SecurityGovernanceToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.*;

@Component
public final class DataPermissionQueryToolHandler extends TypedReadToolHandler<SecurityGovernanceToolPort.DataScopeQuery, SecurityGovernanceToolPort.DataScopeOverview> {
    private final SecurityGovernanceToolPort port;
    public DataPermissionQueryToolHandler(SecurityGovernanceToolPort port, ObjectMapper mapper) { super(ToolCode.DATA_PERMISSION_QUERY, mapper); this.port = port; }
    @Override protected SecurityGovernanceToolPort.DataScopeQuery parseArguments(JsonNode a) { return new SecurityGovernanceToolPort.DataScopeQuery(optionalText(a, "scopeType"), optionalBoolean(a, "enabled")); }
    @Override protected SecurityGovernanceToolPort.DataScopeOverview invoke(TrustedToolContext c, SecurityGovernanceToolPort.DataScopeQuery q) { return port.dataScopes(c.actor(), q); }
}
