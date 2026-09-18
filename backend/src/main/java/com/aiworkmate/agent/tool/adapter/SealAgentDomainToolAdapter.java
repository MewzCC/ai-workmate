package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.SealToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.agent.tool.port.ToolOperationKey;
import com.aiworkmate.agent.tool.port.ToolWriteVerification;
import com.aiworkmate.service.AdminAssetsService;
import com.aiworkmate.service.model.SealAgentApplicationCommand;
import com.aiworkmate.service.model.SealAgentUseCommand;
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

    @Override
    public ApplicationResult apply(
            ToolActorContext context, ApplicationCommand command, ToolOperationKey operationKey) {
        var result = adminAssetsService.submitSealUsageAgent(
                context.userId(), toDomain(command), operationKey.value());
        return new ApplicationResult(
                result.usageId(), result.status(), result.version(), result.submittedAt());
    }

    @Override
    public ToolWriteVerification<ApplicationResult> findApplication(
            ToolActorContext context, ApplicationCommand command, ToolOperationKey operationKey) {
        return adminAssetsService.findAgentSealUsage(
                        context.userId(), toDomain(command), operationKey.value())
                .map(result -> ToolWriteVerification.observed(new ApplicationResult(
                        result.usageId(), result.status(), result.version(), result.submittedAt())))
                .orElseGet(ToolWriteVerification::unobserved);
    }

    private SealAgentApplicationCommand toDomain(ApplicationCommand command) {
        return new SealAgentApplicationCommand(
                command.sealType(), command.documentTitle(), command.usageReason(), command.copies());
    }

    @Override
    public UseResult registerUse(
            ToolActorContext context, UseCommand command, ToolOperationKey operationKey) {
        var result = adminAssetsService.registerSealUseAgent(
                context.userId(), toDomain(command), operationKey.value());
        return new UseResult(result.usageId(), result.status(), result.version(),
                result.actualCopies(), result.usedAt());
    }

    @Override
    public ToolWriteVerification<UseResult> findRegisteredUse(
            ToolActorContext context, UseCommand command, ToolOperationKey operationKey) {
        return adminAssetsService.findAgentRegisteredSealUse(
                        context.userId(), toDomain(command), operationKey.value())
                .map(result -> ToolWriteVerification.observed(new UseResult(
                        result.usageId(), result.status(), result.version(),
                        result.actualCopies(), result.usedAt())))
                .orElseGet(ToolWriteVerification::unobserved);
    }

    private SealAgentUseCommand toDomain(UseCommand command) {
        return new SealAgentUseCommand(
                command.usageId(), command.version(), command.actualCopies(), command.remark());
    }

    private Item toItem(com.aiworkmate.dto.SealUsageResponse item) {
        return new Item(item.id(), item.applicantName(), item.approverName(), item.sealType(),
                item.documentTitle(), item.usageReason(), item.copies(), item.status(), item.version(),
                item.taskStatus(), item.submittedAt(), item.completedAt(), item.actualCopies(), item.handlerName(),
                item.usedAt(), item.returnedAt(), item.canWithdraw(), item.canDecide(), item.canRegisterUse(),
                item.canReturn(), item.canArchiveDocument());
    }
}
