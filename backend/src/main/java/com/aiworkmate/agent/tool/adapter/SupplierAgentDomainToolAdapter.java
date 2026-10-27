package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.SupplierToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.agent.tool.port.ToolPage;
import com.aiworkmate.dto.SupplierRequest;
import com.aiworkmate.dto.SupplierStatusRequest;
import com.aiworkmate.service.SupplierService;
import com.aiworkmate.service.model.SupplierAgentDraftCommand;
import lombok.RequiredArgsConstructor;

import java.util.List;

@LocalAgentDomainAdapter
@RequiredArgsConstructor
public class SupplierAgentDomainToolAdapter implements SupplierToolPort {
    private final SupplierService supplierService;

    @Override public ToolPage<Supplier> suppliers(ToolActorContext context, SupplierQuery query) {
        if (query.supplierId() != null) {
            return new ToolPage<>(List.of(supplier(
                    supplierService.detail(context.userId(), query.supplierId()).supplier())), 1, 1, 1);
        }
        var result = supplierService.list(context.userId(), query.keyword(), query.status(),
                query.category(), query.page(), query.size());
        return new ToolPage<>(result.records().stream().map(this::supplier).toList(),
                result.total(), result.page(), result.size());
    }

    @Override public SupplierDraftResult createSupplierDraft(
            ToolActorContext context, SupplierDraft command) {
        var result = supplierService.create(context.userId(), new SupplierRequest(
                command.code(), command.name(), command.shortName(), null, command.category(),
                command.supplierLevel(), null, null, null, null, command.paymentTerms(), null, null));
        return result(result);
    }

    @Override public SupplierDraftResult updateSupplierDraft(
            ToolActorContext context, SupplierDraftUpdate command) {
        var result = supplierService.updateAgentDraft(context.userId(), command.supplierId(), command.version(),
                new SupplierAgentDraftCommand(command.name(), command.shortName(), command.category(),
                        command.supplierLevel(), command.paymentTerms()));
        return result(result);
    }

    @Override public SupplierDraftResult updateSupplierStatus(
            ToolActorContext context, SupplierStatusUpdate command) {
        var result = supplierService.updateStatus(context.userId(), command.supplierId(),
                new SupplierStatusRequest(command.status(), command.reason(), command.version()));
        return result(result);
    }

    private SupplierDraftResult result(com.aiworkmate.dto.SupplierResponse item) {
        return new SupplierDraftResult(item.id(), item.code(), item.status(), item.version(), item.updatedAt());
    }

    private Supplier supplier(com.aiworkmate.dto.SupplierResponse item) {
        return new Supplier(item.id(), item.code(), item.name(), item.shortName(), item.category(),
                item.supplierLevel(), item.status(), item.paymentTerms(), item.version(), item.updatedAt(),
                item.canManage(), List.copyOf(item.allowedTransitions()));
    }
}
