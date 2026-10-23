package com.aiworkmate.agent.registry;

import com.aiworkmate.agent.gateway.ToolSchemaValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ApprovalFormDefinitionToolDefinitionTest {
    @Test void acceptsOnlyOneIdentityAndReturnsBoundedNonExecutableFields() throws Exception {
        var mapper = new ObjectMapper(); var validator = new ToolSchemaValidator();
        var definition = new AgentReadToolDefinitions().approvalFormDefinitionQueryToolDefinition(mapper);
        assertThat(definition.schemaHash()).isEqualTo("sha256:b91991992231bd332647b3c0942cba36cce1372e9b5d67692b2090366727e00f");
        assertThat(definition.sideEffect()).isEqualTo(SideEffect.NONE);
        assertThat(definition.riskLevel()).isEqualTo(RiskLevel.L0);
        assertThat(definition.requiredPermissions()).containsExactly("approval:read");
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree("{\"formId\":11}"))).isTrue();
        for (String args : new String[]{"{}", "{\"formId\":0}", "{\"formId\":\"11\"}", "{\"formId\":11,\"tenantId\":99}", "{\"formId\":11,\"url\":\"https://invalid.example\"}"}) {
            assertThat(validator.valid(definition.inputSchema(), mapper.readTree(args))).isFalse();
        }
        var output = mapper.readTree("""
          {"formId":11,"formKey":"fixture","formName":"Fixture","status":"DISABLED","version":3,
           "fields":[{"name":"reason","label":"Reason","type":"text","required":true,"options":[],"width":"full"}]}
          """);
        assertThat(validator.valid(definition.outputSchema(), output)).isTrue();
        ((com.fasterxml.jackson.databind.node.ObjectNode) output).put("schemaJson", "private");
        assertThat(validator.valid(definition.outputSchema(), output)).isFalse();
        assertThat(definition.maxResultItems()).isEqualTo(20);
        assertThat(definition.maxResultBytes()).isEqualTo(65536);
        var pages = new com.aiworkmate.agent.capability.PageCapabilityCatalog();
        assertThat(pages.allowsTool("form-engine", definition.code(), java.util.List.of("route:form-engine"))).isTrue();
        assertThat(pages.allowsTool("ai-workspace", definition.code(), java.util.List.of("route:ai-workspace"))).isFalse();
        assertThat(pages.allowsTool("ai-workspace", definition.code(), java.util.List.of("route:ai-workspace", "route:form-engine"))).isTrue();
    }
}
