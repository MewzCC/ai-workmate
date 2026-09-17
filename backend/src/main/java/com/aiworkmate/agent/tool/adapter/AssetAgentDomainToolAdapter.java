package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.AssetToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.agent.tool.port.ToolOperationKey;
import com.aiworkmate.agent.tool.port.ToolWriteVerification;
import com.aiworkmate.service.AdminAssetsService;
import com.aiworkmate.service.model.AssetAgentClaimCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public final class AssetAgentDomainToolAdapter implements AssetToolPort {
    private final AdminAssetsService adminAssetsService;

    @Override
    public Page query(ToolActorContext context, Query query) {
        var result = adminAssetsService.listAssets(
                context.userId(), query.keyword(), query.category(), query.status(), query.page(), query.size());
        return new Page(result.records().stream().map(item -> new Item(
                item.id(), item.assetCode(), item.name(), item.category(), item.specification(), item.status(),
                item.departmentName(), item.ownerName(), item.purchaseDate(), item.originalValue(), item.remark(),
                item.version(), item.canEdit(), item.canDelete(), item.createdAt(), item.updatedAt())).toList(),
                result.total(), result.page(), result.size());
    }

    @Override
    public ClaimResult claim(
            ToolActorContext context, ClaimCommand command, ToolOperationKey operationKey) {
        var result = adminAssetsService.claimAssetAgent(context.userId(), toDomain(command), operationKey.value());
        return new ClaimResult(result.assetId(), result.status(), result.version());
    }

    @Override
    public ToolWriteVerification<ClaimResult> findClaim(
            ToolActorContext context, ClaimCommand command, ToolOperationKey operationKey) {
        return adminAssetsService.findAgentAssetClaim(context.userId(), toDomain(command), operationKey.value())
                .map(result -> ToolWriteVerification.observed(
                        new ClaimResult(result.assetId(), result.status(), result.version())))
                .orElseGet(ToolWriteVerification::unobserved);
    }

    private AssetAgentClaimCommand toDomain(ClaimCommand command) {
        return new AssetAgentClaimCommand(
                command.assetId(), command.employeeId(), command.version(), command.reason());
    }
}
