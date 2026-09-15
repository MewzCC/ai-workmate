package com.aiworkmate.agent.tool.port;

import java.time.LocalDateTime;
import java.util.List;

/** Typed boundary for future access-control and policy microservices. */
public interface SecurityGovernanceToolPort {
    AccessOverview accessOverview(ToolActorContext context, AccessQuery query);
    DataScopeOverview dataScopes(ToolActorContext context, DataScopeQuery query);
    AiPolicyOverview aiPolicies(ToolActorContext context, AiPolicyQuery query);

    record AccessQuery(String filterCode) { }
    record DataScopeQuery(String scopeType, Boolean enabled) { }
    record AiPolicyQuery(String toolCode, String filterCode, Boolean effectiveEnabled) { }
    record AccessOverview(List<Role> roles, int userCount, int permissionCount, int routeCount,
                          int departmentCount, int positionCount) { public AccessOverview { roles = List.copyOf(roles); } }
    record Role(String code, String name, String description, boolean builtin, int permissionCount) { }
    record DataScopeOverview(List<DataScopePolicy> policies, int roleBindingCount, int userExceptionCount) { public DataScopeOverview { policies = List.copyOf(policies); } }
    record DataScopePolicy(long id, String name, String description, String scopeType,
                           int departmentCount, boolean enabled, long version, LocalDateTime updatedAt) { }
    record AiPolicyOverview(RuntimeSwitches runtime, TenantSwitches tenant, List<AiTool> tools,
                            List<AiRole> roles) { public AiPolicyOverview { tools = List.copyOf(tools); roles = List.copyOf(roles); } }
    record RuntimeSwitches(boolean agentEnabled, boolean planningEnabled, boolean executionEnabled, boolean writeToolsEnabled) { }
    record TenantSwitches(boolean enabled, boolean writeToolsEnabled) { }
    record AiTool(String code, String name, String riskLevel, String sideEffect,
                  String confirmationPolicy, List<String> pageIds, boolean platformEnabled,
                  boolean tenantEnabled, boolean effectiveEnabled) { public AiTool { pageIds = List.copyOf(pageIds); } }
    record AiRole(String code, String name, boolean builtin, boolean immutable,
                  int eligibleToolCount, int grantedToolCount) { }
}
