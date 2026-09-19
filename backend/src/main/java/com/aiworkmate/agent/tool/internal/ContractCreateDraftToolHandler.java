package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.ContractToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public final class ContractCreateDraftToolHandler extends TypedWriteToolHandler<
        ContractToolPort.ContractDraft, ContractToolPort.ContractDraftResult> {
    private final ContractToolPort port;

    public ContractCreateDraftToolHandler(ContractToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.CONTRACT_CREATE_DRAFT, objectMapper);
        this.port = port;
    }

    @Override
    protected ContractToolPort.ContractDraft parseArguments(JsonNode arguments) {
        return ContractDraftArguments.parse(arguments);
    }

    @Override
    protected ContractToolPort.ContractDraftResult invoke(
            TrustedToolContext context, ContractToolPort.ContractDraft command) {
        return port.createContractDraft(context.actor(), command);
    }
}
