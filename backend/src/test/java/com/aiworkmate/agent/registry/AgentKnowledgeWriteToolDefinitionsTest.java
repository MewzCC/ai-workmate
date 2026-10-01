package com.aiworkmate.agent.registry;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentKnowledgeWriteToolDefinitionsTest {
    @Test
    void exposesOnlyBoundedConfirmedKnowledgeBaseCreation() throws Exception {
        var tool = new AgentKnowledgeWriteToolDefinitions()
                .knowledgeBaseCreateToolDefinition(new ObjectMapper());
        assertThat(tool.ownershipPolicy()).isEqualTo(OwnershipPolicy.SELF);
        assertThat(tool.sideEffect()).isEqualTo(SideEffect.SINGLE_WRITE);
        assertThat(tool.riskLevel()).isEqualTo(RiskLevel.L1);
        assertThat(tool.confirmationPolicy()).isEqualTo(ConfirmationPolicy.EXPLICIT);
        assertThat(tool.retryPolicy()).isEqualTo(RetryPolicy.NEVER);
        assertThat(tool.requiredPermissions()).containsExactly("knowledge:search");
        assertThat(tool.inputSchema().toString()).doesNotContain(
                "\"file\":", "\"filePath\":", "\"url\":", "\"path\":", "\"userId\":", "\"tenantId\":");
        assertThat(tool.schemaHash()).isEqualTo(
                "sha256:e397aeffc2f83042328b36a7308a762abac81935ffd00c2face07d301aeeb8c1");
    }

    @Test
    void exposesOnlyBoundedConfirmedTextCreation() throws Exception {
        var tool = new AgentKnowledgeWriteToolDefinitions()
                .knowledgeDocumentCreateTextToolDefinition(new ObjectMapper());
        assertThat(tool.ownershipPolicy()).isEqualTo(OwnershipPolicy.FIXED_RESOURCE);
        assertThat(tool.sideEffect()).isEqualTo(SideEffect.SINGLE_WRITE);
        assertThat(tool.riskLevel()).isEqualTo(RiskLevel.L1);
        assertThat(tool.confirmationPolicy()).isEqualTo(ConfirmationPolicy.EXPLICIT);
        assertThat(tool.retryPolicy()).isEqualTo(RetryPolicy.NEVER);
        assertThat(tool.requiredPermissions()).containsExactly("knowledge:search");
        assertThat(tool.inputSchema().toString()).doesNotContain(
                "\"file\":", "\"filePath\":", "\"url\":", "\"path\":");
        assertThat(tool.schemaHash()).isEqualTo(
                "sha256:15e630e3f3e2b4555b462814140dbff020de8be9546f2a365d8c3e3e912a4e9a");
    }
}
