package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.SealToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.service.AdminAssetsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public final class SealAgentDomainToolAdapter implements SealToolPort {
    private final AdminAssetsService adminAssetsService;

    @Override
    public Page query(ToolActorContext context, Query query) {
        if (query.usageId() != null) {
            return new Page(List.of(toItem(adminAssetsService.getSealUsage(
                    context.userId(), query.usageId()))), 1, 1, 1);
        }
        var result = query.queue() == Queue.PENDING
                ? adminAssetsService.listPendingSealUsages(context.userId(), query.page(), query.size())
                : adminAssetsService.listMySealUsages(context.userId(), query.status(), query.page(), query.size());
        return new Page(result.records().stream().map(this::toItem).toList(),
                result.total(), result.page(), result.size());
    }

    private Item toItem(com.aiworkmate.dto.SealUsageResponse item) {
        return new Item(item.id(), item.applicantName(), item.approverName(), item.sealType(),
                item.documentTitle(), item.usageReason(), item.copies(), item.status(), item.version(),
                item.taskStatus(), item.submittedAt(), item.completedAt(), item.actualCopies(), item.handlerName(),
                item.usedAt(), item.returnedAt(), item.canWithdraw(), item.canDecide(), item.canRegisterUse(),
                item.canReturn(), item.canArchiveDocument());
    }
}
