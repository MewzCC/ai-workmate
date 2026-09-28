package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.SupplierToolPort;
import com.aiworkmate.agent.tool.port.ToolPage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.*;

@Component
public final class SupplierQueryToolHandler extends TypedReadToolHandler<SupplierToolPort.SupplierQuery, ToolPage<SupplierToolPort.Supplier>> {
    private final SupplierToolPort port;
    public SupplierQueryToolHandler(SupplierToolPort port, ObjectMapper mapper) { super(ToolCode.SUPPLIER_QUERY, mapper); this.port = port; }
    @Override protected SupplierToolPort.SupplierQuery parseArguments(JsonNode a) {
        return new SupplierToolPort.SupplierQuery(optionalPositiveLong(a, "supplierId"), optionalText(a, "keyword"), optionalText(a, "status"),
                optionalText(a, "category"), pageNumber(a), pageSize(a));
    }
    @Override protected ToolPage<SupplierToolPort.Supplier> invoke(TrustedToolContext c, SupplierToolPort.SupplierQuery q) { return port.suppliers(c.actor(), q); }
}
