package com.aiworkmate.service.model;

public record SupplierAgentDraftCommand(
        String name,
        String shortName,
        String category,
        String supplierLevel,
        String paymentTerms) {
}
