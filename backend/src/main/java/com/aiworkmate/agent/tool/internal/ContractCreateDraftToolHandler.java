package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.FinanceToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public final class ContractCreateDraftToolHandler extends TypedWriteToolHandler<
        FinanceToolPort.ContractDraft, FinanceToolPort.ContractDraftResult> {
    private final FinanceToolPort port;

    public ContractCreateDraftToolHandler(FinanceToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.CONTRACT_CREATE_DRAFT, objectMapper);
        this.port = port;
    }

    @Override
    protected FinanceToolPort.ContractDraft parseArguments(JsonNode arguments) {
        return ContractDraftArguments.parse(arguments);
    }

    @Override
    protected FinanceToolPort.ContractDraftResult invoke(
            TrustedToolContext context, FinanceToolPort.ContractDraft command) {
        return port.createContractDraft(context.actor(), command);
    }
}
