package com.aiworkmate.agent.tool.port;

import java.time.LocalDateTime;
import java.util.List;

/** Typed boundary for the supplier domain and a future supplier service adapter. */
public interface SupplierToolPort {
    ToolPage<Supplier> suppliers(ToolActorContext context, SupplierQuery query);
    SupplierDraftResult createSupplierDraft(ToolActorContext context, SupplierDraft command);
    SupplierDraftResult updateSupplierDraft(ToolActorContext context, SupplierDraftUpdate command);
    SupplierDraftResult updateSupplierStatus(ToolActorContext context, SupplierStatusUpdate command);

    record SupplierQuery(Long supplierId, String keyword, String status, String category, int page, int size) { }
    record SupplierDraft(String code, String name, String shortName, String category,
                         String supplierLevel, String paymentTerms) { }
    record SupplierDraftUpdate(long supplierId, int version, String name, String shortName,
                               String category, String supplierLevel, String paymentTerms) { }
    record SupplierStatusUpdate(long supplierId, int version, String status, String reason) { }
    record SupplierDraftResult(long supplierId, String code, String status, int version,
                               LocalDateTime updatedAt) implements ToolWriteReceipt { }
    record Supplier(long id, String code, String name, String shortName, String category,
                    String supplierLevel, String status, String paymentTerms, int version,
                    LocalDateTime updatedAt, boolean canManage, List<String> allowedTransitions) { }
}
