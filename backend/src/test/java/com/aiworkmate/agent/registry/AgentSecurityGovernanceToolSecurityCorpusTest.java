package com.aiworkmate.agent.registry;

import com.aiworkmate.agent.gateway.ToolSchemaValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentSecurityGovernanceToolSecurityCorpusTest {
    @Test
    void rejectsIdentityPermissionInjectionAndGenericEscapeArguments() throws Exception {
        var m = new ObjectMapper(); var d = new AgentSecurityGovernanceToolDefinitions(); var validator = new ToolSchemaValidator();
        var tools = List.of(d.accessGovernanceQueryToolDefinition(m), d.dataPermissionQueryToolDefinition(m), d.aiPermissionQueryToolDefinition(m));
        for (var tool : tools) for (String json : List.of("{\"userId\":1}","{\"tenantId\":1}","{\"permissions\":[\"*\"]}","{\"sql\":\"select 1\"}","{\"url\":\"https://evil.invalid\"}","{\"filter\":{}}"))
            assertThat(validator.valid(tool.inputSchema(), m.readTree(json))).as(tool.code()+json).isFalse();
    }
}
