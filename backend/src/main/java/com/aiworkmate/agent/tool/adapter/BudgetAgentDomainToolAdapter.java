package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.BudgetToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.agent.tool.port.ToolPage;
import com.aiworkmate.dto.BudgetPlanRequest;
import com.aiworkmate.dto.BudgetStatusRequest;
import com.aiworkmate.service.BudgetService;
import com.aiworkmate.service.model.BudgetAgentDraftCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class BudgetAgentDomainToolAdapter implements BudgetToolPort {
    private final BudgetService budgetService;

    @Override public ToolPage<Budget> budgets(ToolActorContext context, BudgetQuery query) {
        if (query.budgetId() != null) {
            return new ToolPage<>(List.of(budget(
                    budgetService.detail(context.userId(), query.budgetId()).budget())), 1, 1, 1);
        }
        var result = budgetService.list(context.userId(), query.keyword(), query.status(),
                query.fiscalYear(), query.page(), query.size());
        return new ToolPage<>(result.records().stream().map(this::budget).toList(),
                result.total(), result.page(), result.size());
    }

    @Override public BudgetDraftResult createBudgetDraft(ToolActorContext context, BudgetDraft command) {
        var result = budgetService.create(context.userId(), new BudgetPlanRequest(
                command.code(), command.name(), command.fiscalYear(), command.ownerUserId(),
                command.totalAmount(), command.currency(), command.warningThreshold(), command.summary(), null));
        return result(result);
    }

    @Override public BudgetDraftResult updateBudgetDraft(ToolActorContext context, BudgetDraftUpdate command) {
        var result = budgetService.updateAgentDraft(context.userId(), command.budgetId(), command.version(),
                new BudgetAgentDraftCommand(command.name(), command.fiscalYear(), command.ownerUserId(),
                        command.totalAmount(), command.currency(), command.warningThreshold(), command.summary()));
        return result(result);
    }

    @Override public BudgetDraftResult activateBudgetDraft(
            ToolActorContext context, long budgetId, int version) {
        return result(budgetService.updateStatus(
                context.userId(), budgetId, new BudgetStatusRequest("ACTIVE", null, version)));
    }

    @Override public BudgetDraftResult cancelBudgetDraft(
            ToolActorContext context, long budgetId, int version) {
        return result(budgetService.cancelAgentDraft(context.userId(), budgetId, version));
    }

    private BudgetDraftResult result(com.aiworkmate.dto.BudgetResponse item) {
        return new BudgetDraftResult(item.id(), item.code(), item.status(), item.version(), item.updatedAt());
    }

    private Budget budget(com.aiworkmate.dto.BudgetResponse item) {
        return new Budget(item.id(), item.code(), item.name(), item.fiscalYear(), item.ownerLabel(),
                item.totalAmount(), item.occupiedAmount(), item.spentAmount(), item.availableAmount(),
                item.currency(), item.utilizationPercent(), item.alertLevel(), item.status(), item.summary(),
                item.version(), item.updatedAt(), item.canManage(), List.copyOf(item.allowedTransitions()));
    }
}
