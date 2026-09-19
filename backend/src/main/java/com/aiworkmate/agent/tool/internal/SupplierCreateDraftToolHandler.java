package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.SupplierToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public final class SupplierCreateDraftToolHandler extends TypedWriteToolHandler<
        SupplierToolPort.SupplierDraft, SupplierToolPort.SupplierDraftResult> {
    private final SupplierToolPort port;

    public SupplierCreateDraftToolHandler(SupplierToolPort port, ObjectMapper mapper) {
        super(ToolCode.SUPPLIER_CREATE_DRAFT, mapper);
        this.port = port;
    }

    @Override protected SupplierToolPort.SupplierDraft parseArguments(JsonNode arguments) {
        return SupplierDraftArguments.parse(arguments);
    }

    @Override protected SupplierToolPort.SupplierDraftResult invoke(
            TrustedToolContext context, SupplierToolPort.SupplierDraft command) {
        return port.createSupplierDraft(context.actor(), command);
    }
}
