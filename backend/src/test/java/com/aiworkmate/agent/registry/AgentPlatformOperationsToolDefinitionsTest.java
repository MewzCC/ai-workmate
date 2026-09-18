package com.aiworkmate.agent.registry;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentPlatformOperationsToolDefinitionsTest {
    @Test
    void definesFourBoundedReadOnlyToolsWithoutPayloadContent() throws Exception {
        var definitions = new AgentPlatformOperationsToolDefinitions();
        var mapper = new ObjectMapper();
        List<ToolDefinition> tools = List.of(
                definitions.integrationEndpointQueryToolDefinition(mapper),
                definitions.pageActionQueryToolDefinition(mapper),
                definitions.runtimeLogQueryToolDefinition(mapper),
                definitions.sandboxReplayQueryToolDefinition(mapper));
        assertThat(tools).allSatisfy(tool -> {
            assertThat(tool.riskLevel()).isEqualTo(RiskLevel.L0);
            assertThat(tool.sideEffect()).isEqualTo(SideEffect.NONE);
            assertThat(tool.maxResultItems()).isEqualTo(50);
            assertThat(tool.inputSchema().path("additionalProperties").asBoolean()).isFalse();
            assertThat(tool.outputSchema().toString()).doesNotContain("requestTemplate", "responsePreview", "requestFingerprint", "traceId");
        });
        assertThat(tools).extracting(ToolDefinition::schemaHash).containsExactly(
                "sha256:77b6949a8aca42cdd8c7776e51e64e2fa4bf52c64bb4d950e5d61e2c19eadd22",
                "sha256:7f6c91f43b7a3416e8824b064421ec1be233c0a952fed1b068dc255c8e9cdd96",
                "sha256:d79d49ccc330312907ff310bb96264640e938dbedd5df7c5054fe93357cadcac",
                "sha256:733b65aba2e3dc720f2d17d6af7be1ea434b93fc7f219c9a551fd4f08e551978");
    }
}
