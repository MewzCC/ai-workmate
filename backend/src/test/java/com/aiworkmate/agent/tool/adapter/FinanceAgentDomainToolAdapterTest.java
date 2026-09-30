package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.common.PageResponse;
import com.aiworkmate.dto.ExpenseSummaryResponse;
import com.aiworkmate.dto.BudgetResponse;
import com.aiworkmate.dto.ContractResponse;
import com.aiworkmate.dto.SupplierPageResponse;
import com.aiworkmate.dto.SupplierResponse;
import com.aiworkmate.dto.SupplierStatsResponse;
import com.aiworkmate.agent.tool.port.BudgetToolPort;
import com.aiworkmate.agent.tool.port.ContractToolPort;
import com.aiworkmate.agent.tool.port.ExpenseToolPort;
import com.aiworkmate.agent.tool.port.SupplierToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.agent.tool.port.ToolOperationKey;
import com.aiworkmate.agent.tool.port.ToolWriteVerification;
import com.aiworkmate.service.*;
import com.aiworkmate.service.model.ExpenseAgentDraftReceipt;
import com.aiworkmate.service.model.ExpenseAgentLifecycleReceipt;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class FinanceAgentDomainToolAdapterTest {
    private final ExpenseQueryService expenses = mock(ExpenseQueryService.class);
    private final ExpenseApplicationService expenseApplications = mock(ExpenseApplicationService.class);
    private final BudgetService budgets = mock(BudgetService.class);
    private final ContractService contracts = mock(ContractService.class);
    private final SupplierService suppliers = mock(SupplierService.class);
    private final ExpenseAgentDomainToolAdapter expenseAdapter =
            new ExpenseAgentDomainToolAdapter(expenses, expenseApplications);
    private final BudgetAgentDomainToolAdapter budgetAdapter = new BudgetAgentDomainToolAdapter(budgets);
    private final ContractAgentDomainToolAdapter contractAdapter = new ContractAgentDomainToolAdapter(contracts);
    private final SupplierAgentDomainToolAdapter supplierAdapter = new SupplierAgentDomainToolAdapter(suppliers);
    private final ToolActorContext actor = new ToolActorContext(1L, 7L, 3L, 4L, 1, "trace");

    @Test
    void mapsTypedExpenseWithoutRawApprovalJson() {
        var now = LocalDateTime.of(2026, 9, 14, 9, 0);
        when(expenses.mine(7L, "PENDING", 1, 20)).thenReturn(PageResponse.of(List.of(
                new ExpenseSummaryResponse(1L, "差旅报销", new BigDecimal("88.50"), "TRAVEL",
                        LocalDate.of(2026, 9, 13), "INV-1", "客户拜访", "PENDING", 2,
                        "审批人", now.plusDays(1), now, false, true, true, false, false)), 1, 1, 20));
        var result = expenseAdapter.expenses(actor, new ExpenseToolPort.ExpenseQuery(null, "PENDING", 1, 20));
        assertThat(result.items().get(0).amount()).isEqualByComparingTo("88.50");
        assertThat(result.toString()).doesNotContain("dataJson", "taskId", "applicantUserId");
    }

    @Test
    void mapsExpenseDraftThroughTypedServiceBoundary() {
        var command = new ExpenseToolPort.ExpenseDraft(
                new BigDecimal("88.50"), "TRAVEL", LocalDate.of(2026, 9, 17),
                "INV-1", "客户拜访");
        var key = new ToolOperationKey("expense-operation");
        var createdAt = LocalDateTime.of(2026, 9, 18, 12, 0);
        when(expenseApplications.createAgentDraft(eq(7L), any(), eq("expense-operation")))
                .thenReturn(new ExpenseAgentDraftReceipt(
                        51L, "expense-application", "DRAFT", 0, createdAt));
        when(expenseApplications.findAgentDraft(eq(7L), any(), eq("expense-operation")))
                .thenReturn(Optional.of(new ExpenseAgentDraftReceipt(
                        51L, "expense-application", "DRAFT", 0, createdAt)));

        var expected = new ExpenseToolPort.ExpenseDraftResult(
                51L, "expense-application", "DRAFT", 0, createdAt);
        assertThat(expenseAdapter.createExpenseDraft(actor, command, key)).isEqualTo(expected);
        assertThat(expenseAdapter.findExpenseDraft(actor, command, key))
                .isEqualTo(ToolWriteVerification.observed(expected));
        verify(expenseApplications).createAgentDraft(eq(7L), any(), eq("expense-operation"));
    }

    @Test
    void createsBudgetDraftThroughTypedServiceBoundary() {
        var command = new BudgetToolPort.BudgetDraft(
                "RD-2027", "研发预算", 2027, 7L, new BigDecimal("100000.00"),
                "CNY", 80, "年度研发");
        var updatedAt = LocalDateTime.of(2026, 9, 19, 3, 0);
        when(budgets.create(eq(7L), any())).thenReturn(new BudgetResponse(
                81L, "RD-2027", "研发预算", 2027, 7L, "员工",
                new BigDecimal("100000.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("100000.00"), "CNY", 80, 0, "NORMAL",
                "DRAFT", "年度研发", 0, updatedAt, true, List.of("ACTIVE", "CANCELLED")));

        assertThat(budgetAdapter.createBudgetDraft(actor, command)).isEqualTo(
                new BudgetToolPort.BudgetDraftResult(81L, "RD-2027", "DRAFT", 0, updatedAt));
        verify(budgets).create(eq(7L), argThat(request ->
                request.code().equals("RD-2027") && request.version() == null));
    }

    @Test
    void updatesBudgetDraftThroughDedicatedDomainCommand() {
        var command = new BudgetToolPort.BudgetDraftUpdate(
                81L, 0, "研发预算二期", 2027, 7L, new BigDecimal("120000.00"),
                "CNY", 85, "更新范围");
        var updatedAt = LocalDateTime.of(2026, 9, 19, 3, 1);
        when(budgets.updateAgentDraft(eq(7L), eq(81L), eq(0), any())).thenReturn(new BudgetResponse(
                81L, "RD-2027", "研发预算二期", 2027, 7L, "员工",
                new BigDecimal("120000.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("120000.00"), "CNY", 85, 0, "NORMAL",
                "DRAFT", "更新范围", 1, updatedAt, true, List.of("ACTIVE", "CANCELLED")));

        assertThat(budgetAdapter.updateBudgetDraft(actor, command)).isEqualTo(
                new BudgetToolPort.BudgetDraftResult(81L, "RD-2027", "DRAFT", 1, updatedAt));
        verify(budgets).updateAgentDraft(eq(7L), eq(81L), eq(0), argThat(update ->
                update.name().equals("研发预算二期") && update.warningThreshold() == 85));
    }

    @Test
    void activatesBudgetDraftUsingFixedDomainTransition() {
        var updatedAt = LocalDateTime.of(2026, 9, 19, 3, 2);
        when(budgets.updateStatus(eq(7L), eq(81L), any())).thenReturn(new BudgetResponse(
                81L, "RD-2027", "研发预算二期", 2027, 7L, "员工",
                new BigDecimal("120000.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("120000.00"), "CNY", 85, 0, "NORMAL",
                "ACTIVE", "更新范围", 2, updatedAt, true, List.of("CLOSED", "CANCELLED")));

        assertThat(budgetAdapter.activateBudgetDraft(actor, 81L, 1)).isEqualTo(
                new BudgetToolPort.BudgetDraftResult(81L, "RD-2027", "ACTIVE", 2, updatedAt));
        verify(budgets).updateStatus(eq(7L), eq(81L), argThat(request ->
                request.status().equals("ACTIVE") && request.version() == 1 && request.reason() == null));
    }

    @Test
    void cancelsOnlyThroughDedicatedDraftDomainBoundary() {
        var updatedAt = LocalDateTime.of(2026, 9, 19, 3, 3);
        when(budgets.cancelAgentDraft(7L, 82L, 0)).thenReturn(new BudgetResponse(
                82L, "OPS-2027", "运维预算", 2027, 7L, "员工",
                new BigDecimal("80000.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("80000.00"), "CNY", 80, 0, "NORMAL",
                "CANCELLED", null, 1, updatedAt, true, List.of()));

        assertThat(budgetAdapter.cancelBudgetDraft(actor, 82L, 0)).isEqualTo(
                new BudgetToolPort.BudgetDraftResult(82L, "OPS-2027", "CANCELLED", 1, updatedAt));
        verify(budgets).cancelAgentDraft(7L, 82L, 0);
        verify(budgets, never()).updateStatus(anyLong(), anyLong(), any());
    }

    @Test
    void createsContractDraftThroughTypedServiceBoundary() {
        var command = new ContractToolPort.ContractDraft(
                "HT-2027", "年度采购合同", "PURCHASE", "示例公司", null, 7L,
                new BigDecimal("100000.00"), "CNY", LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 1), LocalDate.of(2027, 8, 31), "年度采购");
        var updatedAt = LocalDateTime.of(2026, 9, 19, 4, 0);
        when(contracts.create(eq(7L), any())).thenReturn(new ContractResponse(
                91L, "HT-2027", "年度采购合同", "PURCHASE", "示例公司",
                null, null, 7L, "员工", new BigDecimal("100000.00"), BigDecimal.ZERO,
                "CNY", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1),
                LocalDate.of(2027, 8, 31), "DRAFT", "NOT_STARTED", "NORMAL", 346,
                "年度采购", 0, null, 0, updatedAt, updatedAt, true,
                List.of("ACTIVE", "TERMINATED"), List.of("IN_PROGRESS"), false, false));

        assertThat(contractAdapter.createContractDraft(actor, command)).isEqualTo(
                new ContractToolPort.ContractDraftResult(91L, "HT-2027", "DRAFT", 0, updatedAt));
        verify(contracts).create(eq(7L), argThat(request ->
                request.code().equals("HT-2027") && request.version() == null
                        && request.amount().compareTo(new BigDecimal("100000.00")) == 0));
    }

    @Test
    void updatesContractDraftThroughDedicatedTypedBoundary() {
        var command = new ContractToolPort.ContractDraftUpdate(
                81L, 0, "更新合同", "SERVICE", "示例公司", null, 7L,
                new BigDecimal("1200.00"), "CNY", null, LocalDate.of(2026, 9, 1),
                LocalDate.of(2027, 8, 31), "更新摘要");
        var updatedAt = LocalDateTime.of(2026, 9, 19, 6, 0);
        when(contracts.updateAgentDraft(eq(7L), eq(81L), eq(0), any())).thenReturn(new ContractResponse(
                81L, "HT-001", "更新合同", "SERVICE", "示例公司", null, null, 7L, "员工",
                new BigDecimal("1200.00"), BigDecimal.ZERO, "CNY", null,
                LocalDate.of(2026, 9, 1), LocalDate.of(2027, 8, 31), "DRAFT", "NOT_STARTED",
                "NORMAL", 346, "更新摘要", 0, null, 1, updatedAt, updatedAt, true,
                List.of("ACTIVE", "TERMINATED"), List.of("IN_PROGRESS"), false, false));
        assertThat(contractAdapter.updateContractDraft(actor, command)).isEqualTo(
                new ContractToolPort.ContractDraftResult(81L, "HT-001", "DRAFT", 1, updatedAt));
        verify(contracts).updateAgentDraft(eq(7L), eq(81L), eq(0), argThat(update ->
                update.name().equals("更新合同") && update.amount().compareTo(new BigDecimal("1200.00")) == 0));
    }

    @Test
    void submitsExpenseThroughDedicatedTypedBoundary() {
        when(expenseApplications.submitAgentDraft(7L, 51L, 0))
                .thenReturn(new ExpenseAgentLifecycleReceipt(
                        51L, "expense-application", "PENDING", 1));

        assertThat(expenseAdapter.submitExpenseDraft(actor, 51L, 0))
                .isEqualTo(new ExpenseToolPort.ExpenseLifecycleResult(
                        51L, "expense-application", "PENDING", 1));
        verify(expenseApplications).submitAgentDraft(7L, 51L, 0);
    }

    @Test
    void updatesExpenseDraftThroughDedicatedTypedBoundary() {
        var patch = new ExpenseToolPort.ExpenseDraft(
                new BigDecimal("99.50"), null, null, null, "调整差旅金额");
        when(expenseApplications.updateAgentDraft(eq(7L), eq(51L), eq(0), any()))
                .thenReturn(new ExpenseAgentLifecycleReceipt(
                        51L, "expense-application", "DRAFT", 1));

        assertThat(expenseAdapter.updateExpenseDraft(actor, 51L, 0, patch))
                .isEqualTo(new ExpenseToolPort.ExpenseLifecycleResult(
                        51L, "expense-application", "DRAFT", 1));
        verify(expenseApplications).updateAgentDraft(eq(7L), eq(51L), eq(0), any());
    }

    @Test
    void withdrawsExpenseThroughDedicatedTypedBoundary() {
        when(expenseApplications.withdrawAgentApplication(7L, 51L, 1))
                .thenReturn(new ExpenseAgentLifecycleReceipt(
                        51L, "expense-application", "WITHDRAWN", 2));

        assertThat(expenseAdapter.withdrawExpense(actor, 51L, 1))
                .isEqualTo(new ExpenseToolPort.ExpenseLifecycleResult(
                        51L, "expense-application", "WITHDRAWN", 2));
        verify(expenseApplications).withdrawAgentApplication(7L, 51L, 1);
    }

    @Test
    void reopensExpenseThroughDedicatedTypedBoundary() {
        when(expenseApplications.reopenAgentApplication(7L, 51L, 2))
                .thenReturn(new ExpenseAgentLifecycleReceipt(
                        51L, "expense-application", "DRAFT", 3));

        assertThat(expenseAdapter.reopenExpense(actor, 51L, 2))
                .isEqualTo(new ExpenseToolPort.ExpenseLifecycleResult(
                        51L, "expense-application", "DRAFT", 3));
        verify(expenseApplications).reopenAgentApplication(7L, 51L, 2);
    }

    @Test
    void supplierSummaryDropsContactCreditAddressAndRiskFields() {
        var now = LocalDateTime.of(2026, 9, 14, 10, 0);
        when(suppliers.list(7L, null, null, null, 1, 20)).thenReturn(new SupplierPageResponse(List.of(
                new SupplierResponse(2L, "SUP-1", "供应商", "简称", "secret-credit", "SERVICE", "A", "ACTIVE",
                        "联系人", "13800000000", "secret@example.com", "内部地址", "月结", "高风险", 3,
                        now, now, true, List.of("SUSPENDED"))), 1, 1, 20,
                mock(SupplierStatsResponse.class), true));
        var result = supplierAdapter.suppliers(actor, new SupplierToolPort.SupplierQuery(null, null, null, null, 1, 20));
        assertThat(result.items().get(0).name()).isEqualTo("供应商");
        assertThat(result.toString()).doesNotContain("secret-credit", "13800000000", "secret@example.com", "内部地址", "高风险");
    }

    @Test
    void createsSupplierDraftWithoutSensitiveFields() {
        var command = new SupplierToolPort.SupplierDraft(
                "SUP-2027", "示例供应商", "示例", "SERVICE", "STANDARD", "月结30天");
        var updatedAt = LocalDateTime.of(2026, 9, 19, 5, 0);
        when(suppliers.create(eq(7L), any())).thenReturn(new SupplierResponse(
                92L, "SUP-2027", "示例供应商", "示例", null, "SERVICE", "STANDARD", "DRAFT",
                null, null, null, null, "月结30天", null, 0, updatedAt, updatedAt, true, List.of()));

        assertThat(supplierAdapter.createSupplierDraft(actor, command)).isEqualTo(
                new SupplierToolPort.SupplierDraftResult(92L, "SUP-2027", "DRAFT", 0, updatedAt));
        verify(suppliers).create(eq(7L), argThat(request -> request.version() == null
                && request.unifiedSocialCreditCode() == null && request.contactName() == null
                && request.contactPhone() == null && request.contactEmail() == null
                && request.address() == null && request.riskNote() == null));
    }

    @Test
    void updatesSupplierDraftThroughDedicatedTypedBoundary() {
        var command = new SupplierToolPort.SupplierDraftUpdate(
                92L, 0, "更新供应商", "更新简称", "SERVICE", "PREFERRED", "月结45天");
        var updatedAt = LocalDateTime.of(2026, 9, 19, 7, 0);
        when(suppliers.updateAgentDraft(eq(7L), eq(92L), eq(0), any())).thenReturn(new SupplierResponse(
                92L, "SUP-2027", "更新供应商", "更新简称", null, "SERVICE", "PREFERRED", "DRAFT",
                null, null, null, null, "月结45天", null, 1, updatedAt, updatedAt, true, List.of()));
        assertThat(supplierAdapter.updateSupplierDraft(actor, command)).isEqualTo(
                new SupplierToolPort.SupplierDraftResult(92L, "SUP-2027", "DRAFT", 1, updatedAt));
        verify(suppliers).updateAgentDraft(eq(7L), eq(92L), eq(0), argThat(update ->
                update.name().equals("更新供应商") && update.supplierLevel().equals("PREFERRED")));
    }

    @Test
    void updatesSupplierStatusThroughExistingAuditedStateMachine() {
        var command = new SupplierToolPort.SupplierStatusUpdate(
                92L, 1, "BLACKLISTED", "合规风险");
        var updatedAt = LocalDateTime.of(2026, 9, 30, 19, 55);
        when(suppliers.updateStatus(eq(7L), eq(92L), any())).thenReturn(new SupplierResponse(
                92L, "SUP-2027", "示例供应商", "示例", null, "SERVICE", "STANDARD", "BLACKLISTED",
                null, null, null, null, "月结30天", "合规风险", 2,
                updatedAt.minusDays(1), updatedAt, true, List.of("SUSPENDED")));

        assertThat(supplierAdapter.updateSupplierStatus(actor, command)).isEqualTo(
                new SupplierToolPort.SupplierDraftResult(
                        92L, "SUP-2027", "BLACKLISTED", 2, updatedAt));
        verify(suppliers).updateStatus(eq(7L), eq(92L), argThat(request ->
                request.status().equals("BLACKLISTED") && request.version() == 1
                        && request.reason().equals("合规风险")));
    }
}
