package com.aiworkmate.agent.registry;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class MybatisToolPolicySource implements ToolPolicySource {
    private final AgentToolMapper toolMapper;
    private final AgentTenantPolicyMapper tenantPolicyMapper;

    @Override
    public Optional<TenantPolicy> tenantPolicy(Long tenantId) {
        AgentTenantPolicy row = tenantId == null ? null : tenantPolicyMapper.selectById(tenantId);
        return Optional.ofNullable(row).map(value -> new TenantPolicy(
                value.getTenantId(), Boolean.TRUE.equals(value.getEnabled()),
                Boolean.TRUE.equals(value.getWriteToolsEnabled())));
    }

    @Override
    public Optional<ToolPolicy> platformTool(String toolCode) {
        return Optional.ofNullable(toolMapper.selectPlatformTool(toolCode)).map(this::toPolicy);
    }

    @Override
    public Optional<ToolPolicy> tenantTool(Long tenantId, String toolCode) {
        return Optional.ofNullable(toolMapper.selectTenantTool(tenantId, toolCode)).map(this::toPolicy);
    }

    @Override
    public List<ToolPolicy> platformTools() {
        return toolMapper.selectPlatformTools().stream().map(this::toPolicy).toList();
    }

    @Override
    public List<ToolPolicy> tenantTools(Long tenantId) {
        return toolMapper.selectTenantTools(tenantId).stream().map(this::toPolicy).toList();
    }

    private ToolPolicy toPolicy(AgentTool row) {
        return new ToolPolicy(
                row.getTenantId(), row.getCode(), row.getHandlerVersion(), row.getSchemaHash(),
                row.getRiskLevel(), row.getRequiredPermissions(), row.getPermissionMode(),
                row.getDataScopePolicy(), row.getRetryPolicy(), row.getSideEffect(),
                row.getConfirmationPolicy(), row.getMaxResultItems(), row.getMaxResultBytes(),
                row.getTimeoutMs(), Boolean.TRUE.equals(row.getEnabled()));
    }
}
