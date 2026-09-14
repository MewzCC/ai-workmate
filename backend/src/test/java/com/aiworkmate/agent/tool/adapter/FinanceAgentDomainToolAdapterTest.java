package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.common.PageResponse;
import com.aiworkmate.dto.ExpenseSummaryResponse;
import com.aiworkmate.dto.SupplierPageResponse;
import com.aiworkmate.dto.SupplierResponse;
import com.aiworkmate.dto.SupplierStatsResponse;
import com.aiworkmate.agent.tool.port.FinanceToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.service.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class FinanceAgentDomainToolAdapterTest {
    private final ExpenseQueryService expenses = mock(ExpenseQueryService.class);
    private final BudgetService budgets = mock(BudgetService.class);
    private final ContractService contracts = mock(ContractService.class);
    private final SupplierService suppliers = mock(SupplierService.class);
    private final FinanceAgentDomainToolAdapter adapter = new FinanceAgentDomainToolAdapter(expenses, budgets, contracts, suppliers);
    private final ToolActorContext actor = new ToolActorContext(1L, 7L, 3L, 4L, 1, "trace");

    @Test
    void mapsTypedExpenseWithoutRawApprovalJson() {
        var now = LocalDateTime.of(2026, 9, 14, 9, 0);
        when(expenses.mine(7L, "PENDING", 1, 20)).thenReturn(PageResponse.of(List.of(
                new ExpenseSummaryResponse(1L, "差旅报销", new BigDecimal("88.50"), "TRAVEL",
                        LocalDate.of(2026, 9, 13), "INV-1", "客户拜访", "PENDING", 2,
                        "审批人", now.plusDays(1), now, false, true, true, false, false)), 1, 1, 20));
        var result = adapter.expenses(actor, new FinanceToolPort.ExpenseQuery(null, "PENDING", 1, 20));
        assertThat(result.items().get(0).amount()).isEqualByComparingTo("88.50");
        assertThat(result.toString()).doesNotContain("dataJson", "taskId", "applicantUserId");
    }

    @Test
    void supplierSummaryDropsContactCreditAddressAndRiskFields() {
        var now = LocalDateTime.of(2026, 9, 14, 10, 0);
        when(suppliers.list(7L, null, null, null, 1, 20)).thenReturn(new SupplierPageResponse(List.of(
                new SupplierResponse(2L, "SUP-1", "供应商", "简称", "secret-credit", "SERVICE", "A", "ACTIVE",
                        "联系人", "13800000000", "secret@example.com", "内部地址", "月结", "高风险", 3,
                        now, now, true, List.of("SUSPENDED"))), 1, 1, 20,
                mock(SupplierStatsResponse.class), true));
        var result = adapter.suppliers(actor, new FinanceToolPort.SupplierQuery(null, null, null, null, 1, 20));
        assertThat(result.items().get(0).name()).isEqualTo("供应商");
        assertThat(result.toString()).doesNotContain("secret-credit", "13800000000", "secret@example.com", "内部地址", "高风险");
    }
}
