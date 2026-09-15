package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.SecurityGovernanceToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.service.AccessGovernanceQueryService;
import com.aiworkmate.service.AiOperationPermissionQueryService;
import com.aiworkmate.service.DataPermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SecurityGovernanceAgentDomainToolAdapter implements SecurityGovernanceToolPort {
    private final AccessGovernanceQueryService accessService;
    private final DataPermissionService dataPermissionService;
    private final AiOperationPermissionQueryService aiPermissionService;

    @Override public AccessOverview accessOverview(ToolActorContext c, AccessQuery q) {
        var x = accessService.overview(c.userId());
        var roles = x.roles().stream().filter(r -> q.filterCode() == null || q.filterCode().equals(r.code()))
                .map(r -> new Role(r.code(), r.name(), r.description(), r.builtin(), r.permissions().size())).toList();
        return new AccessOverview(roles, x.users().size(), x.permissions().size(), x.routes().size(), x.departments().size(), x.positions().size());
    }
    @Override public DataScopeOverview dataScopes(ToolActorContext c, DataScopeQuery q) {
        var x = dataPermissionService.overview(c.userId());
        var policies = x.policies().stream().filter(p -> q.scopeType() == null || q.scopeType().equals(p.scopeType()))
                .filter(p -> q.enabled() == null || q.enabled() == p.enabled())
                .map(p -> new DataScopePolicy(p.id(), p.name(), p.description(), p.scopeType(), p.departmentIds().size(), p.enabled(), p.version(), p.updatedAt())).toList();
        return new DataScopeOverview(policies, x.roleBindings().size(), x.userExceptions().size());
    }
    @Override public AiPolicyOverview aiPolicies(ToolActorContext c, AiPolicyQuery q) {
        var x = aiPermissionService.overview(c.userId());
        var tools = x.tools().stream().filter(t -> q.toolCode() == null || q.toolCode().equals(t.code()))
                .filter(t -> q.effectiveEnabled() == null || q.effectiveEnabled() == t.effectiveEnabled())
                .map(t -> new AiTool(t.code(), t.name(), t.riskLevel(), t.sideEffect(), t.confirmationPolicy(), t.pageIds(), t.platformEnabled(), t.tenantEnabled(), t.effectiveEnabled())).toList();
        var roles = x.roles().stream().filter(r -> q.filterCode() == null || q.filterCode().equals(r.code()))
                .map(r -> new AiRole(r.code(), r.name(), r.builtin(), r.immutable(), r.eligibleToolCodes().size(), r.toolCodes().size())).toList();
        return new AiPolicyOverview(new RuntimeSwitches(x.runtime().agentEnabled(), x.runtime().planningEnabled(), x.runtime().executionEnabled(), x.runtime().writeToolsEnabled()), new TenantSwitches(x.tenantPolicy().enabled(), x.tenantPolicy().writeToolsEnabled()), tools, roles);
    }
}
