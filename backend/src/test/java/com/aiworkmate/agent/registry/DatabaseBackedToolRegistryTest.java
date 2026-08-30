package com.aiworkmate.agent.registry;

import com.aiworkmate.agent.config.AgentRuntimeProperties;
import com.aiworkmate.service.model.ResolvedUserAccess;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DatabaseBackedToolRegistryTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AgentToolMapper toolMapper = mock(AgentToolMapper.class);
    private final AgentTenantPolicyMapper tenantPolicyMapper = mock(AgentTenantPolicyMapper.class);
    private AgentRuntimeProperties properties;
    private ToolDefinition definition;
    private DatabaseBackedToolRegistry registry;

    @BeforeEach
    void setUp() throws Exception {
        properties = new AgentRuntimeProperties();
        properties.setEnabled(true);
        properties.setPlanningEnabled(true);
        JsonNode schema = objectMapper.readTree("""
                {"type":"object","properties":{},"additionalProperties":false}
                """);
        definition = ToolDefinition.create(
                "todo.query", "Todo query", "Query my todos", "Read-only self todos", "1.0.0",
                schema, schema, RiskLevel.L0, Set.of("todo:read"), PermissionMode.ALL,
                OwnershipPolicy.ASSIGNED_TO_SELF, RetryPolicy.READ_ONLY_SAFE, SideEffect.NONE,
                ConfirmationPolicy.NONE, 50, 262144, 15000, "HASHED_ARGS"
        );
        registry = new DatabaseBackedToolRegistry(
                properties, toolMapper, tenantPolicyMapper, new ToolCatalog(List.of(definition)), objectMapper
        );
        AgentTenantPolicy policy = new AgentTenantPolicy();
        policy.setTenantId(1L);
        policy.setEnabled(true);
        when(tenantPolicyMapper.selectById(1L)).thenReturn(policy);
    }

    @Test
    void shouldResolveOnlyCodeAndDatabaseApprovedPageTool() {
        when(toolMapper.selectPlatformTool("todo.query")).thenReturn(row("L0", true));
        ResolvedUserAccess access = new ResolvedUserAccess(
                7L, "employee", 1L, "EMPLOYEE", List.of("EMPLOYEE"),
                List.of("todo:read"), List.of("SELF"), 2L
        );

        assertThat(registry.resolveAllowedTools(access, "todo-list"))
                .extracting(ToolDefinition::code)
                .containsExactly("todo.query");
        assertThat(registry.resolveAllowedTools(access, "knowledge-base")).isEmpty();
    }

    @Test
    void shouldFailClosedWhenDatabaseRiskMetadataIsTampered() {
        when(toolMapper.selectPlatformTool("todo.query")).thenReturn(row("L1", true));

        assertThat(registry.resolveExecutableTool(1L, "todo.query")).isEmpty();
    }

    @Test
    void shouldApplyOnlyRiskPermissionAndLimitNarrowing() {
        AgentTool platform = row("L1", true);
        platform.setConfirmationPolicy("EXPLICIT");
        platform.setRequiredPermissions("[\"todo:read\",\"agent:restricted\"]");
        platform.setMaxResultItems(10);
        platform.setMaxResultBytes(8192);
        platform.setTimeoutMs(5000);
        when(toolMapper.selectPlatformTool("todo.query")).thenReturn(platform);

        ToolDefinition effective = registry.resolveExecutableTool(1L, "todo.query").orElseThrow();

        assertThat(effective.riskLevel()).isEqualTo(RiskLevel.L1);
        assertThat(effective.confirmationPolicy()).isEqualTo(ConfirmationPolicy.EXPLICIT);
        assertThat(effective.requiredPermissions()).containsExactlyInAnyOrder("todo:read", "agent:restricted");
        assertThat(effective.maxResultItems()).isEqualTo(10);
        assertThat(effective.maxResultBytes()).isEqualTo(8192);
        assertThat(effective.timeoutMs()).isEqualTo(5000);
    }

    @Test
    void tenantOverrideMustBelongToTenantAndNeverRelaxPlatformPolicy() {
        AgentTool platform = row("L1", true);
        platform.setConfirmationPolicy("EXPLICIT");
        when(toolMapper.selectPlatformTool("todo.query")).thenReturn(platform);

        AgentTool wrongTenant = row("L1", true);
        wrongTenant.setConfirmationPolicy("EXPLICIT");
        wrongTenant.setTenantId(2L);
        when(toolMapper.selectTenantTool(1L, "todo.query")).thenReturn(wrongTenant);
        assertThat(registry.resolveExecutableTool(1L, "todo.query")).isEmpty();

        AgentTool relaxed = row("L0", true);
        relaxed.setTenantId(1L);
        when(toolMapper.selectTenantTool(1L, "todo.query")).thenReturn(relaxed);
        assertThat(registry.resolveExecutableTool(1L, "todo.query")).isEmpty();
    }

    @Test
    void shouldFailClosedWhenGlobalOrTenantSwitchIsOff() {
        when(toolMapper.selectPlatformTool("todo.query")).thenReturn(row("L0", true));
        properties.setEnabled(false);
        assertThat(registry.resolveExecutableTool(1L, "todo.query")).isEmpty();

        properties.setEnabled(true);
        AgentTenantPolicy policy = new AgentTenantPolicy();
        policy.setTenantId(1L);
        policy.setEnabled(false);
        when(tenantPolicyMapper.selectById(1L)).thenReturn(policy);
        assertThat(registry.resolveExecutableTool(1L, "todo.query")).isEmpty();
    }

    @Test
    void writeToolRequiresGlobalAndTenantWriteSwitches() throws Exception {
        JsonNode schema = objectMapper.readTree(
                "{\"type\":\"object\",\"properties\":{},\"additionalProperties\":false}");
        definition = ToolDefinition.create(
                "leave.createDraft", "Create draft", "Create owned draft", "Create one draft", "1.0.0",
                schema, schema, RiskLevel.L1, Set.of("leave:create"), PermissionMode.ALL,
                OwnershipPolicy.SELF, RetryPolicy.BUSINESS_IDEMPOTENT, SideEffect.SINGLE_WRITE,
                ConfirmationPolicy.EXPLICIT, 1, 16384, 15000, "FULL_WRITE_AUDIT");
        registry = new DatabaseBackedToolRegistry(
                properties, toolMapper, tenantPolicyMapper, new ToolCatalog(List.of(definition)), objectMapper);
        AgentTenantPolicy policy = new AgentTenantPolicy();
        policy.setTenantId(1L);
        policy.setEnabled(true);
        policy.setWriteToolsEnabled(false);
        when(tenantPolicyMapper.selectById(1L)).thenReturn(policy);
        when(toolMapper.selectPlatformTool("leave.createDraft")).thenReturn(writeRow());

        assertThat(registry.resolveExecutableTool(1L, "leave.createDraft")).isEmpty();
        properties.setWriteToolsEnabled(true);
        assertThat(registry.resolveExecutableTool(1L, "leave.createDraft")).isEmpty();
        policy.setWriteToolsEnabled(true);
        assertThat(registry.resolveExecutableTool(1L, "leave.createDraft")).isPresent();
    }

    private AgentTool row(String riskLevel, boolean enabled) {
        AgentTool row = new AgentTool();
        row.setCode(definition.code());
        row.setHandlerVersion(definition.handlerVersion());
        row.setSchemaHash(definition.schemaHash());
        row.setRiskLevel(riskLevel);
        row.setRequiredPermissions("[\"todo:read\"]");
        row.setPermissionMode("ALL");
        row.setDataScopePolicy("ASSIGNED_TO_SELF");
        row.setRetryPolicy("READ_ONLY_SAFE");
        row.setSideEffect("NONE");
        row.setConfirmationPolicy("NONE");
        row.setMaxResultItems(50);
        row.setMaxResultBytes(262144);
        row.setTimeoutMs(15000);
        row.setEnabled(enabled);
        return row;
    }

    private AgentTool writeRow() {
        AgentTool row = new AgentTool();
        row.setCode(definition.code());
        row.setHandlerVersion(definition.handlerVersion());
        row.setSchemaHash(definition.schemaHash());
        row.setRiskLevel("L1");
        row.setRequiredPermissions("[\"leave:create\"]");
        row.setPermissionMode("ALL");
        row.setDataScopePolicy("SELF");
        row.setRetryPolicy("BUSINESS_IDEMPOTENT");
        row.setSideEffect("SINGLE_WRITE");
        row.setConfirmationPolicy("EXPLICIT");
        row.setMaxResultItems(1);
        row.setMaxResultBytes(16384);
        row.setTimeoutMs(15000);
        row.setEnabled(true);
        return row;
    }
}
