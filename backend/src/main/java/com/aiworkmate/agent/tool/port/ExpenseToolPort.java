package com.aiworkmate.agent.tool.port;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Typed boundary for the expense domain and a future expense service adapter. */
public interface ExpenseToolPort {
    ToolPage<Expense> expenses(ToolActorContext context, ExpenseQuery query);
    ExpenseDraftResult createExpenseDraft(
            ToolActorContext context, ExpenseDraft command, ToolOperationKey operationKey);
    ToolWriteVerification<ExpenseDraftResult> findExpenseDraft(
            ToolActorContext context, ExpenseDraft command, ToolOperationKey operationKey);
    ExpenseLifecycleResult updateExpenseDraft(
            ToolActorContext context, long applicationId, int version, ExpenseDraft patch);
    ExpenseLifecycleResult submitExpenseDraft(
            ToolActorContext context, long applicationId, int version);
    ExpenseLifecycleResult withdrawExpense(
            ToolActorContext context, long applicationId, int version);
    ExpenseLifecycleResult reopenExpense(
            ToolActorContext context, long applicationId, int version);

    record ExpenseQuery(Long applicationId, String status, int page, int size) { }
    record ExpenseDraft(BigDecimal amount, String category, LocalDate expenseDate,
                        String invoiceNumber, String reason) { }
    record ExpenseDraftResult(long applicationId, String formKey, String status,
                              int version, LocalDateTime createdAt) implements ToolWriteReceipt { }
    record ExpenseLifecycleResult(long applicationId, String formKey, String status, int version)
            implements ToolWriteReceipt { }
    record Expense(long id, String title, BigDecimal amount, String category, LocalDate expenseDate,
                   String invoiceNumber, String reason, String status, int version, String approverName,
                   LocalDateTime dueAt, LocalDateTime submittedAt, boolean overdue, boolean canRemind,
                   boolean canWithdraw, boolean canEditDraft, boolean canCancel) { }
}
