package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.ExpenseToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.agent.tool.port.ToolOperationKey;
import com.aiworkmate.agent.tool.port.ToolPage;
import com.aiworkmate.agent.tool.port.ToolWriteVerification;
import com.aiworkmate.service.ExpenseApplicationService;
import com.aiworkmate.service.ExpenseQueryService;
import com.aiworkmate.service.model.ExpenseAgentDraftCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ExpenseAgentDomainToolAdapter implements ExpenseToolPort {
    private final ExpenseQueryService expenseQueryService;
    private final ExpenseApplicationService expenseApplicationService;

    @Override public ToolPage<Expense> expenses(ToolActorContext context, ExpenseQuery query) {
        if (query.applicationId() != null) {
            return new ToolPage<>(List.of(expense(
                    expenseQueryService.detail(context.userId(), query.applicationId()))), 1, 1, 1);
        }
        var result = expenseQueryService.mine(context.userId(), query.status(), query.page(), query.size());
        return new ToolPage<>(result.records().stream().map(this::expense).toList(),
                result.total(), result.page(), result.size());
    }

    @Override public ExpenseDraftResult createExpenseDraft(
            ToolActorContext context, ExpenseDraft command, ToolOperationKey operationKey) {
        return draftResult(expenseApplicationService.createAgentDraft(
                context.userId(), toDomain(command), operationKey.value()));
    }

    @Override public ToolWriteVerification<ExpenseDraftResult> findExpenseDraft(
            ToolActorContext context, ExpenseDraft command, ToolOperationKey operationKey) {
        return ToolWriteVerification.fromOptional(expenseApplicationService.findAgentDraft(
                context.userId(), toDomain(command), operationKey.value()), this::draftResult);
    }

    @Override public ExpenseLifecycleResult updateExpenseDraft(
            ToolActorContext context, long applicationId, int version, ExpenseDraft patch) {
        var result = expenseApplicationService.updateAgentDraft(
                context.userId(), applicationId, version, toDomain(patch));
        return lifecycleResult(result);
    }

    @Override public ExpenseLifecycleResult submitExpenseDraft(
            ToolActorContext context, long applicationId, int version) {
        return lifecycleResult(expenseApplicationService.submitAgentDraft(
                context.userId(), applicationId, version));
    }

    @Override public ExpenseLifecycleResult withdrawExpense(
            ToolActorContext context, long applicationId, int version) {
        return lifecycleResult(expenseApplicationService.withdrawAgentApplication(
                context.userId(), applicationId, version));
    }

    @Override public ExpenseLifecycleResult reopenExpense(
            ToolActorContext context, long applicationId, int version) {
        return lifecycleResult(expenseApplicationService.reopenAgentApplication(
                context.userId(), applicationId, version));
    }

    private Expense expense(com.aiworkmate.dto.ExpenseSummaryResponse item) {
        return new Expense(item.id(), item.title(), item.amount(), item.category(), item.expenseDate(),
                item.invoiceNumber(), item.reason(), item.status(), item.version(), item.approverName(),
                item.dueAt(), item.submittedAt(), item.overdue(), item.canRemind(), item.canWithdraw(),
                item.canEditDraft(), item.canCancel());
    }

    private ExpenseAgentDraftCommand toDomain(ExpenseDraft command) {
        return new ExpenseAgentDraftCommand(command.amount(), command.category(), command.expenseDate(),
                command.invoiceNumber(), command.reason());
    }

    private ExpenseDraftResult draftResult(com.aiworkmate.service.model.ExpenseAgentDraftReceipt value) {
        return new ExpenseDraftResult(value.applicationId(), value.formKey(), value.status(),
                value.version(), value.createdAt());
    }

    private ExpenseLifecycleResult lifecycleResult(
            com.aiworkmate.service.model.ExpenseAgentLifecycleReceipt value) {
        return new ExpenseLifecycleResult(
                value.applicationId(), value.formKey(), value.status(), value.version());
    }
}
