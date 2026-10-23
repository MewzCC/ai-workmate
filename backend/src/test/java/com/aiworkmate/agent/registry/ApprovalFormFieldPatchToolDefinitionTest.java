package com.aiworkmate.agent.registry;

import com.aiworkmate.agent.gateway.ToolSchemaValidator;
import com.aiworkmate.agent.capability.PageCapabilityCatalog;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class ApprovalFormFieldPatchToolDefinitionTest {
    @Test void acceptsOnlyABoundedSingleFieldChangeAndRequiresExplicitNonRetryableWrite() throws Exception {
        var mapper = new ObjectMapper(); var validator = new ToolSchemaValidator(); var definition = definition(mapper);
        assertThat(definition.schemaHash()).isEqualTo("sha256:2d7f9c12e2c7bc7a72f986c077ba8de9a5b81f69d23342f1e8408959f8369744");
        assertThat(definition.sideEffect()).isEqualTo(SideEffect.SINGLE_WRITE);
        assertThat(definition.riskLevel()).isEqualTo(RiskLevel.L1);
        assertThat(definition.confirmationPolicy()).isEqualTo(ConfirmationPolicy.EXPLICIT);
        assertThat(definition.retryPolicy()).isEqualTo(RetryPolicy.NEVER);
        assertThat(definition.ownershipPolicy()).isEqualTo(OwnershipPolicy.FIXED_RESOURCE);
        assertThat(definition.requiredPermissions()).containsExactly("approval:manage");
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree("{\"formId\":11,\"version\":3,\"fieldName\":\"reason\",\"required\":false}"))).isTrue();
        for (String args : List.of("{}", "{\"formId\":11,\"version\":3,\"fieldName\":\"reason\"}",
                "{\"formId\":11,\"version\":3,\"fieldName\":\"reason\",\"required\":null}",
                "{\"formId\":11,\"version\":3,\"fieldName\":\"reason\",\"required\":\"true\"}",
                "{\"formId\":11,\"version\":2147483647,\"fieldName\":\"reason\",\"required\":true}",
                "{\"formId\":11,\"version\":3,\"fieldName\":\"reason\",\"required\":true,\"tenantId\":99}",
                "{\"formId\":11,\"version\":3,\"fieldName\":\"reason\",\"fields\":[]}",
                "{\"formId\":11,\"version\":3,\"fieldName\":\"reason\",\"min\":0}",
                "{\"formId\":11,\"version\":3,\"fieldName\":\"reason\",\"schemaJson\":\"{}\"}")) {
            assertThat(validator.valid(definition.inputSchema(), mapper.readTree(args))).as(args).isFalse();
        }
        var pages = new PageCapabilityCatalog();
        assertThat(pages.allowsTool("form-engine", definition.code(), List.of("route:form-engine"))).isTrue();
        assertThat(pages.allowsTool("ai-workspace", definition.code(), List.of("route:ai-workspace"))).isFalse();
        assertThat(pages.allowsTool("ai-workspace", definition.code(), List.of("route:ai-workspace", "route:form-engine"))).isTrue();
        var output = mapper.readTree("{\"formId\":11,\"formKey\":\"fixture\",\"status\":\"DISABLED\",\"version\":4,\"fieldName\":\"reason\",\"label\":\"Reason\",\"required\":true,\"placeholder\":\"\",\"width\":\"half\"}");
        assertThat(validator.valid(definition.outputSchema(), output)).isTrue();
        ((com.fasterxml.jackson.databind.node.ObjectNode) output).put("schemaJson", "private");
        assertThat(validator.valid(definition.outputSchema(), output)).isFalse();
    }
    private ToolDefinition definition(ObjectMapper mapper) throws Exception {
        return new AgentApprovalFormFieldPatchToolDefinitions().approvalFormFieldPatchToolDefinition(mapper);
    }
}
