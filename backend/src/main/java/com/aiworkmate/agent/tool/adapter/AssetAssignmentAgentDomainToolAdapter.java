package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.AssetAssignmentToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.common.PageResponse;
import com.aiworkmate.dto.AssetAssignmentOption;
import com.aiworkmate.service.AdminAssetsService;
import com.aiworkmate.service.UserAccessService;
import lombok.RequiredArgsConstructor;

@LocalAgentDomainAdapter
@RequiredArgsConstructor
public final class AssetAssignmentAgentDomainToolAdapter implements AssetAssignmentToolPort {
    private final AdminAssetsService assets;
    private final UserAccessService access;

    @Override public Page departments(ToolActorContext actor, Query query) {
        requireActor(actor);
        return page(assets.assetDepartmentOptions(actor.userId(), actor.tenantId(), query.keyword(), query.page(), query.size()));
    }
    @Override public Page custodians(ToolActorContext actor, CustodianQuery query) {
        requireActor(actor);
        return page(assets.assetCustodianOptions(actor.userId(), actor.tenantId(), query.departmentId(), query.keyword(), query.page(), query.size()));
    }
    private void requireActor(ToolActorContext actor) {
        var current = access.resolveActiveUser(actor.userId());
        if (current == null || !Long.valueOf(actor.tenantId()).equals(current.tenantId())
                || !current.permissions().contains("route:asset-ledger")) throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN);
    }
    private Page page(PageResponse<AssetAssignmentOption> result) {
        return new Page(result.records().stream().map(item -> new Item(item.id(), item.name(), item.departmentId())).toList(),
                result.total(), result.page(), result.size());
    }
}
