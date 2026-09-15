package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.OperationalGovernanceToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.*;

@Component
public final class AuditQueryToolHandler extends TypedReadToolHandler<OperationalGovernanceToolPort.AuditQuery, OperationalGovernanceToolPort.AuditPage> {
    private final OperationalGovernanceToolPort port;
    public AuditQueryToolHandler(OperationalGovernanceToolPort port, ObjectMapper mapper) { super(ToolCode.AUDIT_QUERY, mapper); this.port = port; }
    @Override protected OperationalGovernanceToolPort.AuditQuery parseArguments(JsonNode a) { return new OperationalGovernanceToolPort.AuditQuery(optionalText(a, "action"), optionalText(a, "resourceType"), optionalText(a, "result"), optionalDateTime(a, "from"), optionalDateTime(a, "to"), positiveInt(a, "page", 1, 10000), positiveInt(a, "size", 20, 50)); }
    @Override protected OperationalGovernanceToolPort.AuditPage invoke(TrustedToolContext c, OperationalGovernanceToolPort.AuditQuery q) { return port.auditRecords(c.actor(), q); }
}
