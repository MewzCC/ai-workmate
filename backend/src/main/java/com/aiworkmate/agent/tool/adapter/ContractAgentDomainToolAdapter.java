package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.ContractToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.agent.tool.port.ToolPage;
import com.aiworkmate.dto.ContractRequest;
import com.aiworkmate.dto.ContractFulfillmentRequest;
import com.aiworkmate.dto.ContractPaymentRequest;
import com.aiworkmate.dto.ContractReminderRequest;
import com.aiworkmate.dto.ContractStatusRequest;
import com.aiworkmate.service.ContractService;
import com.aiworkmate.service.model.ContractAgentDraftCommand;
import lombok.RequiredArgsConstructor;

import java.util.List;

@LocalAgentDomainAdapter
@RequiredArgsConstructor
public class ContractAgentDomainToolAdapter implements ContractToolPort {
    private final ContractService contractService;

    @Override public ToolPage<Contract> contracts(ToolActorContext context, ContractQuery query) {
        if (query.contractId() != null) {
            return new ToolPage<>(List.of(contract(
                    contractService.detail(context.userId(), query.contractId()).contract())), 1, 1, 1);
        }
        var result = contractService.list(context.userId(), query.keyword(), query.status(),
                query.contractType(), query.expiryState(), query.page(), query.size());
        return new ToolPage<>(result.records().stream().map(this::contract).toList(),
                result.total(), result.page(), result.size());
    }

    @Override public ContractDraftResult createContractDraft(
            ToolActorContext context, ContractDraft command) {
        var result = contractService.create(context.userId(), new ContractRequest(
                command.code(), command.name(), command.contractType(), command.counterpartyName(),
                command.supplierId(), command.ownerUserId(), command.amount(), command.currency(),
                command.signedDate(), command.startDate(), command.endDate(), command.summary(), null));
        return result(result);
    }

    @Override public ContractDraftResult updateContractDraft(
            ToolActorContext context, ContractDraftUpdate command) {
        var result = contractService.updateAgentDraft(context.userId(), command.contractId(), command.version(),
                new ContractAgentDraftCommand(command.name(), command.contractType(), command.counterpartyName(),
                        command.supplierId(), command.ownerUserId(), command.amount(), command.currency(),
                        command.signedDate(), command.startDate(), command.endDate(), command.summary()));
        return result(result);
    }

    @Override public ContractDraftResult updateContractStatus(
            ToolActorContext context, ContractStatusUpdate command) {
        var result = contractService.updateStatus(context.userId(), command.contractId(),
                new ContractStatusRequest(command.status(), command.reason(), command.version()));
        return result(result);
    }

    @Override public ContractFulfillmentResult updateContractFulfillment(
            ToolActorContext context, ContractFulfillmentUpdate command) {
        var result = contractService.updateFulfillment(context.userId(), command.contractId(),
                new ContractFulfillmentRequest(command.status(), command.reason(), command.version()));
        return new ContractFulfillmentResult(result.id(), result.code(), result.status(),
                result.fulfillmentStatus(), result.version(), result.updatedAt());
    }

    @Override public ContractPaymentResult recordContractPayment(
            ToolActorContext context, ContractPayment command) {
        var result = contractService.recordPayment(context.userId(), command.contractId(),
                new ContractPaymentRequest(command.amount(), command.paymentDate(),
                        command.reference(), command.note(), command.version()));
        return new ContractPaymentResult(result.id(), result.code(), result.status(), command.amount(),
                result.paidAmount(), result.currency(), result.version(), result.updatedAt());
    }

    @Override public ContractReminderResult remindContract(
            ToolActorContext context, long contractId, int version) {
        var result = contractService.remind(context.userId(), contractId, new ContractReminderRequest(version));
        return new ContractReminderResult(result.id(), result.code(), result.expiryState(),
                result.reminderCount(), result.lastRemindedAt(), result.version());
    }

    private ContractDraftResult result(com.aiworkmate.dto.ContractResponse item) {
        return new ContractDraftResult(item.id(), item.code(), item.status(), item.version(), item.updatedAt());
    }

    private Contract contract(com.aiworkmate.dto.ContractResponse item) {
        return new Contract(item.id(), item.code(), item.name(), item.contractType(), item.counterpartyName(),
                item.supplierLabel(), item.ownerLabel(), item.amount(), item.paidAmount(), item.currency(),
                item.signedDate(), item.startDate(), item.endDate(), item.status(), item.fulfillmentStatus(),
                item.expiryState(), item.daysUntilExpiry(), item.summary(), item.version(), item.updatedAt(),
                item.canManage(), item.canRecordPayment(), item.canRemind());
    }
}
