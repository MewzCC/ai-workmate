package com.aiworkmate.agent.tool.port;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Typed boundary for the budget domain and a future budget service adapter. */
public interface BudgetToolPort {
    ToolPage<Budget> budgets(ToolActorContext context, BudgetQuery query);
    BudgetDraftResult createBudgetDraft(ToolActorContext context, BudgetDraft command);
    BudgetDraftResult updateBudgetDraft(ToolActorContext context, BudgetDraftUpdate command);
    BudgetDraftResult activateBudgetDraft(ToolActorContext context, long budgetId, int version);
    BudgetDraftResult cancelBudgetDraft(ToolActorContext context, long budgetId, int version);

    record BudgetQuery(Long budgetId, String keyword, String status, Integer fiscalYear, int page, int size) { }
    record BudgetDraft(String code, String name, int fiscalYear, long ownerUserId,
                       BigDecimal totalAmount, String currency, int warningThreshold, String summary) { }
    record BudgetDraftUpdate(long budgetId, int version, String name, int fiscalYear,
                             long ownerUserId, BigDecimal totalAmount, String currency,
                             int warningThreshold, String summary) { }
    record BudgetDraftResult(long budgetId, String code, String status, int version,
                             LocalDateTime updatedAt) implements ToolWriteReceipt { }
    record Budget(long id, String code, String name, Integer fiscalYear, String ownerLabel,
                  BigDecimal totalAmount, BigDecimal occupiedAmount, BigDecimal spentAmount,
                  BigDecimal availableAmount, String currency, int utilizationPercent, String alertLevel,
                  String status, String summary, int version, LocalDateTime updatedAt,
                  boolean canManage, List<String> allowedTransitions) { }
}
