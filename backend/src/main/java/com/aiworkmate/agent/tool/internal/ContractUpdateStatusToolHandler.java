package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.ContractToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalText;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredInt;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredLong;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredText;

@Component
public final class ContractUpdateStatusToolHandler extends TypedWriteToolHandler<
        ContractToolPort.ContractStatusUpdate, ContractToolPort.ContractDraftResult> {
    private final ContractToolPort port;

    public ContractUpdateStatusToolHandler(ContractToolPort port, ObjectMapper mapper) {
        super(ToolCode.CONTRACT_UPDATE_STATUS, mapper);
        this.port = port;
    }

    @Override protected ContractToolPort.ContractStatusUpdate parseArguments(JsonNode arguments) {
        return new ContractToolPort.ContractStatusUpdate(
                requiredLong(arguments, "contractId", 1),
                requiredInt(arguments, "version", 0, Integer.MAX_VALUE - 1),
                requiredText(arguments, "status"), optionalText(arguments, "reason"));
    }

    @Override protected ContractToolPort.ContractDraftResult invoke(
            TrustedToolContext context, ContractToolPort.ContractStatusUpdate command) {
        return port.updateContractStatus(context.actor(), command);
    }
}
