package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.SecurityGovernanceToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalText;

@Component
public final class AccessGovernanceQueryToolHandler extends TypedReadToolHandler<SecurityGovernanceToolPort.AccessQuery, SecurityGovernanceToolPort.AccessOverview> {
    private final SecurityGovernanceToolPort port;
    public AccessGovernanceQueryToolHandler(SecurityGovernanceToolPort port, ObjectMapper mapper) { super(ToolCode.ACCESS_GOVERNANCE_QUERY, mapper); this.port = port; }
    @Override protected SecurityGovernanceToolPort.AccessQuery parseArguments(JsonNode a) { return new SecurityGovernanceToolPort.AccessQuery(optionalText(a, "filterCode")); }
    @Override protected SecurityGovernanceToolPort.AccessOverview invoke(TrustedToolContext c, SecurityGovernanceToolPort.AccessQuery q) { return port.accessOverview(c.actor(), q); }
}
