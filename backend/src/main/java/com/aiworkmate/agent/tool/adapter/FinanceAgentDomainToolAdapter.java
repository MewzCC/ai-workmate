package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.FinanceToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.agent.tool.port.ToolOperationKey;
import com.aiworkmate.agent.tool.port.ToolWriteVerification;
import com.aiworkmate.service.BudgetService;
import com.aiworkmate.service.ContractService;
import com.aiworkmate.service.ExpenseApplicationService;
import com.aiworkmate.service.ExpenseQueryService;
import com.aiworkmate.service.SupplierService;
import com.aiworkmate.service.model.ExpenseAgentDraftCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class FinanceAgentDomainToolAdapter implements FinanceToolPort {
    private final ExpenseQueryService expenseQueryService;
    private final ExpenseApplicationService expenseApplicationService;
    private final BudgetService budgetService;
    private final ContractService contractService;
    private final SupplierService supplierService;

    @Override public Page<Expense> expenses(ToolActorContext context, ExpenseQuery query) {
        if (query.applicationId() != null) {
            return new Page<>(List.of(expense(expenseQueryService.detail(context.userId(), query.applicationId()))), 1, 1, 1);
        }
        var result = expenseQueryService.mine(context.userId(), query.status(), query.page(), query.size());
        return new Page<>(result.records().stream().map(this::expense).toList(), result.total(), result.page(), result.size());
    }

    @Override
    public ExpenseDraftResult createExpenseDraft(
            ToolActorContext context, ExpenseDraft command, ToolOperationKey operationKey) {
        var result = expenseApplicationService.createAgentDraft(
                context.userId(), toDomain(command), operationKey.value());
        return draftResult(result);
    }

    @Override
    public ToolWriteVerification<ExpenseDraftResult> findExpenseDraft(
            ToolActorContext context, ExpenseDraft command, ToolOperationKey operationKey) {
        return expenseApplicationService.findAgentDraft(
                        context.userId(), toDomain(command), operationKey.value())
                .map(result -> ToolWriteVerification.observed(draftResult(result)))
                .orElseGet(ToolWriteVerification::unobserved);
    }

    @Override public Page<Budget> budgets(ToolActorContext context, BudgetQuery query) {
        if (query.budgetId() != null) {
            var detail = budgetService.detail(context.userId(), query.budgetId()).budget();
            return new Page<>(List.of(budget(detail)), 1, 1, 1);
        }
        var result = budgetService.list(context.userId(), query.keyword(), query.status(), query.fiscalYear(), query.page(), query.size());
        return new Page<>(result.records().stream().map(this::budget).toList(), result.total(), result.page(), result.size());
    }

    @Override public Page<Contract> contracts(ToolActorContext context, ContractQuery query) {
        if (query.contractId() != null) {
            var detail = contractService.detail(context.userId(), query.contractId()).contract();
            return new Page<>(List.of(contract(detail)), 1, 1, 1);
        }
        var result = contractService.list(context.userId(), query.keyword(), query.status(), query.contractType(),
                query.expiryState(), query.page(), query.size());
        return new Page<>(result.records().stream().map(this::contract).toList(), result.total(), result.page(), result.size());
    }

    @Override public Page<Supplier> suppliers(ToolActorContext context, SupplierQuery query) {
        if (query.supplierId() != null) {
            var detail = supplierService.detail(context.userId(), query.supplierId()).supplier();
            return new Page<>(List.of(supplier(detail)), 1, 1, 1);
        }
        var result = supplierService.list(context.userId(), query.keyword(), query.status(), query.category(), query.page(), query.size());
        return new Page<>(result.records().stream().map(this::supplier).toList(), result.total(), result.page(), result.size());
    }

    private Expense expense(com.aiworkmate.dto.ExpenseSummaryResponse item) {
        return new Expense(item.id(), item.title(), item.amount(), item.category(), item.expenseDate(),
                item.invoiceNumber(), item.reason(), item.status(), item.version(), item.approverName(), item.dueAt(), item.submittedAt(), item.overdue(),
                item.canRemind(), item.canWithdraw(), item.canEditDraft(), item.canCancel());
    }

    private ExpenseAgentDraftCommand toDomain(ExpenseDraft command) {
        return new ExpenseAgentDraftCommand(
                command.amount(), command.category(), command.expenseDate(),
                command.invoiceNumber(), command.reason());
    }

    private ExpenseDraftResult draftResult(
            com.aiworkmate.service.model.ExpenseAgentDraftReceipt value) {
        return new ExpenseDraftResult(
                value.applicationId(), value.formKey(), value.status(),
                value.version(), value.createdAt());
    }
    private Budget budget(com.aiworkmate.dto.BudgetResponse item) {
        return new Budget(item.id(), item.code(), item.name(), item.fiscalYear(), item.ownerLabel(), item.totalAmount(),
                item.occupiedAmount(), item.spentAmount(), item.availableAmount(), item.currency(), item.utilizationPercent(),
                item.alertLevel(), item.status(), item.summary(), item.version(), item.updatedAt(), item.canManage(),
                List.copyOf(item.allowedTransitions()));
    }
    private Contract contract(com.aiworkmate.dto.ContractResponse item) {
        return new Contract(item.id(), item.code(), item.name(), item.contractType(), item.counterpartyName(),
                item.supplierLabel(), item.ownerLabel(), item.amount(), item.paidAmount(), item.currency(), item.signedDate(),
                item.startDate(), item.endDate(), item.status(), item.fulfillmentStatus(), item.expiryState(),
                item.daysUntilExpiry(), item.summary(), item.version(), item.updatedAt(), item.canManage(),
                item.canRecordPayment(), item.canRemind());
    }
    private Supplier supplier(com.aiworkmate.dto.SupplierResponse item) {
        return new Supplier(item.id(), item.code(), item.name(), item.shortName(), item.category(), item.supplierLevel(),
                item.status(), item.paymentTerms(), item.version(), item.updatedAt(), item.canManage(),
                List.copyOf(item.allowedTransitions()));
    }
}
