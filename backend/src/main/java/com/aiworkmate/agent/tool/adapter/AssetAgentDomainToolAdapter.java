package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.AssetToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.service.AdminAssetsService;
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
}
