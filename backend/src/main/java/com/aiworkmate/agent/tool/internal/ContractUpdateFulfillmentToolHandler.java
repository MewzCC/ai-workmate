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
public final class ContractUpdateFulfillmentToolHandler extends TypedWriteToolHandler<
        ContractToolPort.ContractFulfillmentUpdate, ContractToolPort.ContractFulfillmentResult> {
    private final ContractToolPort port;

    public ContractUpdateFulfillmentToolHandler(ContractToolPort port, ObjectMapper mapper) {
        super(ToolCode.CONTRACT_UPDATE_FULFILLMENT, mapper);
        this.port = port;
    }

    @Override protected ContractToolPort.ContractFulfillmentUpdate parseArguments(JsonNode arguments) {
        return new ContractToolPort.ContractFulfillmentUpdate(
                requiredLong(arguments, "contractId", 1),
                requiredInt(arguments, "version", 0, Integer.MAX_VALUE - 1),
                requiredText(arguments, "status"), optionalText(arguments, "reason"));
    }

    @Override protected ContractToolPort.ContractFulfillmentResult invoke(
            TrustedToolContext context, ContractToolPort.ContractFulfillmentUpdate command) {
        return port.updateContractFulfillment(context.actor(), command);
    }
}
