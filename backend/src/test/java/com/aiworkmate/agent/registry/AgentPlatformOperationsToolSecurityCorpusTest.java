package com.aiworkmate.agent.registry;

import com.aiworkmate.agent.gateway.ToolSchemaValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentPlatformOperationsToolSecurityCorpusTest {
    @Test
    void rejectsIdentityNetworkSqlNestedAndOverLimitArguments() throws Exception {
        var mapper = new ObjectMapper();
        var validator = new ToolSchemaValidator();
        var d = new AgentPlatformOperationsToolDefinitions();
        List<ToolDefinition> tools = List.of(d.integrationEndpointQueryToolDefinition(mapper),
                d.pageActionQueryToolDefinition(mapper), d.runtimeLogQueryToolDefinition(mapper),
                d.sandboxReplayQueryToolDefinition(mapper));
        for (ToolDefinition tool : tools) for (String json : List.of("{\"userId\":7}", "{\"tenantId\":7}",
                "{\"url\":\"https://evil.invalid\"}", "{\"sql\":\"select 1\"}",
                "{\"filter\":{\"nested\":true}}", "{\"size\":51}"))
            assertThat(validator.valid(tool.inputSchema(), mapper.readTree(json))).as(tool.code() + json).isFalse();
        assertThat(validator.valid(tools.get(2).inputSchema(), mapper.readTree("{\"recordId\":7}"))).isFalse();
        assertThat(validator.valid(tools.get(2).inputSchema(), mapper.readTree("{\"recordId\":7,\"source\":\"AGENT\"}"))).isTrue();
    }
}
