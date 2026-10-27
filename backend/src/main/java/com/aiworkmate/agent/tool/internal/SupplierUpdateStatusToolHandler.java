package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.SupplierToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalText;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredInt;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredLong;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredText;

@Component
public final class SupplierUpdateStatusToolHandler extends TypedWriteToolHandler<
        SupplierToolPort.SupplierStatusUpdate, SupplierToolPort.SupplierDraftResult> {
    private final SupplierToolPort port;

    public SupplierUpdateStatusToolHandler(SupplierToolPort port, ObjectMapper mapper) {
        super(ToolCode.SUPPLIER_UPDATE_STATUS, mapper);
        this.port = port;
    }

    @Override protected SupplierToolPort.SupplierStatusUpdate parseArguments(JsonNode arguments) {
        return new SupplierToolPort.SupplierStatusUpdate(
                requiredLong(arguments, "supplierId", 1),
                requiredInt(arguments, "version", 0, Integer.MAX_VALUE - 1),
                requiredText(arguments, "status"), optionalText(arguments, "reason"));
    }

    @Override protected SupplierToolPort.SupplierDraftResult invoke(
            TrustedToolContext context, SupplierToolPort.SupplierStatusUpdate command) {
        return port.updateSupplierStatus(context.actor(), command);
    }
}
