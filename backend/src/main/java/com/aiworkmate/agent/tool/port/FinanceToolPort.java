package com.aiworkmate.agent.tool.port;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Typed boundary for a future finance service adapter. */
public interface FinanceToolPort {
    Page<Expense> expenses(ToolActorContext context, ExpenseQuery query);
    Page<Budget> budgets(ToolActorContext context, BudgetQuery query);
    Page<Contract> contracts(ToolActorContext context, ContractQuery query);
    Page<Supplier> suppliers(ToolActorContext context, SupplierQuery query);

    record ExpenseQuery(Long applicationId, String status, int page, int size) { }
    record BudgetQuery(Long budgetId, String keyword, String status, Integer fiscalYear, int page, int size) { }
    record ContractQuery(Long contractId, String keyword, String status, String contractType,
                         String expiryState, int page, int size) { }
    record SupplierQuery(Long supplierId, String keyword, String status, String category, int page, int size) { }
    record Page<T>(List<T> items, long total, int page, int size) {
        public Page { items = List.copyOf(items); }
    }
    record Expense(long id, String title, BigDecimal amount, String category, LocalDate expenseDate,
                   String invoiceNumber, String reason, String status, int version, String approverName,
                   LocalDateTime dueAt, LocalDateTime submittedAt, boolean overdue, boolean canRemind,
                   boolean canWithdraw, boolean canEditDraft, boolean canCancel) { }
    record Budget(long id, String code, String name, Integer fiscalYear, String ownerLabel,
                  BigDecimal totalAmount, BigDecimal occupiedAmount, BigDecimal spentAmount,
                  BigDecimal availableAmount, String currency, int utilizationPercent, String alertLevel,
                  String status, String summary, int version, LocalDateTime updatedAt,
                  boolean canManage, List<String> allowedTransitions) { }
    record Contract(long id, String code, String name, String contractType, String counterpartyName,
                    String supplierLabel, String ownerLabel, BigDecimal amount, BigDecimal paidAmount,
                    String currency, LocalDate signedDate, LocalDate startDate, LocalDate endDate,
                    String status, String fulfillmentStatus, String expiryState, long daysUntilExpiry,
                    String summary, int version, LocalDateTime updatedAt, boolean canManage,
                    boolean canRecordPayment, boolean canRemind) { }
    record Supplier(long id, String code, String name, String shortName, String category,
                    String supplierLevel, String status, String paymentTerms, int version,
                    LocalDateTime updatedAt, boolean canManage, List<String> allowedTransitions) { }
}
