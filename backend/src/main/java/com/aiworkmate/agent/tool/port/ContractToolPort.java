package com.aiworkmate.agent.tool.port;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Typed boundary for the contract domain and a future contract service adapter. */
public interface ContractToolPort {
    ToolPage<Contract> contracts(ToolActorContext context, ContractQuery query);
    ContractDraftResult createContractDraft(ToolActorContext context, ContractDraft command);
    ContractDraftResult updateContractDraft(ToolActorContext context, ContractDraftUpdate command);
    ContractDraftResult updateContractStatus(ToolActorContext context, ContractStatusUpdate command);
    ContractFulfillmentResult updateContractFulfillment(
            ToolActorContext context, ContractFulfillmentUpdate command);

    record ContractQuery(Long contractId, String keyword, String status, String contractType,
                         String expiryState, int page, int size) { }
    record ContractDraft(String code, String name, String contractType, String counterpartyName,
                         Long supplierId, long ownerUserId, BigDecimal amount, String currency,
                         LocalDate signedDate, LocalDate startDate, LocalDate endDate, String summary) { }
    record ContractDraftUpdate(long contractId, int version, String name, String contractType,
                               String counterpartyName, Long supplierId, long ownerUserId,
                               BigDecimal amount, String currency, LocalDate signedDate,
                               LocalDate startDate, LocalDate endDate, String summary) { }
    record ContractStatusUpdate(long contractId, int version, String status, String reason) { }
    record ContractFulfillmentUpdate(long contractId, int version, String status, String reason) { }
    record ContractDraftResult(long contractId, String code, String status, int version,
                               LocalDateTime updatedAt) implements ToolWriteReceipt { }
    record ContractFulfillmentResult(long contractId, String code, String status,
                                     String fulfillmentStatus, int version,
                                     LocalDateTime updatedAt) implements ToolWriteReceipt { }
    record Contract(long id, String code, String name, String contractType, String counterpartyName,
                    String supplierLabel, String ownerLabel, BigDecimal amount, BigDecimal paidAmount,
                    String currency, LocalDate signedDate, LocalDate startDate, LocalDate endDate,
                    String status, String fulfillmentStatus, String expiryState, long daysUntilExpiry,
                    String summary, int version, LocalDateTime updatedAt, boolean canManage,
                    boolean canRecordPayment, boolean canRemind) { }
}
