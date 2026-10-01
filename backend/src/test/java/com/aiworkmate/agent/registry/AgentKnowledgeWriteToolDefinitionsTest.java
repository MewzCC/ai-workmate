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
    void exposesOnlyBoundedConfirmedOwnedKnowledgeBaseUpdate() throws Exception {
        var tool = new AgentKnowledgeWriteToolDefinitions()
                .knowledgeBaseUpdateToolDefinition(new ObjectMapper());
        assertThat(tool.ownershipPolicy()).isEqualTo(OwnershipPolicy.FIXED_RESOURCE);
        assertThat(tool.sideEffect()).isEqualTo(SideEffect.SINGLE_WRITE);
        assertThat(tool.riskLevel()).isEqualTo(RiskLevel.L1);
        assertThat(tool.confirmationPolicy()).isEqualTo(ConfirmationPolicy.EXPLICIT);
        assertThat(tool.retryPolicy()).isEqualTo(RetryPolicy.NEVER);
        assertThat(tool.requiredPermissions()).containsExactly("knowledge:search");
        assertThat(tool.inputSchema().toString()).doesNotContain(
                "\"provider\"", "\"model\"", "\"file\"", "\"url\"", "\"path\"",
                "\"userId\"", "\"tenantId\"", "\"delete\"");
        assertThat(tool.schemaHash()).isEqualTo(
                "sha256:79f873ae75d3b83b062efa802cfd1aa22708fd41d7fdde6f373ad400457c66f2");
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

    @Test
    void exposesOnlyOneConfirmedOwnedDocumentReindex() throws Exception {
        var tool = new AgentKnowledgeWriteToolDefinitions()
                .knowledgeDocumentReindexToolDefinition(new ObjectMapper());
        assertThat(tool.ownershipPolicy()).isEqualTo(OwnershipPolicy.FIXED_RESOURCE);
        assertThat(tool.sideEffect()).isEqualTo(SideEffect.SINGLE_WRITE);
        assertThat(tool.riskLevel()).isEqualTo(RiskLevel.L1);
        assertThat(tool.confirmationPolicy()).isEqualTo(ConfirmationPolicy.EXPLICIT);
        assertThat(tool.retryPolicy()).isEqualTo(RetryPolicy.NEVER);
        assertThat(tool.requiredPermissions()).containsExactly("knowledge:search");
        assertThat(tool.inputSchema().toString()).doesNotContain(
                "\"model\"", "\"file\"", "\"url\"", "\"path\"", "\"ids\"",
                "\"userId\"", "\"tenantId\"", "\"delete\"");
        assertThat(tool.schemaHash()).isEqualTo(
                "sha256:9b0d45dc6506d6b7e5f342d62f71aba31cc2a7e3d993fb7eab30c38528356205");
    }
}
