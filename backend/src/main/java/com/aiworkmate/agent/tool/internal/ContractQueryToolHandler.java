package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.FinanceToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.*;

@Component
public final class ContractQueryToolHandler extends TypedReadToolHandler<FinanceToolPort.ContractQuery, FinanceToolPort.Page<FinanceToolPort.Contract>> {
    private final FinanceToolPort port;
    public ContractQueryToolHandler(FinanceToolPort port, ObjectMapper mapper) { super(ToolCode.CONTRACT_QUERY, mapper); this.port = port; }
    @Override protected FinanceToolPort.ContractQuery parseArguments(JsonNode a) {
        return new FinanceToolPort.ContractQuery(optionalPositiveLong(a, "contractId"), optionalText(a, "keyword"), optionalText(a, "status"),
                optionalText(a, "contractType"), optionalText(a, "expiryState"), pageNumber(a), pageSize(a));
    }
    @Override protected FinanceToolPort.Page<FinanceToolPort.Contract> invoke(TrustedToolContext c, FinanceToolPort.ContractQuery q) { return port.contracts(c.actor(), q); }
}
