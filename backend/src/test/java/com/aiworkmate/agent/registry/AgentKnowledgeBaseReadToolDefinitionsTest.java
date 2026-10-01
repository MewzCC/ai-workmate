package com.aiworkmate.agent.registry;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentKnowledgeBaseReadToolDefinitionsTest {
    @Test
    void exposesOnlyBoundedOwnedKnowledgeBaseMetadata() throws Exception {
        var tool = new AgentKnowledgeBaseReadToolDefinitions()
                .knowledgeBaseQueryToolDefinition(new ObjectMapper());

        assertThat(tool.ownershipPolicy()).isEqualTo(OwnershipPolicy.SELF);
        assertThat(tool.sideEffect()).isEqualTo(SideEffect.NONE);
        assertThat(tool.riskLevel()).isEqualTo(RiskLevel.L0);
        assertThat(tool.retryPolicy()).isEqualTo(RetryPolicy.READ_ONLY_SAFE);
        assertThat(tool.requiredPermissions()).containsExactly("knowledge:search");
        assertThat(tool.inputSchema().toString()).doesNotContain(
                "\"userId\":", "\"tenantId\":", "\"file\":", "\"url\":", "\"path\":");
        assertThat(tool.outputSchema().toString()).doesNotContain(
                "embeddingProvider", "embeddingModel", "rerankModel");
        assertThat(tool.schemaHash()).isEqualTo(
                "sha256:7cc0a0854493e1ff0fffe222391339feb71ac75d84ddfc7467c587f1335acbc5");
    }
}
