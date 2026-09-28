package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.ContractToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public final class ContractUpdateDraftToolHandler extends TypedWriteToolHandler<
        ContractToolPort.ContractDraftUpdate, ContractToolPort.ContractDraftResult> {
    private final ContractToolPort port;

    public ContractUpdateDraftToolHandler(ContractToolPort port, ObjectMapper mapper) {
        super(ToolCode.CONTRACT_UPDATE_DRAFT, mapper);
        this.port = port;
    }

    @Override protected ContractToolPort.ContractDraftUpdate parseArguments(JsonNode arguments) {
        return ContractDraftArguments.parseUpdate(arguments);
    }

    @Override protected ContractToolPort.ContractDraftResult invoke(
            TrustedToolContext context, ContractToolPort.ContractDraftUpdate command) {
        return port.updateContractDraft(context.actor(), command);
    }
}
