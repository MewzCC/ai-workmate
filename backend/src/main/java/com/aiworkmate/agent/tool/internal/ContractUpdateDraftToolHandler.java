package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.FinanceToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public final class ContractUpdateDraftToolHandler extends TypedWriteToolHandler<
        FinanceToolPort.ContractDraftUpdate, FinanceToolPort.ContractDraftResult> {
    private final FinanceToolPort port;

    public ContractUpdateDraftToolHandler(FinanceToolPort port, ObjectMapper mapper) {
        super(ToolCode.CONTRACT_UPDATE_DRAFT, mapper);
        this.port = port;
    }

    @Override protected FinanceToolPort.ContractDraftUpdate parseArguments(JsonNode arguments) {
        return ContractDraftArguments.parseUpdate(arguments);
    }

    @Override protected FinanceToolPort.ContractDraftResult invoke(
            TrustedToolContext context, FinanceToolPort.ContractDraftUpdate command) {
        return port.updateContractDraft(context.actor(), command);
    }
}
