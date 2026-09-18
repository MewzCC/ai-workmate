package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.SecurityGovernanceToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.*;

@Component
public final class AiPermissionQueryToolHandler extends TypedReadToolHandler<SecurityGovernanceToolPort.AiPolicyQuery, SecurityGovernanceToolPort.AiPolicyOverview> {
    private final SecurityGovernanceToolPort port;
    public AiPermissionQueryToolHandler(SecurityGovernanceToolPort port, ObjectMapper mapper) { super(ToolCode.AI_PERMISSION_QUERY, mapper); this.port = port; }
    @Override protected SecurityGovernanceToolPort.AiPolicyQuery parseArguments(JsonNode a) { return new SecurityGovernanceToolPort.AiPolicyQuery(optionalText(a, "toolCode"), optionalText(a, "filterCode"), optionalBoolean(a, "effectiveEnabled")); }
    @Override protected SecurityGovernanceToolPort.AiPolicyOverview invoke(TrustedToolContext c, SecurityGovernanceToolPort.AiPolicyQuery q) { return port.aiPolicies(c.actor(), q); }
}
