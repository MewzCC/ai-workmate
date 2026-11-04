package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.BusinessUserOptionsToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.common.PageResponse;
import com.aiworkmate.dto.BusinessUserOption;
import com.aiworkmate.service.AdminAssetsService;
import com.aiworkmate.service.EmployeeChangeService;
import com.aiworkmate.service.UserAccessService;
import lombok.RequiredArgsConstructor;

@LocalAgentDomainAdapter
@RequiredArgsConstructor
public final class BusinessUserOptionsAgentDomainToolAdapter implements BusinessUserOptionsToolPort {
    private final EmployeeChangeService employeeChanges;
    private final AdminAssetsService adminAssets;
    private final UserAccessService access;

    @Override public Page reviewers(ToolActorContext actor, Query query) {
        requireActor(actor, "employee-change");
        return page(employeeChanges.reviewerOptions(actor.userId(), actor.tenantId(), query.keyword(), query.page(), query.size()));
    }
    @Override public Page visitorHosts(ToolActorContext actor, Query query) {
        requireActor(actor, "visitor-booking");
        return page(adminAssets.visitorHostOptions(actor.userId(), actor.tenantId(), query.keyword(), query.page(), query.size()));
    }
    private void requireActor(ToolActorContext actor, String pageId) {
        var current = access.resolveActiveUser(actor.userId());
        if (current == null || !Long.valueOf(actor.tenantId()).equals(current.tenantId())
                || !current.permissions().contains("route:" + pageId)) throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN);
    }
    private Page page(PageResponse<BusinessUserOption> result) {
        return new Page(result.records().stream().map(item -> new Item(item.id(), item.name())).toList(),
                result.total(), result.page(), result.size());
    }
}
