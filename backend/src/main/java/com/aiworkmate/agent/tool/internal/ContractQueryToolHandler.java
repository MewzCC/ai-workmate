package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.ContractToolPort;
import com.aiworkmate.agent.tool.port.ToolPage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.*;

@Component
public final class ContractQueryToolHandler extends TypedReadToolHandler<ContractToolPort.ContractQuery, ToolPage<ContractToolPort.Contract>> {
    private final ContractToolPort port;
    public ContractQueryToolHandler(ContractToolPort port, ObjectMapper mapper) { super(ToolCode.CONTRACT_QUERY, mapper); this.port = port; }
    @Override protected ContractToolPort.ContractQuery parseArguments(JsonNode a) {
        return new ContractToolPort.ContractQuery(optionalPositiveLong(a, "contractId"), optionalText(a, "keyword"), optionalText(a, "status"),
                optionalText(a, "contractType"), optionalText(a, "expiryState"), pageNumber(a), pageSize(a));
    }
    @Override protected ToolPage<ContractToolPort.Contract> invoke(TrustedToolContext c, ContractToolPort.ContractQuery q) { return port.contracts(c.actor(), q); }
}
